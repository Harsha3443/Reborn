package com.harsha.reborn.feature.friction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.harsha.reborn.ui.components.GlassBottomSheet
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BreathingPauseShield(
    appLabel: String,
    ruleTitle: String,
    ruleDetail: String,
    totalPauseSeconds: Int,
    remainingPauseSeconds: Int,
    passesUsedToday: Int,
    dailyPassCap: Int,
    errorMessage: String?,
    unlockDisabledReason: String? = null,
    onGoHome: () -> Unit,
    onReturnToSafeApp: (() -> Unit)? = null,
    onOpenReborn: () -> Unit,
    onRequestUnlock: (reason: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reasonText by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableIntStateOf(5) }
    var showUnlockForm by remember { mutableStateOf(false) }

    val reduceMotion = LocalRebornReduceMotion.current
    val breathScale = if (!reduceMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing_cycle")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.68f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath_scale",
        )
        anim
    } else {
        0.86f
    }

    val pauseCompleted = remainingPauseSeconds <= 0
    val passesRemaining = (dailyPassCap - passesUsedToday).coerceAtLeast(0)
    val countdownProgress = if (totalPauseSeconds > 0) {
        1f - (remainingPauseSeconds.toFloat() / totalPauseSeconds.toFloat())
    } else {
        1f
    }

    if (showUnlockForm && pauseCompleted && unlockDisabledReason == null && passesRemaining > 0) {
        GlassBottomSheet(
            onDismissRequest = { showUnlockForm = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Why do you need $appLabel right now?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Write a brief intention to use 1 of your $passesRemaining remaining passes today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    placeholder = { Text("e.g. Reply to family message") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("intent_unlock_reason_input"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15).forEach { mins ->
                        val isSelected = selectedDuration == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDuration = mins },
                            colors = glassFilterChipColors(),
                            border = glassFilterChipBorder(isSelected),
                            label = { Text("$mins min") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("intent_duration_$mins"),
                        )
                    }
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                GlassPrimaryButton(
                    onClick = { onRequestUnlock(reasonText, selectedDuration) },
                    enabled = reasonText.trim().length >= 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_intent_unlock_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock for $selectedDuration min")
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Breathing Phoenix Halo + Progress Ring
            Box(
                modifier = Modifier.size(176.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outerRadius = (size.minDimension / 2f) - 10.dp.toPx()
                    drawCircle(
                        color = PhoenixOrange.copy(alpha = 0.16f * breathScale),
                        radius = outerRadius * breathScale,
                    )
                    drawCircle(
                        color = PhoenixAmber.copy(alpha = 0.22f),
                        radius = outerRadius * 0.72f * breathScale,
                    )
                }
                PhoenixProgressRing(
                    progress = countdownProgress.coerceIn(0f, 1f),
                    size = 156.dp,
                    strokeWidth = 7.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_reborn_logo),
                            contentDescription = "Reborn Phoenix",
                            modifier = Modifier.size(56.dp),
                        )
                        if (!pauseCompleted) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${remainingPauseSeconds}s",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PhoenixAmber,
                            )
                        }
                    }
                }
            }

            GlassPillBadge(
                containerColor = PhoenixOrange.copy(alpha = 0.18f),
                borderColor = PhoenixAmber.copy(alpha = 0.45f),
            ) {
                Text(
                    text = ruleTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = PhoenixAmber,
                )
            }

            Text(
                text = if (!pauseCompleted) {
                    "Take a breath before opening $appLabel"
                } else {
                    "$appLabel is paused"
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (!pauseCompleted) {
                    if (breathScale > 0.84f) "Breathe in slowly…" else "Breathe out gently…"
                } else {
                    stringResource(id = R.string.app_tagline)
                },
                style = MaterialTheme.typography.titleMedium,
                color = PhoenixAmber,
                textAlign = TextAlign.Center,
            )

            GlassCard(
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = ruleDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Primary escape: always available immediately
            GlassPrimaryButton(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("block_screen_go_home_button"),
            ) {
                Icon(imageVector = Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Go Home", style = MaterialTheme.typography.labelLarge)
            }

            if (onReturnToSafeApp != null) {
                GlassButton(
                    onClick = onReturnToSafeApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_screen_back_to_safe_app_button"),
                ) {
                    Text("Back to App (Exit Feed / Page)")
                }
            }

            // Intent-based unlock appears only after breathing pause finishes
            AnimatedVisibility(visible = pauseCompleted) {
                GlassCard(
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (unlockDisabledReason != null) {
                            Text(
                                text = unlockDisabledReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("strict_unlock_locked_notice"),
                            )
                        } else if (passesRemaining <= 0) {
                            Text(
                                text = if (dailyPassCap == 0) {
                                    "Intent unlock passes are disabled."
                                } else {
                                    "Daily unlock cap reached ($passesUsedToday / $dailyPassCap passes used today)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            GlassButton(
                                onClick = { showUnlockForm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_intent_unlock_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("I need this app ($passesRemaining passes left)")
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onOpenReborn,
                modifier = Modifier.testTag("block_screen_open_reborn_button"),
            ) {
                Text("Open Reborn")
            }
        }
    }
}
