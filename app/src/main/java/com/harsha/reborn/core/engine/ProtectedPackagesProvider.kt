package com.harsha.reborn.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves packages that must never be blocked in non-strict mode:
 * Reborn itself, Phone/Dialer, Emergency, Home Launchers, SystemUI, Settings,
 * Permission/Package installers, and active Input Methods.
 */
@Singleton
class ProtectedPackagesProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cachedProtected: Set<String> = emptySet()

    @Volatile
    private var cachedLaunchable: Set<String> = emptySet()

    @Volatile
    private var lastRefreshEpochMs: Long = 0L

    fun getProtectedPackages(forceRefresh: Boolean = false): Set<String> {
        refreshIfStale(forceRefresh)
        return cachedProtected
    }

    fun getLaunchablePackages(forceRefresh: Boolean = false): Set<String> {
        refreshIfStale(forceRefresh)
        return cachedLaunchable
    }

    private fun refreshIfStale(forceRefresh: Boolean) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedProtected.isNotEmpty() && now - lastRefreshEpochMs < 60_000L) {
            return
        }

        val pm = context.packageManager
        val protected = mutableSetOf(
            context.packageName,
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        // Home launchers
        queryPackages(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .forEach { protected.add(it) }

        // Dialers
        queryPackages(pm, Intent(Intent.ACTION_DIAL))
            .forEach { protected.add(it) }

        // Default input method (keyboard)
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
            ?.let { protected.add(it) }

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList?.forEach { protected.add(it.packageName) }

        val launchables = queryPackages(
            pm,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        ).toSet()

        cachedProtected = protected
        cachedLaunchable = launchables
        lastRefreshEpochMs = now
    }

    @Suppress("DEPRECATION")
    private fun queryPackages(pm: PackageManager, intent: Intent): List<String> {
        return runCatching {
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
            resolved.mapNotNull { it.activityInfo?.packageName }
        }.getOrElse { emptyList() }
    }
}
