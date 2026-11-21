package com.harsha.reborn.core.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/**
 * Self-healing WorkManager watchdog checking every 15 minutes that Reborn's foreground service
 * and AccessibilityService remain alive when blocking rules are active.
 */
class GuardWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, EngineEntryPoint::class.java)
        val snapshot = entryPoint.rulesRepository().rulesSnapshotFlow.first()
        val hasActive = snapshot.hasAnyPotentiallyActiveRule(System.currentTimeMillis())

        if (!hasActive) {
            return Result.success()
        }

        if (!RebornGuardService.isRunning.value) {
            runCatching {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, RebornGuardService::class.java),
                )
            }
        }

        val accessibilityActive = entryPoint.permissionHealthManager().isAccessibilityEnabled()
        if (!accessibilityActive) {
            postAccessibilityStoppedAlert()
        }

        return Result.success()
    }

    private fun postAccessibilityStoppedAlert() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Protection Health Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts if Android pauses Reborn's Accessibility service."
            }
            manager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            101,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phoenix_monochrome)
            .setContentTitle("Reborn protection paused")
            .setContentText("Accessibility service was stopped by the system. Tap to restore.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching { manager.notify(ALERT_NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val ALERT_CHANNEL_ID = "reborn_health_alerts"
        private const val ALERT_NOTIFICATION_ID = 1002
    }
}
