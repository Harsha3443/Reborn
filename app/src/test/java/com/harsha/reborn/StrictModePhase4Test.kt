package com.harsha.reborn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harsha.reborn.core.data.local.RebornDatabase
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.model.BlockDecision
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.model.StrictEventType
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.core.data.repository.StrictUnlockResult
import com.harsha.reborn.core.engine.StrictModeCrypto
import com.harsha.reborn.core.engine.StrictSystemScreenDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StrictModePhase4Test {

    private lateinit var context: Context
    private lateinit var db: RebornDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var strictModeRepository: StrictModeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RebornDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsRepository = SettingsRepository(context)
        strictModeRepository = StrictModeRepository(
            settingsRepository = settingsRepository,
            strictModeDao = db.strictModeDao(),
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            strictModeRepository.executeEmergencyEscape(StrictModeRepository.EMERGENCY_ESCAPE_PHRASE)
        }
        db.close()
    }

    @Test
    fun `salted crypto hashes verify valid PIN and reject tampering`() {
        val salt = StrictModeCrypto.generateSaltHex()
        val hash = StrictModeCrypto.hashSecret("2580", salt)

        assertNotEquals("2580", hash)
        assertTrue(StrictModeCrypto.verifySecret("2580", salt, hash))
        assertFalse(StrictModeCrypto.verifySecret("0000", salt, hash))

        val friendCode = StrictModeCrypto.generateTrustedContactCode()
        assertTrue(friendCode.contains("-"))
        assertEquals(8, StrictModeCrypto.normalizeTrustedCode(friendCode).length)
    }

    @Test
    fun `system screen detector blocks Reborn tamper screens and allows normal settings`() {
        val uninstall = StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            visibleTexts = listOf("Do you want to uninstall Reborn?", "Cancel", "OK"),
        )
        assertTrue(uninstall is BlockDecision.Block && uninstall.reason == BlockReason.STRICT_SYSTEM_GUARD)

        val accessibility = StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment",
            visibleTexts = listOf("Reborn App Guard", "Use Reborn App Guard", "Accessibility"),
        )
        assertTrue(accessibility is BlockDecision.Block && accessibility.reason == BlockReason.STRICT_SYSTEM_GUARD)

        val appInfo = StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetailsTop",
            visibleTexts = listOf("App info", "Reborn", "Force stop", "Uninstall"),
        )
        assertTrue(appInfo is BlockDecision.Block && appInfo.reason == BlockReason.STRICT_SYSTEM_GUARD)

        val deviceAdmin = StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd",
            visibleTexts = listOf("Reborn Uninstall Guard", "Deactivate this admin app"),
        )
        assertTrue(deviceAdmin is BlockDecision.Block && deviceAdmin.reason == BlockReason.STRICT_SYSTEM_GUARD)

        val wifiSettings = StrictSystemScreenDetector.evaluateSystemWindow(
            packageName = "com.android.settings",
            className = "com.android.settings.Settings\$WifiSettingsActivity",
            visibleTexts = listOf("Wi-Fi", "Use Wi-Fi", "Saved networks"),
        )
        assertEquals(BlockDecision.Allow, wifiSettings)
    }

    @Test
    fun `duration-only strict lock refuses early unlock and auto-expires cleanly`() = runBlocking {
        strictModeRepository.clearLogs()
        val now = System.currentTimeMillis()
        val config = strictModeRepository.activateStrictMode(
            durationMinutes = 60,
            requirePin = false,
            rawPin = "",
            requireChargerConnected = false,
            requireTrustedContactCode = false,
            rawTrustedContactCode = "",
            blockAppSettingsAndSystemGuards = true,
            disallowUnlockPassesWhileActive = true,
            emergencyWaitMinutes = 5,
            nowEpochMs = now,
        )

        assertTrue(config.isEffectivelyLocked(now + 10_000L))
        assertEquals(
            StrictUnlockResult.NoEarlyUnlockEnabled,
            strictModeRepository.attemptEarlyUnlock("", "", isChargerConnected = true, nowEpochMs = now + 10_000L),
        )
        assertFalse(strictModeRepository.expireIfDue(now + 60_000L))
        assertTrue(strictModeRepository.expireIfDue(config.endsAtEpochMs))
        assertFalse(strictModeRepository.isEffectivelyLocked(config.endsAtEpochMs))

        val logs = strictModeRepository.strictLogsFlow.first()
        assertEquals(StrictEventType.TIMER_ENDED, logs.first().eventType)
    }

    @Test
    fun `multi-safeguard early unlock enforces charger, PIN, and trusted code`() = runBlocking {
        strictModeRepository.clearLogs()
        val now = System.currentTimeMillis()
        val friendCode = "AB3K-9Q2M"

        strictModeRepository.activateStrictMode(
            durationMinutes = 120,
            requirePin = true,
            rawPin = "4321",
            requireChargerConnected = true,
            requireTrustedContactCode = true,
            rawTrustedContactCode = friendCode,
            blockAppSettingsAndSystemGuards = true,
            disallowUnlockPassesWhileActive = true,
            emergencyWaitMinutes = 5,
            nowEpochMs = now,
        )

        assertEquals(
            StrictUnlockResult.ChargerNotConnected,
            strictModeRepository.attemptEarlyUnlock("4321", friendCode, isChargerConnected = false, nowEpochMs = now + 5_000L),
        )
        assertEquals(
            StrictUnlockResult.InvalidPin,
            strictModeRepository.attemptEarlyUnlock("9999", friendCode, isChargerConnected = true, nowEpochMs = now + 5_000L),
        )
        assertEquals(
            StrictUnlockResult.InvalidTrustedCode,
            strictModeRepository.attemptEarlyUnlock("4321", "WRONG", isChargerConnected = true, nowEpochMs = now + 5_000L),
        )
        assertEquals(
            StrictUnlockResult.Unlocked,
            strictModeRepository.attemptEarlyUnlock("4321", "ab3k 9q2m", isChargerConnected = true, nowEpochMs = now + 5_000L),
        )

        assertFalse(strictModeRepository.isEffectivelyLocked(now + 10_000L))
        val logs = strictModeRepository.strictLogsFlow.first()
        assertEquals(StrictEventType.UNLOCKED_EARLY, logs.first().eventType)
    }

    @Test
    fun `emergency escape requires exact phrase and writes audit log`() = runBlocking {
        strictModeRepository.clearLogs()
        val now = System.currentTimeMillis()
        strictModeRepository.activateStrictMode(
            durationMinutes = 90,
            requirePin = false,
            rawPin = "",
            requireChargerConnected = false,
            requireTrustedContactCode = false,
            rawTrustedContactCode = "",
            blockAppSettingsAndSystemGuards = true,
            disallowUnlockPassesWhileActive = true,
            emergencyWaitMinutes = 5,
            nowEpochMs = now,
        )

        assertEquals(
            StrictUnlockResult.InvalidEscapePhrase,
            strictModeRepository.executeEmergencyEscape("let me out", nowEpochMs = now + 10_000L),
        )
        assertTrue(strictModeRepository.isEffectivelyLocked(now + 10_000L))

        assertEquals(
            StrictUnlockResult.Unlocked,
            strictModeRepository.executeEmergencyEscape(
                StrictModeRepository.EMERGENCY_ESCAPE_PHRASE,
                nowEpochMs = now + 10_000L,
            ),
        )
        assertFalse(strictModeRepository.isEffectivelyLocked(now + 10_000L))
        val logs = strictModeRepository.strictLogsFlow.first()
        assertEquals(StrictEventType.EMERGENCY_ESCAPE, logs.first().eventType)
    }
}
