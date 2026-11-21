package com.harsha.reborn.core.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.harsha.reborn.core.data.model.BlockingRulesSnapshot
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Persistent foreground service keeping Reborn's blocking engine alive across OEMs.
 */
class RebornGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rulesObserverJob: Job? = null

    private val entryPoint: EngineEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, EngineEntryPoint::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cacheManager = entryPoint.rulesCacheManager()
        cacheManager.ensureStarted()

        val initialNotification = buildNotification(buildStatusSummary(cacheManager.rulesSnapshot.value))
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)

        if (rulesObserverJob == null) {
            rulesObserverJob = serviceScope.launch {
                cacheManager.rulesSnapshot.collect { snapshot ->
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification(buildStatusSummary(snapshot)))
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildStatusSummary(snapshot: BlockingRulesSnapshot): String {
        val now = System.currentTimeMillis()
        val parts = mutableListOf<String>()
        val quick = snapshot.quickBlock
        if (quick != null && quick.isActive && now < quick.endTimeEpochMs) {
            parts.add("Quick Block (${quick.packageNames.size} apps)")
        }
        val activeSchedules = snapshot.schedules.count { it.isEnabled && it.packageNames.isNotEmpty() }
        if (activeSchedules > 0) {
            parts.add("$activeSchedules schedule${if (activeSchedules == 1) "" else "s"}")
        }
        val activeLimits = snapshot.dailyTimeLimits.count { it.isEnabled } + snapshot.launchLimits.count { it.isEnabled }
        if (activeLimits > 0) {
            parts.add("$activeLimits limit${if (activeLimits == 1) "" else "s"}")
        }
        if (snapshot.allowlist.isEnabled) {
            parts.add("Allowlist Mode")
        }
        return if (parts.isEmpty()) {
            "Pause. Choose. Begin again."
        } else {
            "Active: ${parts.joinToString(" • ")}"
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phoenix_monochrome)
            .setContentTitle("Reborn Guard Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reborn Protection Guard",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps Reborn's offline app blocker running reliably."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "reborn_guard_channel"
        const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
