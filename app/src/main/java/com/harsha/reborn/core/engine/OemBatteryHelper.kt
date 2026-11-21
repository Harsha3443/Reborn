package com.harsha.reborn.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class OemBatteryGuidance(
    val manufacturerDisplayName: String,
    val isAggressiveOem: Boolean,
    val steps: List<String>,
    val deepLinkIntents: List<Intent>,
)

/**
 * Detects aggressive OEM background process killers (Xiaomi, Samsung, Oppo, Vivo, OnePlus, Huawei)
 * and provides safe deep-link intents + clear manual fallback steps.
 */
object OemBatteryHelper {

    fun getGuidance(context: Context): OemBatteryGuidance {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val lower = manufacturer.lowercase()
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return when {
            lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "Xiaomi / MIUI / HyperOS",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Enable Autostart for Reborn in Security → Permissions → Autostart.",
                        "Open App Info → Battery saver → set to 'No restrictions'.",
                        "Lock Reborn in Recents so MIUI doesn't swipe-kill the service.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        componentIntent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            lower.contains("samsung") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "Samsung One UI",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Open App Info → Battery → select 'Unrestricted'.",
                        "In Device Care → Battery → Background usage limits, add Reborn to 'Never auto sleeping apps'.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            lower.contains("oppo") || lower.contains("realme") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "Oppo / Realme ColorOS",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Open App Info → Battery usage → enable 'Allow background activity' and 'Allow auto-launch'.",
                        "Lock Reborn in the Recent Apps switcher.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            lower.contains("vivo") || lower.contains("iqoo") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "Vivo / iQOO FuntouchOS",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Open App Info → Battery → select 'High background power consumption'.",
                        "In iManager → App Manager → Autostart, enable Reborn.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            lower.contains("oneplus") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "OnePlus OxygenOS",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Open App Info → Battery usage → enable 'Allow background activity' and 'Allow auto-launch'.",
                        "Disable Sleep Standby Optimization if blocking stops overnight.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            lower.contains("huawei") || lower.contains("honor") -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = "Huawei / Honor EMUI",
                    isAggressiveOem = true,
                    steps = listOf(
                        "Open Settings → Battery → App launch → disable 'Manage automatically' for Reborn.",
                        "Enable Auto-launch, Secondary launch, and Run in background.",
                    ),
                    deepLinkIntents = listOf(
                        componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        appDetailsIntent,
                    ),
                )
            }

            else -> {
                OemBatteryGuidance(
                    manufacturerDisplayName = manufacturer.ifEmpty { "Android Device" },
                    isAggressiveOem = false,
                    steps = listOf(
                        "Set Battery usage for Reborn to 'Unrestricted' so Android does not pause the guard.",
                    ),
                    deepLinkIntents = listOf(appDetailsIntent),
                )
            }
        }
    }

    /**
     * Tries each OEM-specific intent in order and falls back safely to App Info
     * so missing/renamed ROM activities never crash.
     */
    fun openBestBatterySettings(context: Context): Boolean {
        val guidance = getGuidance(context)
        val pm = context.packageManager
        for (intent in guidance.deepLinkIntents) {
            val resolved = runCatching { pm.resolveActivity(intent, 0) != null }.getOrDefault(false)
            if (resolved) {
                val started = runCatching { context.startActivity(intent) }.isSuccess
                if (started) return true
            }
        }
        return false
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().apply {
            component = ComponentName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
