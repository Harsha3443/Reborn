package com.harsha.reborn.core.engine

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RebornPermissionType {
    ACCESSIBILITY,
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
}

data class PermissionItemStatus(
    val type: RebornPermissionType,
    val title: String,
    val whyNeeded: String,
    val isGranted: Boolean,
    val isCritical: Boolean = true,
)

data class PermissionHealthSnapshot(
    val items: List<PermissionItemStatus>,
    val grantedCount: Int,
    val totalCount: Int,
    val allGranted: Boolean,
    val criticalMissingCount: Int,
)

/**
 * Evaluates live status of every permission required by Reborn and builds safe
 * Intent actions for one-tap repair across Android 8–15+.
 */
@Singleton
class PermissionHealthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _healthSnapshot = MutableStateFlow(evaluate())
    val healthSnapshot: StateFlow<PermissionHealthSnapshot> = _healthSnapshot.asStateFlow()

    fun refresh(): PermissionHealthSnapshot {
        val latest = evaluate()
        _healthSnapshot.value = latest
        return latest
    }

    fun evaluate(): PermissionHealthSnapshot {
        val items = listOf(
            PermissionItemStatus(
                type = RebornPermissionType.ACCESSIBILITY,
                title = "Accessibility Service",
                whyNeeded = "Detects when a distracting app opens so Reborn can pause it immediately. Runs locally with zero network access.",
                isGranted = isAccessibilityEnabled(),
                isCritical = true,
            ),
            PermissionItemStatus(
                type = RebornPermissionType.USAGE_ACCESS,
                title = "Usage Access",
                whyNeeded = "Tracks daily screen time, per-app limits, and launch counts accurately on-device.",
                isGranted = isUsageAccessGranted(),
                isCritical = true,
            ),
            PermissionItemStatus(
                type = RebornPermissionType.OVERLAY,
                title = "Display Over Other Apps",
                whyNeeded = "Shows the calm pause screen and block shield on top of blocked apps.",
                isGranted = isOverlayGranted(),
                isCritical = true,
            ),
            PermissionItemStatus(
                type = RebornPermissionType.NOTIFICATIONS,
                title = "Notifications",
                whyNeeded = "Keeps the protection guard active in the foreground and alerts you if the service stops.",
                isGranted = areNotificationsGranted(),
                isCritical = false,
            ),
            PermissionItemStatus(
                type = RebornPermissionType.BATTERY_OPTIMIZATION,
                title = "Unrestricted Battery",
                whyNeeded = "Stops Android and OEM battery savers from silently killing Reborn in the background.",
                isGranted = isIgnoringBatteryOptimizations(),
                isCritical = false,
            ),
        )

        val granted = items.count { it.isGranted }
        val criticalMissing = items.count { it.isCritical && !it.isGranted }
        return PermissionHealthSnapshot(
            items = items,
            grantedCount = granted,
            totalCount = items.size,
            allGranted = granted == items.size,
            criticalMissingCount = criticalMissing,
        )
    }

    fun isAccessibilityEnabled(): Boolean {
        if (RebornAccessibilityService.isConnected.value) return true

        val expectedComponent = ComponentName(context, RebornAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        val foundInSecureSetting = enabledServices
            .split(':')
            .any { entry ->
                ComponentName.unflattenFromString(entry) == expectedComponent
            }
        if (foundInSecureSetting) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                val si = serviceInfo.resolveInfo?.serviceInfo
                si != null && si.packageName == context.packageName &&
                    si.name == RebornAccessibilityService::class.java.name
            }
    }

    @Suppress("DEPRECATION")
    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(context)

    fun areNotificationsGranted(): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the appropriate Android Settings screen for the given permission,
     * falling back gracefully to App Details if an OEM hides a specific settings page.
     */
    @SuppressLint("BatteryLife")
    fun openPermissionSettings(type: RebornPermissionType) {
        val primaryIntent = when (type) {
            RebornPermissionType.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            RebornPermissionType.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }

            RebornPermissionType.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null),
                )

            RebornPermissionType.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }

            RebornPermissionType.BATTERY_OPTIMIZATION ->
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", context.packageName, null),
                )
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launchedPrimary = runCatching {
            if (primaryIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(primaryIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launchedPrimary) {
            // Usage Access with package URI fails on some OEMs; retry without data URI first.
            if (type == RebornPermissionType.USAGE_ACCESS) {
                val genericUsage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(genericUsage) }.isSuccess) return
            }
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}
