package com.harsha.reborn.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts RebornGuardService and schedules the WorkManager watchdog after device reboot
 * or app update whenever blocking rules are active.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(appContext, EngineEntryPoint::class.java)
                val snapshot = entryPoint.rulesRepository().rulesSnapshotFlow.first()
                entryPoint.rulesCacheManager().ensureStarted()
                entryPoint.rulesCacheManager().syncGuardServiceAndWatchdog(snapshot)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
