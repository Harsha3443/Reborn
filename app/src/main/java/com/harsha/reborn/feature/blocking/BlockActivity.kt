package com.harsha.reborn.feature.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.harsha.reborn.ui.components.LiquidGlassBackdrop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.harsha.reborn.core.data.local.SettingsRepository
import com.harsha.reborn.core.data.local.UserPreferences
import com.harsha.reborn.core.data.model.BlockReason
import com.harsha.reborn.core.data.repository.FrictionRepository
import com.harsha.reborn.core.data.repository.GrantPassResult
import com.harsha.reborn.core.engine.RulesCacheManager
import com.harsha.reborn.feature.friction.BreathingPauseShield
import com.harsha.reborn.ui.theme.RebornTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen shield shown over a blocked app.
 * Guides a configurable 5-30s breathing pause, supports Intent-Based Unlock
 * (with automatic re-block when the 5/10/15m pass expires), and always provides
 * an immediate "Go Home" escape to the launcher.
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var frictionRepository: FrictionRepository
    @Inject lateinit var rulesCacheManager: RulesCacheManager

    private var packageNameState by mutableStateOf("")
    private var appLabelState by mutableStateOf("App")
    private var blockTitleState by mutableStateOf("Paused by Reborn")
    private var blockDetailState by mutableStateOf("This app is currently blocked by an active rule.")
    private var blockReasonState by mutableStateOf("")
    private var countdownNonce by mutableIntStateOf(0)
    private var remainingSecondsState by mutableIntStateOf(10)
    private var unlockErrorState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)

        setContent {
            val prefs by settingsRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences(),
            )
            val todayLogs by frictionRepository.observeUnlockLogsToday().collectAsStateWithLifecycle(
                initialValue = emptyList(),
            )
            val scope = rememberCoroutineScope()
            val totalPauseSec = prefs.frictionSettings.pauseSeconds

            LaunchedEffect(countdownNonce, totalPauseSec) {
                remainingSecondsState = totalPauseSec
                while (remainingSecondsState > 0) {
                    delay(1_000L)
                    remainingSecondsState -= 1
                }
            }

            RebornTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColorEnabled,
            ) {
                BackHandler {
                    navigateToHomeLauncher()
                }
                LiquidGlassBackdrop(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    key(countdownNonce) {
                        val strictConfig = prefs.strictModeConfig
                        val isStrictSystemGuard = blockReasonState == BlockReason.STRICT_SYSTEM_GUARD.name
                        val isStrictActive = strictConfig.isEffectivelyLocked(System.currentTimeMillis())
                        val unlockDisabledReason = when {
                            isStrictSystemGuard -> "Strict Mode protects this system screen until your lock timer ends."
                            isStrictActive && strictConfig.disallowUnlockPassesWhileActive ->
                                "Intent unlock passes are locked while Strict Mode is active."
                            else -> null
                        }

                        val isPartialAppBlock = blockReasonState == BlockReason.IN_APP_FEED.name ||
                            blockReasonState == BlockReason.BROWSER_KEYWORD.name

                        BreathingPauseShield(
                            appLabel = appLabelState,
                            ruleTitle = blockTitleState,
                            ruleDetail = blockDetailState,
                            totalPauseSeconds = totalPauseSec,
                            remainingPauseSeconds = remainingSecondsState,
                            passesUsedToday = todayLogs.size,
                            dailyPassCap = prefs.frictionSettings.dailyUnlockPassCap,
                            errorMessage = unlockErrorState,
                            unlockDisabledReason = unlockDisabledReason,
                            onGoHome = ::navigateToHomeLauncher,
                            onReturnToSafeApp = if (isPartialAppBlock) {
                                { finish() }
                            } else {
                                null
                            },
                            onOpenReborn = {
                                startActivity(
                                    Intent(this@BlockActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                )
                                finish()
                            },
                            onRequestUnlock = { reason, durationMin ->
                                scope.launch {
                                    when (
                                        val result = frictionRepository.grantUnlockPass(
                                            packageName = packageNameState,
                                            appLabel = appLabelState,
                                            reason = reason,
                                            durationMinutes = durationMin,
                                        )
                                    ) {
                                        is GrantPassResult.Granted -> {
                                            unlockErrorState = null
                                            rulesCacheManager.registerActivePassImmediately(result.pass)
                                            finish()
                                        }
                                        GrantPassResult.DailyCapReached -> {
                                            unlockErrorState = "Daily unlock cap reached."
                                        }
                                        GrantPassResult.InvalidReason -> {
                                            unlockErrorState = "Please enter at least 4 characters explaining why."
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent?) {
        packageNameState = intent?.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        appLabelState = intent?.getStringExtra(EXTRA_APP_LABEL)?.takeIf { it.isNotBlank() } ?: "App"
        blockTitleState = intent?.getStringExtra(EXTRA_BLOCK_TITLE)?.takeIf { it.isNotBlank() } ?: "Paused by Reborn"
        blockDetailState = intent?.getStringExtra(EXTRA_BLOCK_DETAIL)?.takeIf { it.isNotBlank() }
            ?: "This app is currently blocked by an active rule."
        blockReasonState = intent?.getStringExtra(EXTRA_BLOCK_REASON).orEmpty()
        unlockErrorState = null
        countdownNonce += 1
    }

    private fun navigateToHomeLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_BLOCK_TITLE = "extra_block_title"
        const val EXTRA_BLOCK_DETAIL = "extra_block_detail"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }
}
