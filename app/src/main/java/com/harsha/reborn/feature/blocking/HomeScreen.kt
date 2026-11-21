package com.harsha.reborn.feature.blocking

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.harsha.reborn.core.data.model.SelectedAppEntity
import com.harsha.reborn.core.engine.PermissionHealthSnapshot
import com.harsha.reborn.feature.insights.HomeInsightsSummaryCard
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixGradientColors
import com.harsha.reborn.ui.theme.PhoenixOrange
import com.harsha.reborn.ui.theme.StatusHealthy
import com.harsha.reborn.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    onOpenPermissionHealth: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenPrivacyDisclosure: () -> Unit,
    onOpenFriction: () -> Unit = {},
    onOpenStrictMode: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onOpenFeedBlocking: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val health by viewModel.healthSnapshot.collectAsStateWithLifecycle()
    val selectedApps by viewModel.selectedApps.collectAsStateWithLifecycle()
    val protection by viewModel.protectionSummary.collectAsStateWithLifecycle()
    val insightsPreview by viewModel.insightsPreview.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                RebornBrandHeader(onOpenPrivacyDisclosure = onOpenPrivacyDisclosure)
            }

            item {
                PermissionWarningBanner(
                    health = health,
                    onFixClick = onOpenPermissionHealth,
                )
            }

            item {
                HomeInsightsSummaryCard(
                    snapshot = insightsPreview,
                    onOpenInsights = onOpenInsights,
                )
            }

            item {
                ActiveProtectionCard(
                    summary = protection,
                    onOpenRules = onOpenRules,
                    onStopQuickBlock = viewModel::stopQuickBlock,
                )
            }

            item {
                MindfulFrictionCard(
                    summary = protection,
                    onOpenFriction = onOpenFriction,
                )
            }

            item {
                StrictModeSummaryCard(
                    summary = protection,
                    onOpenStrictMode = onOpenStrictMode,
                )
            }

            item {
                GlassCard(
                    onClick = onOpenFeedBlocking,
                    cornerRadius = 26.dp,
                    modifier = Modifier.testTag("home_feed_blocking_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsPaused,
                                contentDescription = null,
                                tint = PhoenixAmber,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Feed Blocking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (protection.inAppFeedBlockingEnabled) {
                                        "Short-video feeds shielded (Shorts, Reels, Spotlight)"
                                    } else {
                                        "Configure Shorts, Reels & Spotlight toggles"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Open Feed Blocking",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SelectedAppsSummaryCard(
                    selectedApps = selectedApps,
                    loadIcon = viewModel::loadIcon,
                    onOpenAppPicker = onOpenAppPicker,
                )
            }
        }
    }
}

