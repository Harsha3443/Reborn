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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isStrictSettingsLocked) {
                    item {
                        GlassCard(
                            cornerRadius = 22.dp,
                            tintColor = PhoenixOrange.copy(alpha = 0.14f),
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
                                    tint = PhoenixOrange,
                                )
                                Text(
                                    text = "Strict Mode is locked — Friction settings cannot be changed until the timer ends.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item {
                    GlassCard(cornerRadius = 26.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Air,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                )
                                Text(
                                    text = "Breathing Pause Countdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "Before an unlock option appears on a blocked app, Reborn guides a ${state.settings.pauseSeconds}-second breath.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Pause duration: ${state.settings.pauseSeconds} seconds",
                                style = MaterialTheme.typography.titleSmall,
                                color = PhoenixAmber,
                            )
                            Slider(
                                value = state.settings.pauseSeconds.toFloat(),
                                onValueChange = { viewModel.setPauseSeconds(it.roundToInt()) },
                                enabled = !state.isStrictSettingsLocked,
                                valueRange = 5f..30f,
                                steps = 24,
                                colors = SliderDefaults.colors(
                                    thumbColor = PhoenixAmber,
                                    activeTrackColor = PhoenixOrange,
                                ),
                                modifier = Modifier.testTag("friction_pause_slider"),
                            )
                        }
                    }
                }

                item {
                    val cap = state.settings.dailyUnlockPassCap.coerceAtLeast(1)
                    val passProgress = (state.passesUsedToday.toFloat() / cap.toFloat()).coerceIn(0f, 1f)
                    GlassCard(cornerRadius = 26.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PhoenixProgressRing(
                                    progress = passProgress,
                                    size = 48.dp,
                                    strokeWidth = 4.5.dp,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Key,
                                        contentDescription = null,
                                        tint = PhoenixAmber,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Intent Unlock Cap",
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
                                        modifier = Modifier.testTag("cap_minus_button"),
                                    ) { Text("-1") }
                                    TextButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            viewModel.setDailyUnlockPassCap(state.settings.dailyUnlockPassCap + 1)
                                        },
                                        enabled = !state.isStrictSettingsLocked && state.settings.dailyUnlockPassCap < 10,
                                        modifier = Modifier.testTag("cap_plus_button"),
                                    ) { Text("+1") }
                                }
                            }
                        }
                    }
                }

                item {
                    GlassCard(cornerRadius = 26.dp) {
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
                                        tint = PhoenixOrange,
                                    )
                                    Column {
                                        Text(
                                            text = "Delayed Rule Disable",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Turning off an active block waits ${state.settings.delayedDisableMinutes} min before taking effect",
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
                                        ) { Text("-1m") }
                                        TextButton(
                                            onClick = {
                                                viewModel.setDelayedDisableMinutes(state.settings.delayedDisableMinutes + 1)
                                            },
                                            enabled = !state.isStrictSettingsLocked && state.settings.delayedDisableMinutes < 30,
                                        ) { Text("+1m") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.pendingDisables.isNotEmpty()) {
                    item {
                        Text(
                            text = "Pending Rule Disables",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(state.pendingDisables, key = { "pending_${it.id}" }) { pending ->
                        val remSec = ((pending.executeAfterEpochMs - state.nowEpochMs) / 1000L).coerceAtLeast(0L)
                        GlassCard(
                            cornerRadius = 22.dp,
                            tintColor = PhoenixOrange.copy(alpha = 0.14f),
                            contentPadding = PaddingValues(14.dp),
                        ) {
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

                item {
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
                                tint = PhoenixAmber,
                            )
                            Text(
                                text = "Intent Unlock Journal",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (state.unlockLogs.isNotEmpty() && !state.isStrictSettingsLocked) {
                            TextButton(
                                onClick = viewModel::clearUnlockLogs,
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
                }

                if (state.unlockLogs.isEmpty()) {
                    item {
                        GlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = "When you use an Intent Unlock pass, your written reason and duration are logged locally here so you can reflect on your habits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(state.unlockLogs, key = { "log_${it.id}" }) { log ->
                        val dateText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            .format(Date(log.grantedAtEpochMs))
                        GlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(14.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    GlassPillBadge {
                                        Text(
                                            text = "${log.durationMinutes} min • $dateText",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                Text(
                                    text = "\"${log.reason}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}
