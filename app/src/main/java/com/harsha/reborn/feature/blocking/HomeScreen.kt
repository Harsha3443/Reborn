package com.harsha.reborn.feature.blocking

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.core.engine.PermissionHealthSnapshot
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens

private const val DAILY_SCREEN_TIME_GOAL_MINUTES = 120

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                CompactTopHeader(
                    health = health,
                    onOpenPermissionHealth = onOpenPermissionHealth,
                )
            }

            if (!health.allGranted) {
                item {
                    MissingPermissionWarningCard(
                        health = health,
                        onFixClick = onOpenPermissionHealth,
                    )
                }
            }

            item {
                MainFocusCard(
                    todayScreenTimeMinutes = insightsPreview.todayScreenTimeMinutes,
                    blockedTodayCount = insightsPreview.blockedTodayCount,
                    unlockAttemptsTodayCount = insightsPreview.unlockAttemptsTodayCount,
                    protection = protection,
                    onQuickBlockClick = onOpenRules,
                    onStopQuickBlock = viewModel::stopQuickBlock,
                )
            }

            item {
                val activeRulesCount = protection.activeScheduleNames.size +
                    protection.activeLimitCount +
                    protection.conditionalRuleCount +
                    (if (protection.allowlistEnabled) 1 else 0) +
                    (if (protection.inAppFeedBlockingEnabled) 1 else 0) +
                    (if (protection.quickBlockRemainingMinutes != null) 1 else 0)

                val rulesStatus = when {
                    activeRulesCount > 0 && selectedApps.isNotEmpty() ->
                        "$activeRulesCount active • ${selectedApps.size} apps"
                    activeRulesCount > 0 ->
                        "$activeRulesCount active rule${if (activeRulesCount == 1) "" else "s"}"
                    selectedApps.isNotEmpty() ->
                        "${selectedApps.size} selected app${if (selectedApps.size == 1) "" else "s"}"
                    else ->
                        "Schedules, limits & app list"
                }

                val frictionStatus = "${protection.pauseSeconds}s pause • " +
                    "${protection.passesUsedToday}/${protection.dailyUnlockPassCap} passes today"

                val strictStatus = if (protection.isStrictLocked) {
                    "Locked • ${protection.strictRemainingMinutes}m remaining"
                } else {
                    "Unlocked • Timer & tamper guards"
                }

                val insightsStatus = if (insightsPreview.streak.daysReborn > 0) {
                    "${insightsPreview.streak.daysReborn}d streak • Trends & app usage"
                } else {
                    "Weekly trends & app usage"
                }

                GroupedNavigationCard(
                    rulesStatus = rulesStatus,
                    frictionStatus = frictionStatus,
                    strictStatus = strictStatus,
                    isStrictLocked = protection.isStrictLocked,
                    insightsStatus = insightsStatus,
                    onOpenRules = onOpenRules,
                    onOpenFriction = onOpenFriction,
                    onOpenStrictMode = onOpenStrictMode,
                    onOpenInsights = onOpenInsights,
                )
            }
        }
    }
}

