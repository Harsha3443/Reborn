package com.harsha.reborn

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.AppCategory
import com.harsha.reborn.core.data.model.BackupRestoreResult
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.ThemeMode
import com.harsha.reborn.core.data.repository.BackupRepository
import com.harsha.reborn.core.data.repository.BlockingRulesRepository
import com.harsha.reborn.core.data.repository.SelectedAppsRepository
import com.harsha.reborn.core.engine.QuickBlockToggleController
import com.harsha.reborn.core.engine.QuickBlockToggleOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PolishPhase7Test {

    private lateinit var context: Context
    private lateinit var db: RebornDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var rulesRepository: BlockingRulesRepository
    private lateinit var selectedAppsRepository: SelectedAppsRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var toggleController: QuickBlockToggleController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RebornDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = SettingsRepository(context)
        rulesRepository = BlockingRulesRepository(
            dao = db.blockingRulesDao(),
            settingsRepository = settingsRepository,
        )
        selectedAppsRepository = SelectedAppsRepository(db.selectedAppDao())
        backupRepository = BackupRepository(
            database = db,
            blockingRulesDao = db.blockingRulesDao(),
            selectedAppDao = db.selectedAppDao(),
            blockingRulesRepository = rulesRepository,
            settingsRepository = settingsRepository,
        )
        toggleController = QuickBlockToggleController(
            context = context,
            blockingRulesRepository = rulesRepository,
            selectedAppDao = db.selectedAppDao(),
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `json backup round-trips AMOLED theme, rules, apps, and blocks restore during Strict Mode`() = runBlocking {
        settingsRepository.setThemeMode(ThemeMode.AMOLED)
        settingsRepository.setDynamicColorEnabled(true)
        settingsRepository.setPauseSeconds(15)
        settingsRepository.setDailyUnlockPassCap(2)
        settingsRepository.recordMindfulDay("2026-11-20", currentStreak = 5)

        selectedAppsRepository.selectApps(
            listOf(
                InstalledAppInfo("com.instagram.android", "Instagram", AppCategory.SOCIAL),
                InstalledAppInfo("com.google.android.youtube", "YouTube", AppCategory.VIDEO_AUDIO),
            )
        )
        rulesRepository.saveSchedule(
            ScheduleProfile(
                name = "Deep Focus",
                daysOfWeek = setOf(1, 2, 3),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 12 * 60,
                packageNames = setOf("com.instagram.android"),
                isEnabled = true,
            )
        )
        rulesRepository.saveDailyTimeLimit(
            DailyTimeLimitRule(
                name = "YouTube Dim",
                packageNames = setOf("com.google.android.youtube"),
                dailyLimitMinutes = 25,
                afterLimitAction = DailyLimitAfterAction.GRAYSCALE_DIM,
                isEnabled = true,
            )
        )
        rulesRepository.saveLaunchLimit(
            LaunchLimitRule(
                packageName = "com.instagram.android",
                appLabel = "Instagram",
                maxLaunchesPerDay = 4,
                isEnabled = true,
            )
        )
        rulesRepository.saveConditionalRule(
            ConditionalRule(
                name = "Night Charger",
                packageNames = setOf("com.instagram.android"),
                chargerCondition = ChargerCondition.CHARGING,
                isEnabled = true,
            )
        )
        rulesRepository.addBrowserKeywordRule("reddit.com", isDomain = true)

        val exportedJson = backupRepository.exportBackupJson()
        assertTrue(exportedJson.contains("AMOLED"))
        assertTrue(exportedJson.contains("Deep Focus"))
        assertTrue(exportedJson.contains("reddit.com"))

        // Wipe local state before restoring
        settingsRepository.setThemeMode(ThemeMode.LIGHT)
        settingsRepository.setDynamicColorEnabled(false)
        selectedAppsRepository.clearSelection()
        db.blockingRulesDao().clearSchedules()
        db.blockingRulesDao().clearDailyTimeLimits()
        db.blockingRulesDao().clearLaunchLimits()
        db.blockingRulesDao().clearConditionalRules()
        db.blockingRulesDao().clearBrowserKeywordRules()

        val result = backupRepository.importBackupJson(exportedJson)
        assertTrue(result is BackupRestoreResult.Success)
        val success = result as BackupRestoreResult.Success
        assertEquals(2, success.restoredAppsCount)
        assertEquals(5, success.restoredRulesCount)

        val restoredPrefs = settingsRepository.preferencesFlow.first()
        assertEquals(ThemeMode.AMOLED, restoredPrefs.themeMode)
        assertTrue(restoredPrefs.dynamicColorEnabled)
        assertEquals(15, restoredPrefs.frictionSettings.pauseSeconds)
        assertEquals(5, restoredPrefs.bestStreakDays)

        val restoredRules = rulesRepository.rulesSnapshotFlow.first()
        assertEquals("Deep Focus", restoredRules.schedules.first().name)
        assertEquals(DailyLimitAfterAction.GRAYSCALE_DIM, restoredRules.dailyTimeLimits.first().afterLimitAction)
        assertEquals(4, restoredRules.launchLimits.first().maxLaunchesPerDay)
        assertEquals("Night Charger", restoredRules.conditionalRules.first().name)
        assertEquals("reddit.com", restoredRules.browserKeywordRules.first().pattern)

        // Broken JSON returns InvalidBackup
        assertTrue(backupRepository.importBackupJson("{broken") is BackupRestoreResult.InvalidBackup)

        // Active Strict Mode lock blocks overwriting rules via restore
        val now = System.currentTimeMillis()
        settingsRepository.saveStrictModeConfig(
            StrictModeConfig(
                isActive = true,
                startedAtEpochMs = now,
                endsAtEpochMs = now + 600_000L,
            )
        )
        assertEquals(BackupRestoreResult.BlockedByStrictMode, backupRepository.importBackupJson(exportedJson))
    }

    @Test
    fun `quick block toggle controller starts, stops, handles empty apps, and respects strict mode`() = runBlocking {
        // No apps selected yet
        assertEquals(QuickBlockToggleOutcome.NoAppsSelected, toggleController.toggleQuickBlock())

        // Select an app and toggle ON
        selectedAppsRepository.selectApps(
            listOf(InstalledAppInfo("com.instagram.android", "Instagram", AppCategory.SOCIAL))
        )
        val startOutcome = toggleController.toggleQuickBlock(defaultDurationMinutes = 30)
        assertTrue(startOutcome is QuickBlockToggleOutcome.Started)
        assertEquals(1, (startOutcome as QuickBlockToggleOutcome.Started).appCount)

        val activeState = toggleController.readCurrentState()
        assertTrue(activeState.isActive)
        assertTrue(activeState.remainingMinutes in 29..30)

        // Strict lock prevents stopping via widget/tile
        val now = System.currentTimeMillis()
        settingsRepository.saveStrictModeConfig(
            StrictModeConfig(isActive = true, startedAtEpochMs = now, endsAtEpochMs = now + 600_000L)
        )
        assertEquals(QuickBlockToggleOutcome.StrictLocked, toggleController.toggleQuickBlock())
        assertTrue(toggleController.readCurrentState().isActive)

        // Release Strict Mode -> toggle OFF succeeds
        settingsRepository.saveStrictModeConfig(StrictModeConfig(isActive = false))
        assertEquals(QuickBlockToggleOutcome.Stopped, toggleController.toggleQuickBlock())
        assertFalse(toggleController.readCurrentState().isActive)
    }

    @Test
    fun `hindi localization strings resolve for values-hi locale`() {
        val hiConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi"))
        }
        val hiContext = context.createConfigurationContext(hiConfig)
        val hiTagline = hiContext.getString(R.string.app_tagline)
        val hiThemeAmoled = hiContext.getString(R.string.theme_amoled)
        val hiBackupBtn = hiContext.getString(R.string.backup_export_button)

        assertTrue(hiTagline.contains("रुकें"))
        assertTrue(hiThemeAmoled.contains("AMOLED"))
        assertTrue(hiBackupBtn.contains("JSON"))
    }
}