@Composable
private fun RebornBrandHeader(
    onOpenPrivacyDisclosure: () -> Unit,
) {
    GlassCard(
        cornerRadius = 30.dp,
        elevated = true,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PhoenixAmber.copy(alpha = 0.14f),
                            PhoenixOrange.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    PhoenixOrange.copy(alpha = 0.28f),
                                    Color.Transparent,
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_reborn_logo),
                        contentDescription = "Reborn Phoenix logo",
                        modifier = Modifier.size(54.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhoenixAmber,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenPrivacyDisclosure,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("home_privacy_disclosure_link"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = StatusHealthy,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.home_offline_link),
                            style = MaterialTheme.typography.labelMedium,
                            color = StatusHealthy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveProtectionCard(
    summary: ActiveProtectionSummary,
    onOpenRules: () -> Unit,
    onStopQuickBlock: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val hasAnyActiveNow = summary.quickBlockRemainingMinutes != null ||
        summary.activeScheduleNames.isNotEmpty() ||
        summary.activeLimitCount > 0 ||
        summary.allowlistEnabled ||
        summary.conditionalRuleCount > 0 ||
        summary.inAppFeedBlockingEnabled ||
        summary.browserRuleCount > 0

    GlassCard(cornerRadius = 28.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (hasAnyActiveNow) PhoenixOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Blocking Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                GlassPillBadge {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = PhoenixOrange,
                    )
                    Text(
                        text = "${summary.blockedTodayCount} blocked today",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (summary.quickBlockRemainingMinutes != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(PhoenixOrange.copy(alpha = 0.14f))
                        .border(
                            width = 1.dp,
                            color = PhoenixAmber.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PhoenixProgressRing(
                            progress = (summary.quickBlockRemainingMinutes / 60f).coerceIn(0.15f, 1f),
                            size = 40.dp,
                            strokeWidth = 4.dp,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = PhoenixAmber,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "Quick Block • ${summary.quickBlockRemainingMinutes}m left",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    GlassButton(
                        onClick = onStopQuickBlock,
                        enabled = !summary.quickBlockPendingDisable && !summary.isStrictLocked,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("home_stop_quick_block_button"),
                    ) {
                        Text(
                            when {
                                summary.isStrictLocked -> "Locked"
                                summary.quickBlockPendingDisable -> "Waiting…"
                                summary.delayedDisableEnabled -> "Stop (${summary.delayedDisableMinutes}m)"
                                else -> "Stop"
                            }
                        )
                    }
                }
            }

            val statusBullets = buildList {
                if (summary.activeScheduleNames.isNotEmpty()) {
                    add("Active schedules: ${summary.activeScheduleNames.joinToString(", ")}")
                }
                if (summary.activeLimitCount > 0) {
                    add("${summary.activeLimitCount} daily time / launch limit${if (summary.activeLimitCount == 1) "" else "s"} enabled")
                }
                if (summary.allowlistEnabled) {
                    add("Allowlist Mode enabled (blocking non-allowed apps)")
                }
                if (summary.conditionalRuleCount > 0) {
                    add("${summary.conditionalRuleCount} conditional smart rule${if (summary.conditionalRuleCount == 1) "" else "s"} active")
                }
                if (summary.inAppFeedBlockingEnabled) {
                    add("In-app short feeds blocked (Shorts / Reels / TikTok)")
                }
                if (summary.browserRuleCount > 0) {
                    add("${summary.browserRuleCount} browser URL/keyword filter${if (summary.browserRuleCount == 1) "" else "s"} active")
                }
                if (summary.grayscaleDimEnabled) {
                    add("Grayscale dim overlay enabled after daily limit")
                }
                if (summary.pendingDisablesCount > 0) {
                    add("${summary.pendingDisablesCount} delayed rule disable${if (summary.pendingDisablesCount == 1) "" else "s"} counting down")
                }
            }

            if (statusBullets.isEmpty() && summary.quickBlockRemainingMinutes == null) {
                Text(
                    text = "No blocking rules active right now. Start a Quick Block, turn on a Schedule, or configure Daily Limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                statusBullets.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GlassPrimaryButton(
                onClick = onOpenRules,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_rules_button"),
            ) {
                Text("Manage Blocking Rules")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MindfulFrictionCard(
    summary: ActiveProtectionSummary,
    onOpenFriction: () -> Unit,
) {
    GlassCard(
        onClick = onOpenFriction,
        cornerRadius = 26.dp,
        modifier = Modifier.testTag("home_friction_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        tint = PhoenixAmber,
                    )
                    Text(
                        text = "Mindful Friction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                GlassPillBadge {
                    Text(
                        text = "${summary.passesUsedToday}/${summary.dailyUnlockPassCap} passes today",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            val delayText = if (summary.delayedDisableEnabled) {
                "${summary.delayedDisableMinutes}m delayed disable"
            } else {
                "instant rule disable"
            }
            Text(
                text = "${summary.pauseSeconds}s breathing pause before unlocking • $delayText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GlassButton(
                onClick = onOpenFriction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_friction_button"),
            ) {
                Text("Friction Settings & Unlock Log")
            }
        }
    }
}

@Composable
private fun StrictModeSummaryCard(
    summary: ActiveProtectionSummary,
    onOpenStrictMode: () -> Unit,
) {
    GlassCard(
        onClick = onOpenStrictMode,
        cornerRadius = 26.dp,
        tintColor = if (summary.isStrictLocked) PhoenixOrange.copy(alpha = 0.14f) else null,
        elevated = summary.isStrictLocked,
        modifier = Modifier.testTag("home_strict_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PhoenixOrange,
                    )
                    Text(
                        text = "Strict Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                GlassPillBadge(
                    containerColor = if (summary.isStrictLocked) {
                        PhoenixOrange.copy(alpha = 0.28f)
                    } else {
                        null
                    },
                    borderColor = if (summary.isStrictLocked) PhoenixAmber else null,
                ) {
                    Text(
                        text = if (summary.isStrictLocked) {
                            "${summary.strictRemainingMinutes}m locked"
                        } else {
                            "Unlocked"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (summary.isStrictLocked) {
                            PhoenixAmber
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = if (summary.isStrictLocked) {
                    "Duration-locked commitment active. System tamper guards and settings lock are protecting your focus."
                } else {
                    "Lock your rules with a timer, PIN, charger, trusted-contact code, and Device Admin uninstall guard."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GlassButton(
                onClick = onOpenStrictMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_strict_button"),
            ) {
                Text(if (summary.isStrictLocked) "View Strict Mode Lock" else "Configure Strict Mode")
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    health: PermissionHealthSnapshot,
    onFixClick: () -> Unit,
) {
    if (health.allGranted) {
        GlassCard(
            cornerRadius = 24.dp,
            tintColor = StatusHealthy.copy(alpha = 0.12f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permission_healthy_banner"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = StatusHealthy,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All ${health.totalCount} permissions active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Accessibility, Usage Access, Overlay, Notifications, and Battery are healthy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        val missingCount = health.totalCount - health.grantedCount
        GlassCard(
            cornerRadius = 26.dp,
            tintColor = StatusWarning.copy(alpha = 0.14f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permission_warning_banner"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhoenixProgressRing(
                        progress = health.grantedCount.toFloat() / health.totalCount.coerceAtLeast(1),
                        size = 46.dp,
                        strokeWidth = 4.5.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$missingCount permission${if (missingCount == 1) "" else "s"} needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Reborn needs these permissions to detect distracting apps and stay alive in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GlassPrimaryButton(
                    onClick = onFixClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_fix_permissions_button"),
                ) {
                    Text("Fix Permissions (${health.grantedCount}/${health.totalCount})")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedAppsSummaryCard(
    selectedApps: List<SelectedAppEntity>,
    loadIcon: suspend (String) -> ImageBitmap?,
    onOpenAppPicker: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = PhoenixOrange,
                    )
                    Text(
                        text = "Selected Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                GlassPillBadge(
                    containerColor = PhoenixOrange.copy(alpha = 0.20f),
                    borderColor = PhoenixAmber.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = "${selectedApps.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = PhoenixAmber,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (selectedApps.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(tokens.cardSubtleFill)
                        .padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsPaused,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pick the apps that pull your attention away. Phone, Emergency, and your Launcher always stay accessible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(selectedApps.take(12), key = { it.packageName }) { app ->
                        SelectedAppPreviewChip(
                            app = app,
                            loadIcon = loadIcon,
                        )
                    }
                }
            }

            GlassButton(
                onClick = onOpenAppPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_app_picker_button"),
            ) {
                Text(if (selectedApps.isEmpty()) "Choose Distracting Apps" else "Manage Selected Apps")
            }
        }
    }
}

@Composable
private fun SelectedAppPreviewChip(
    app: SelectedAppEntity,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val tokens = LocalRebornGlassTokens.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app.packageName)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.cardElevatedFill)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)),
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.appLabel,
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Text(
                    text = app.appLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
