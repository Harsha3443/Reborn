package com.harsha.reborn.feature.onboarding

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.core.engine.PermissionItemStatus
import com.harsha.reborn.core.engine.RebornPermissionType
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixGradientColors
import com.harsha.reborn.ui.theme.StatusCritical
import com.harsha.reborn.ui.theme.StatusHealthy
import com.harsha.reborn.ui.theme.StatusWarning

@Composable
fun PermissionHealthScreen(
    onOpenPrivacyDisclosure: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val health by viewModel.healthSnapshot.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Permission Health",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Live diagnostics for reliable blocking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.refreshPermissions()
                        },
                        modifier = Modifier.testTag("health_refresh_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh permission status",
                        )
                    }
                }
            }

            item {
                val progress = health.grantedCount.toFloat() / health.totalCount.coerceAtLeast(1)
                GlassCard(
                    cornerRadius = 28.dp,
                    elevated = true,
                    tintColor = if (health.allGranted) StatusHealthy.copy(alpha = 0.12f) else null,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PhoenixProgressRing(
                            progress = progress,
                            size = 64.dp,
                            strokeWidth = 6.dp,
                            sweepColors = if (health.allGranted) {
                                listOf(StatusHealthy, StatusHealthy)
                            } else {
                                PhoenixGradientColors
                            },
                        ) {
                            Icon(
                                imageVector = if (health.allGranted) Icons.Rounded.Shield else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (health.allGranted) StatusHealthy else StatusWarning,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (health.allGranted) {
                                    "All ${health.totalCount} permissions active"
                                } else {
                                    "${health.grantedCount} of ${health.totalCount} permissions granted"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (health.allGranted) {
                                    "Reborn is healthy and ready to enforce blocking rules."
                                } else {
                                    "Tap Fix on any missing item to open the exact Android setting."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(health.items, key = { it.type.name }) { item ->
                PermissionHealthRow(
                    item = item,
                    onFixClick = {
                        if (item.type == RebornPermissionType.NOTIFICATIONS &&
                            !item.isGranted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.openPermissionSettings(item.type)
                        }
                    },
                )
            }

            item {
                val oem = viewModel.oemGuidance
                GlassCard(cornerRadius = 26.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryAlert,
                                contentDescription = null,
                                tint = PhoenixAmber,
                            )
                            Text(
                                text = "Device Battery Protection (${oem.manufacturerDisplayName})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        oem.steps.forEach { step ->
                            Text(
                                text = "• $step",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GlassButton(
                            onClick = { viewModel.openOemBatterySettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("oem_battery_settings_button"),
                        ) {
                            Text("Open Device Battery Settings")
                        }
                    }
                }
            }

            item {
                GlassButton(
                    onClick = onOpenPrivacyDisclosure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_privacy_disclosure_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PrivacyTip,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accessibility & Offline Privacy Disclosure")
                }
            }
        }
    }
}

@Composable
private fun PermissionHealthRow(
    item: PermissionItemStatus,
    onFixClick: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.testTag("permission_card_${item.type.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val statusTint = when {
                item.isGranted -> StatusHealthy
                item.isCritical -> StatusCritical
                else -> StatusWarning
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(statusTint.copy(alpha = 0.16f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = permissionIcon(item.type),
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.whyNeeded,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Granted",
                        tint = StatusHealthy,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusHealthy,
                    )
                }
            } else {
                GlassPrimaryButton(
                    onClick = onFixClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("fix_permission_${item.type.name.lowercase()}"),
                ) {
                    Text("Fix")
                }
            }
        }
    }
}
