package com.harsha.reborn.core.engine

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.harsha.reborn.core.data.model.AppUsageSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class WeeklyUsageBreakdown(
    val dailyTotalMinutesByDate: Map<LocalDate, Int> = emptyMap(),
    val todayMinutesByPackage: Map<String, Int> = emptyMap(),
    val capturedAtEpochMs: Long = 0L,
)

/**
 * Computes today's per-app foreground minutes and launch counts using UsageStatsManager.queryEvents
 * anchored to local midnight, combined with live foreground entry counts from AccessibilityService
 * so launch limits enforce immediately on the Nth open.
 */
@Singleton
class UsageStatsTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionHealthManager: PermissionHealthManager,
) {
    private val _usageSnapshot = MutableStateFlow(AppUsageSnapshot())
    val usageSnapshot: StateFlow<AppUsageSnapshot> = _usageSnapshot.asStateFlow()

    @Volatile
    private var trackedDay: LocalDate = LocalDate.now()
    private val liveLaunchesToday = ConcurrentHashMap<String, Int>()

    @Volatile
    private var lastForegroundPackage: String? = null

    fun recordForegroundEntry(packageName: String, protectedPackages: Set<String>) {
        val today = LocalDate.now()
        if (today != trackedDay) {
            liveLaunchesToday.clear()
            trackedDay = today
            lastForegroundPackage = null
        }

        // Ignore transient SystemUI notification shade or Reborn itself so overlays don't falsely re-trigger counts,
        // but DO reset lastForegroundPackage when the user returns to the Home launcher or another app.
        if (packageName == "com.android.systemui" || packageName == context.packageName) return

        if (packageName in protectedPackages) {
            lastForegroundPackage = null
            return
        }

        if (packageName != lastForegroundPackage) {
            lastForegroundPackage = packageName
            val baseline = _usageSnapshot.value.dailyLaunchesByPackage[packageName] ?: 0
            val currentLive = liveLaunchesToday[packageName] ?: 0
            liveLaunchesToday[packageName] = maxOf(baseline, currentLive) + 1
            mergeLiveCountsIntoSnapshot()
        }
    }

    suspend fun refreshTodayUsage(): AppUsageSnapshot = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        if (today != trackedDay) {
            liveLaunchesToday.clear()
            trackedDay = today
            lastForegroundPackage = null
        }

        val nowMs = System.currentTimeMillis()
        val midnightMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val minutesMap = mutableMapOf<String, Int>()
        val eventsLaunchesMap = mutableMapOf<String, Int>()

        if (permissionHealthManager.isUsageAccessGranted()) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val activeStartByPkg = mutableMapOf<String, Long>()
                val totalForegroundMs = mutableMapOf<String, Long>()

                val events = runCatching { usm.queryEvents(midnightMs, nowMs) }.getOrNull()
                val event = UsageEvents.Event()
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val type = event.eventType

                    val isResume = type == UsageEvents.Event.ACTIVITY_RESUMED ||
                        @Suppress("DEPRECATION")
                        (type == UsageEvents.Event.MOVE_TO_FOREGROUND)
                    val isPause = type == UsageEvents.Event.ACTIVITY_PAUSED ||
                        @Suppress("DEPRECATION")
                        (type == UsageEvents.Event.MOVE_TO_BACKGROUND) ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_STOPPED)

                    if (isResume) {
                        if (!activeStartByPkg.containsKey(pkg)) {
                            activeStartByPkg[pkg] = event.timeStamp
                            eventsLaunchesMap[pkg] = (eventsLaunchesMap[pkg] ?: 0) + 1
                        }
                    } else if (isPause) {
                        val startedAt = activeStartByPkg.remove(pkg)
                        if (startedAt != null && event.timeStamp >= startedAt) {
                            totalForegroundMs[pkg] = (totalForegroundMs[pkg] ?: 0L) + (event.timeStamp - startedAt)
                        }
                    }
                }

                // Close any currently open session up to nowMs.
                activeStartByPkg.forEach { (pkg, startedAt) ->
                    if (nowMs > startedAt) {
                        totalForegroundMs[pkg] = (totalForegroundMs[pkg] ?: 0L) + (nowMs - startedAt)
                    }
                }

                totalForegroundMs.forEach { (pkg, ms) ->
                    minutesMap[pkg] = (ms / 60_000L).toInt()
                }
            }
        }

        val combinedLaunches = mutableMapOf<String, Int>()
        (eventsLaunchesMap.keys + liveLaunchesToday.keys).forEach { pkg ->
            val fromEvents = eventsLaunchesMap[pkg] ?: 0
            val fromLive = liveLaunchesToday[pkg] ?: 0
            combinedLaunches[pkg] = maxOf(fromEvents, fromLive)
        }

        val snapshot = AppUsageSnapshot(
            dailyMinutesByPackage = minutesMap,
            dailyLaunchesByPackage = combinedLaunches,
            capturedAtEpochMs = nowMs,
        )
        _usageSnapshot.value = snapshot
        snapshot
    }

    private fun mergeLiveCountsIntoSnapshot() {
        val current = _usageSnapshot.value
        val updatedLaunches = current.dailyLaunchesByPackage.toMutableMap()
        liveLaunchesToday.forEach { (pkg, count) ->
            updatedLaunches[pkg] = maxOf(updatedLaunches[pkg] ?: 0, count)
        }
        _usageSnapshot.value = current.copy(dailyLaunchesByPackage = updatedLaunches)
    }

    suspend fun queryWeeklyUsage(
        days: Int = 7,
        excludedPackages: Set<String> = emptySet(),
    ): WeeklyUsageBreakdown = withContext(Dispatchers.IO) {
        val safeDays = days.coerceIn(1, 14)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMs = System.currentTimeMillis()
        val startDate = today.minusDays((safeDays - 1).toLong())
        val startEpochMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()

        if (!permissionHealthManager.isUsageAccessGranted()) {
            return@withContext WeeklyUsageBreakdown(capturedAtEpochMs = nowMs)
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext WeeklyUsageBreakdown(capturedAtEpochMs = nowMs)

        val activeStartByPkg = mutableMapOf<String, Long>()
        val dailyMsByDate = mutableMapOf<LocalDate, Long>()
        val todayMsByPkg = mutableMapOf<String, Long>()
        val todayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()

        fun accumulateInterval(pkg: String, startMs: Long, endMs: Long) {
            if (pkg in excludedPackages || pkg == context.packageName || pkg == "com.android.systemui") return
            if (endMs <= startMs) return
            var cursor = startMs
            while (cursor < endMs) {
                val cursorDate = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val nextMidnightMs = cursorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(endMs, nextMidnightMs)
                val delta = (sliceEnd - cursor).coerceAtLeast(0L)
                if (!cursorDate.isBefore(startDate) && !cursorDate.isAfter(today)) {
                    dailyMsByDate[cursorDate] = (dailyMsByDate[cursorDate] ?: 0L) + delta
                }
                if (sliceEnd > todayStartMs && cursorDate == today) {
                    todayMsByPkg[pkg] = (todayMsByPkg[pkg] ?: 0L) + delta
                }
                cursor = sliceEnd
            }
        }

        val events = runCatching { usm.queryEvents(startEpochMs, nowMs) }.getOrNull()
        val event = UsageEvents.Event()
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val type = event.eventType

            val isResume = type == UsageEvents.Event.ACTIVITY_RESUMED ||
                @Suppress("DEPRECATION")
                (type == UsageEvents.Event.MOVE_TO_FOREGROUND)
            val isPause = type == UsageEvents.Event.ACTIVITY_PAUSED ||
                @Suppress("DEPRECATION")
                (type == UsageEvents.Event.MOVE_TO_BACKGROUND) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_STOPPED)

            if (isResume) {
                if (!activeStartByPkg.containsKey(pkg)) {
                    activeStartByPkg[pkg] = event.timeStamp
                }
            } else if (isPause) {
                val startedAt = activeStartByPkg.remove(pkg)
                if (startedAt != null && event.timeStamp >= startedAt) {
                    accumulateInterval(pkg, startedAt, event.timeStamp)
                }
            }
        }

        // Close still-open foreground sessions up to nowMs (clamped to avoid multi-day stuck sessions > 12h)
        activeStartByPkg.forEach { (pkg, startedAt) ->
            if (nowMs > startedAt && (nowMs - startedAt) <= 12L * 3_600_000L) {
                accumulateInterval(pkg, startedAt, nowMs)
            }
        }

        val dailyMinutes = dailyMsByDate.mapValues { (_, ms) -> (ms / 60_000L).toInt() }
        val todayMinutes = todayMsByPkg.mapValues { (_, ms) -> (ms / 60_000L).toInt() }

        WeeklyUsageBreakdown(
            dailyTotalMinutesByDate = dailyMinutes,
            todayMinutesByPackage = todayMinutes,
            capturedAtEpochMs = nowMs,
        )
    }
}