@Composable
private fun CompactTopHeader(
    health: PermissionHealthSnapshot,
    onOpenPermissionHealth: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_reborn_logo),
                contentDescription = "Reborn logo",
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (health.allGranted) tokens.statusHealthy else tokens.statusWarning),
                )
                Text(
                    text = if (health.allGranted) "Protected" else "Setup needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (health.allGranted) tokens.statusHealthy else tokens.statusWarning,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (health.allGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tokens.statusHealthy.copy(alpha = 0.12f))
                    .border(0.75.dp, tokens.borderTopLeft, RoundedCornerShape(50))
                    .clickable(onClick = onOpenPermissionHealth)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("permission_healthy_banner"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "All permissions active",
                    tint = tokens.statusHealthy,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "All set",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.statusHealthy,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MissingPermissionWarningCard(
    health: PermissionHealthSnapshot,
    onFixClick: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val missingCount = health.totalCount - health.grantedCount
    GlassCard(
        cornerRadius = 24.dp,
        tintColor = tokens.statusWarning.copy(alpha = 0.12f),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_warning_banner"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = tokens.statusWarning,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$missingCount permission${if (missingCount == 1) "" else "s"} needed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Grant access so Reborn can block distracting apps reliably.",
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

@Composable
private fun MainFocusCard(
    todayScreenTimeMinutes: Int,
    blockedTodayCount: Int,
    unlockAttemptsTodayCount: Int,
    protection: ActiveProtectionSummary,
    onQuickBlockClick: () -> Unit,
    onStopQuickBlock: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val goalProgress = (todayScreenTimeMinutes.toFloat() / DAILY_SCREEN_TIME_GOAL_MINUTES.toFloat())
        .coerceIn(0f, 1f)
    val isQuickActive = protection.quickBlockRemainingMinutes != null

    GlassCard(
        cornerRadius = 28.dp,
        elevated = true,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_focus_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhoenixProgressRing(
                progress = if (todayScreenTimeMinutes == 0) 0.04f else goalProgress,
                size = 176.dp,
                strokeWidth = 11.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = InsightsCalculator.formatDurationMinutes(todayScreenTimeMinutes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "of ${InsightsCalculator.formatDurationMinutes(DAILY_SCREEN_TIME_GOAL_MINUTES)} goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isQuickActive && protection.quickBlockRemainingMinutes != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = tokens.accentText,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Quick Block • ${protection.quickBlockRemainingMinutes}m remaining",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = tokens.accentText,
                    )
                }
                GlassPrimaryButton(
                    onClick = onStopQuickBlock,
                    enabled = !protection.quickBlockPendingDisable && !protection.isStrictLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_stop_quick_block_button"),
                ) {
                    Text(
                        when {
                            protection.isStrictLocked -> "Quick Block Locked"
                            protection.quickBlockPendingDisable -> "Cooling down…"
                            protection.delayedDisableEnabled -> "Stop Quick Block (${protection.delayedDisableMinutes}m)"
                            else -> "Stop Quick Block"
                        }
                    )
                }
            } else {
                GlassPrimaryButton(
                    onClick = onQuickBlockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_open_rules_button"),
                ) {
                    Text("Quick Block")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                thickness = 0.5.dp,
                color = tokens.borderBottomRight,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_today_stats_row"),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FlatStatColumn(
                    value = InsightsCalculator.formatDurationMinutes(todayScreenTimeMinutes),
                    label = "Screen time",
                    modifier = Modifier.weight(1f),
                )
                FlatStatColumn(
                    value = blockedTodayCount.toString(),
                    label = "Blocked",
                    valueColor = tokens.accentText,
                    modifier = Modifier.weight(1f),
                )
                FlatStatColumn(
                    value = unlockAttemptsTodayCount.toString(),
                    label = "Unlocks",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlatStatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupedNavigationCard(
    rulesStatus: String,
    frictionStatus: String,
    strictStatus: String,
    isStrictLocked: Boolean,
    insightsStatus: String,
    onOpenRules: () -> Unit,
    onOpenFriction: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current

    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_navigation_list"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GroupedNavRow(
                icon = Icons.Rounded.Shield,
                iconTint = tokens.accentText,
                title = "Blocking Rules",
                status = rulesStatus,
                testTag = "home_row_rules",
                onClick = onOpenRules,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = tokens.borderBottomRight,
            )
            GroupedNavRow(
                icon = Icons.Rounded.Air,
                iconTint = tokens.accentText,
                title = "Friction",
                status = frictionStatus,
                testTag = "home_row_friction",
                onClick = onOpenFriction,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = tokens.borderBottomRight,
            )
            GroupedNavRow(
                icon = Icons.Rounded.Lock,
                iconTint = tokens.accentText,
                title = "Strict Mode",
                status = strictStatus,
                statusColor = if (isStrictLocked) tokens.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                testTag = "home_row_strict",
                onClick = onOpenStrictMode,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = tokens.borderBottomRight,
            )
            GroupedNavRow(
                icon = Icons.Rounded.BarChart,
                iconTint = tokens.accentText,
                title = "Weekly Insights",
                status = insightsStatus,
                testTag = "home_row_insights",
                onClick = onOpenInsights,
            )
        }
    }
}

@Composable
private fun GroupedNavRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    status: String,
    testTag: String,
    onClick: () -> Unit,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                indication = ripple(),
                interactionSource = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
