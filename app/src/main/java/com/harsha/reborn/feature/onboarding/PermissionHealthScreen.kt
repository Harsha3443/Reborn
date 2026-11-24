package com.harsha.reborn.feature.onboarding

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens

@Composable
fun PermissionHealthScreen(
    onOpenPrivacyDisclosure: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val health by viewModel.healthSnapshot.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("health_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column {
                            Text(
                                text = "Permission Health",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = "Live diagnostics for reliable blocking",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

            // Glass Surface #1: Overall status + grouped permission rows
            item {
                val progress = health.grantedCount.toFloat() / health.totalCount.coerceAtLeast(1)
                GlassCard(
                    cornerRadius = 28.dp,
                    elevated = true,
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PhoenixProgressRing(
                                progress = progress,
                                size = 60.dp,
                                strokeWidth = 5.5.dp,
                                sweepColors = if (health.allGranted) {
                                    listOf(tokens.statusHealthy, tokens.statusHealthy)
                                } else {
                                    tokens.accentGradient
                                },
                            ) {
                                Icon(
                                    imageVector = if (health.allGranted) Icons.Rounded.Shield else Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (health.allGranted) tokens.statusHealthy else tokens.statusWarning,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                        "Reborn is healthy and ready to enforce rules."
                                    } else {
                                        "Tap Fix on any missing item to grant access."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                        health.items.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                            }
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
                    }
                }
            }

            // Glass Surface #2: Device Battery Protection + Privacy link
            item {
                val oem = viewModel.oemGuidance
                GlassCard(cornerRadius = 26.dp, contentPadding = PaddingValues(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryAlert,
                                contentDescription = null,
                                tint = tokens.accentText,
                            )
                            Text(
                                text = "Device Battery Protection (${oem.manufacturerDisplayName})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        oem.steps.forEach { step ->
                            Text(
                                text = "• $step",
                                style = MaterialTheme.typography.bodySmall,
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
                        TextButton(
                            onClick = onOpenPrivacyDisclosure,
                            colors = glassTextButtonColors(),
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
    }
}

@Composable
private fun PermissionHealthRow(
    item: PermissionItemStatus,
    onFixClick: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val statusTint = when {
        item.isGranted -> tokens.statusHealthy
        item.isCritical -> tokens.statusCritical
        else -> tokens.statusWarning
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_card_${item.type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = permissionIcon(item.type),
            contentDescription = null,
            tint = statusTint,
            modifier = Modifier.size(22.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    tint = tokens.statusHealthy,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.statusHealthy,
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
