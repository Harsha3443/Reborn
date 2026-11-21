package com.harsha.reborn.core.engine

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional Device Admin receiver hindering accidental or impulsive uninstalls.
 * Requests zero wipe/lock policies.
 */
class RebornDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Reborn Strict Mode safeguard: disabling Device Admin allows uninstalling the app before your lock finishes."
    }
}

@Singleton
class RebornDeviceAdminManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val componentName: ComponentName
        get() = ComponentName(context, RebornDeviceAdminReceiver::class.java)

    private val devicePolicyManager: DevicePolicyManager?
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isAdminActive(): Boolean =
        runCatching { devicePolicyManager?.isAdminActive(componentName) == true }.getOrDefault(false)

    fun createActivateAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Optional safeguard that prevents Reborn from being uninstalled easily while Strict Mode is active.",
            )
        }

    fun deactivateAdmin(): Boolean =
        runCatching {
            val dpm = devicePolicyManager ?: return@runCatching false
            if (dpm.isAdminActive(componentName)) {
                dpm.removeActiveAdmin(componentName)
            }
            true
        }.getOrDefault(false)
}
