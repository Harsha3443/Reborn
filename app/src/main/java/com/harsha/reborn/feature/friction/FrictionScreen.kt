package com.harsha.reborn.feature.friction

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassSliderColors
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrictionScreen(
    onBack: () -> Unit,
    viewModel: FrictionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Mindful Friction") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("friction_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state.isStrictSettingsLocked) {
                    item {
                        GlassCard(
                            cornerRadius = 22.dp,
                            tintColor = tokens.accentTintFill,
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("friction_strict_locked_banner"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Text(
                                    text = "Strict Mode is locked — Friction settings cannot be changed until the timer ends.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Glass Surface #1: Friction Controls
                item {
                    val cap = state.settings.dailyUnlockPassCap.coerceAtLeast(1)
                    val passProgress = (state.passesUsedToday.toFloat() / cap.toFloat()).coerceIn(0f, 1f)
                    GlassCard(
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 1. Breathing Pause Countdown
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Air,
                                            contentDescription = null,
                                            tint = tokens.accentText,
                                        )
                                        Text(
                                            text = "Breathing Pause",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Text(
                                        text = "${state.settings.pauseSeconds}s",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.accentText,
                                    )
                                }
                                Text(
                                    text = "Guided breath before unlock options appear on a blocked app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = state.settings.pauseSeconds.toFloat(),
                                    onValueChange = { viewModel.setPauseSeconds(it.roundToInt()) },
                                    enabled = !state.isStrictSettingsLocked,
                                    valueRange = 5f..30f,
                                    steps = 24,
                                    colors = glassSliderColors(),
                                    modifier = Modifier.testTag("friction_pause_slider"),
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                            // 2. Daily Intent Unlock Cap
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PhoenixProgressRing(
                                        progress = passProgress,
                                        size = 44.dp,
                                        strokeWidth = 4.dp,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Key,
                                            contentDescription = null,
                                            tint = tokens.accentText,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Daily Unlock Cap",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${state.passesUsedToday} / ${state.settings.dailyUnlockPassCap} passes used today",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (state.settings.dailyUnlockPassCap == 0) {
                                            "No unlock passes allowed"
                                        } else {
                                            "Max ${state.settings.dailyUnlockPassCap} passes / day"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                viewModel.setDailyUnlockPassCap(state.settings.dailyUnlockPassCap - 1)
                                            },
                                            enabled = !state.isStrictSettingsLocked && state.settings.dailyUnlockPassCap > 0,
                                            colors = glassTextButtonColors(),
                                            modifier = Modifier.testTag("cap_minus_button"),
                                        ) { Text("-1") }
                                        TextButton(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                viewModel.setDailyUnlockPassCap(state.settings.dailyUnlockPassCap + 1)
                                            },
                                            enabled = !state.isStrictSettingsLocked && state.settings.dailyUnlockPassCap < 10,
                                            colors = glassTextButtonColors(),
                                            modifier = Modifier.testTag("cap_plus_button"),
                                        ) { Text("+1") }
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                            // 3. Delayed Rule Disable
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.HourglassTop,
                                            contentDescription = null,
                                            tint = tokens.accentText,
                                        )
                                        Column {
                                            Text(
                                                text = "Delayed Rule Disable",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "Wait ${state.settings.delayedDisableMinutes}m before turning off an active block.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = state.settings.delayedDisableEnabled,
                                        colors = glassSwitchColors(),
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            viewModel.setDelayedDisableEnabled(it)
                                        },
                                        enabled = !state.isStrictSettingsLocked,
                                        modifier = Modifier.testTag("delayed_disable_switch"),
                                    )
                                }

                                if (state.settings.delayedDisableEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Wait time: ${state.settings.delayedDisableMinutes} min",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    viewModel.setDelayedDisableMinutes(state.settings.delayedDisableMinutes - 1)
                                                },
                                                enabled = !state.isStrictSettingsLocked && state.settings.delayedDisableMinutes > 1,
                                                colors = glassTextButtonColors(),
                                            ) { Text("-1m") }
                                            TextButton(
                                                onClick = {
                                                    viewModel.setDelayedDisableMinutes(state.settings.delayedDisableMinutes + 1)
                                                },
                                                enabled = !state.isStrictSettingsLocked && state.settings.delayedDisableMinutes < 30,
                                                colors = glassTextButtonColors(),
                                            ) { Text("+1m") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.pendingDisables.isNotEmpty()) {
                    item {
                        GlassCard(
                            cornerRadius = 26.dp,
                            tintColor = tokens.accentTintFill,
                            contentPadding = PaddingValues(18.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Pending Rule Disables",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                state.pendingDisables.forEachIndexed { idx, pending ->
                                    if (idx > 0) {
                                        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                                    }
                                    val remSec = ((pending.executeAfterEpochMs - state.nowEpochMs) / 1000L).coerceAtLeast(0L)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pending.targetLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "Disables in %02d:%02d",
                                                    remSec / 60L,
                                                    remSec % 60L,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        GlassButton(
                                            onClick = { viewModel.cancelPendingDisable(pending.id) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("cancel_pending_disable_${pending.id}"),
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Glass Surface #2: Intent Unlock Journal
                item {
                    GlassCard(
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = tokens.accentText,
                                    )
                                    Text(
                                        text = "Intent Unlock Journal",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (state.unlockLogs.isNotEmpty() && !state.isStrictSettingsLocked) {
                                    TextButton(
                                        onClick = viewModel::clearUnlockLogs,
                                        colors = glassTextButtonColors(),
                                        modifier = Modifier.testTag("clear_unlock_journal_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear")
                                    }
                                }
                            }

                            if (state.unlockLogs.isEmpty()) {
                                Text(
                                    text = "When you use an Intent Unlock pass, your written reason and duration are logged locally here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                state.unlockLogs.forEachIndexed { idx, log ->
                                    if (idx > 0) {
                                        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                                    }
                                    val dateText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                        .format(Date(log.grantedAtEpochMs))
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = log.appLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${log.durationMinutes}m • $dateText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = "\"${log.reason}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
