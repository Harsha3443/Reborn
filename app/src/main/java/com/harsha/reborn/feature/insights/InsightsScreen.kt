package com.harsha.reborn.feature.insights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.harsha.reborn.core.data.model.AppScreenTimeItem
import com.harsha.reborn.core.data.model.PhoenixStreakSummary
import com.harsha.reborn.core.data.model.UnlockPassLog
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixGradientColors
import com.harsha.reborn.ui.theme.StatusHealthy
import com.harsha.reborn.ui.theme.StatusWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onOpenPermissionHealth: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(id = R.string.insights_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("insights_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.insights_back_cd),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("insights_refresh_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.insights_refresh_cd),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PhoenixStreakBannerCard(streak = snapshot.streak)
                }

                if (!snapshot.usageAccessGranted) {
                    item {
                        UsageAccessNoticeCard(onGrantClick = onOpenPermissionHealth)
                    }
                }

                item {
                    TodayMetricsRow(
                        todayScreenTimeMinutes = snapshot.todayScreenTimeMinutes,
                        blockedTodayCount = snapshot.blockedTodayCount,
                        unlockAttemptsTodayCount = snapshot.unlockAttemptsTodayCount,
                    )
                }

                item {
                    TopAppsTodayCard(
                        topApps = snapshot.topAppsToday,
                        usageAccessGranted = snapshot.usageAccessGranted,
                        loadIcon = viewModel::loadIcon,
                    )
                }

                item {
                    WeeklyScreenTimeChartCard(
                        points = snapshot.weeklyPoints,
                        averageMinutes = snapshot.averageDailyScreenTimeMinutes,
                        selectedIndex = selectedDayIndex,
                        onSelectDay = viewModel::selectDay,
                    )
                }

                item {
                    WeeklyBlocksVsUnlocksChartCard(
                        points = snapshot.weeklyPoints,
                        totalBlocked = snapshot.totalWeeklyBlockedCount,
                        totalUnlocks = snapshot.totalWeeklyUnlockAttemptsCount,
                        selectedIndex = selectedDayIndex,
                        onSelectDay = viewModel::selectDay,
                    )
                }

                if (snapshot.recentUnlockReasons.isNotEmpty()) {
                    item {
                        RecentUnlockIntentNotesCard(logs = snapshot.recentUnlockReasons)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PhoenixStreakBannerCard(streak: PhoenixStreakSummary) {
    val tokens = LocalRebornGlassTokens.current
    val cycleProgress = if (streak.daysReborn <= 0) {
        0.12f
    } else {
        val rem = streak.daysReborn % 7
        if (rem == 0) 1f else rem / 7f
    }

    GlassCard(
        elevated = true,
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_phoenix_streak_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    PhoenixProgressRing(
                        progress = cycleProgress,
                        size = 56.dp,
                        strokeWidth = 5.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(tokens.cardElevatedFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_reborn_logo),
                                contentDescription = "Phoenix streak emblem",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = streak.encouragingTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Gentle Phoenix Rhythm",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (streak.bestStreak > 0) {
                    GlassPillBadge {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = PhoenixAmber,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Best ${streak.bestStreak}d",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = streak.encouragingSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageAccessNoticeCard(onGrantClick: () -> Unit) {
    GlassCard(
        tintColor = StatusWarning.copy(alpha = 0.14f),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_usage_access_banner"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = StatusWarning,
                )
                Text(
                    text = "Usage Access is off—screen time and top apps cannot be read yet. Blocked urges and unlock notes are still tracked locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            GlassPrimaryButton(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("insights_grant_usage_access_button"),
            ) {
                Text("Enable Usage Access")
            }
        }
    }
}

@Composable
private fun TodayMetricsRow(
    todayScreenTimeMinutes: Int,
    blockedTodayCount: Int,
    unlockAttemptsTodayCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_today_metrics"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricTile(
            icon = Icons.Rounded.Schedule,
            tint = MaterialTheme.colorScheme.primary,
            value = InsightsCalculator.formatDurationMinutes(todayScreenTimeMinutes),
            label = "Screen time",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            icon = Icons.Rounded.Block,
            tint = StatusHealthy,
            value = blockedTodayCount.toString(),
            label = "Blocked",
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            icon = Icons.Rounded.LockOpen,
            tint = PhoenixAmber,
            value = unlockAttemptsTodayCount.toString(),
            label = "Unlocks",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(14.dp),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.16f))
                    .border(1.dp, tokens.borderTopLeft.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopAppsTodayCard(
    topApps: List<AppScreenTimeItem>,
    usageAccessGranted: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_top_apps_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Top Apps Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (topApps.isEmpty()) {
                Text(
                    text = if (usageAccessGranted) {
                        "No foreground app usage recorded yet today."
                    } else {
                        "Grant Usage Access above to break down today's screen time by app."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                topApps.forEach { item ->
                    TopAppRow(item = item, loadIcon = loadIcon)
                }
            }
        }
    }
}

@Composable
private fun TopAppRow(
    item: AppScreenTimeItem,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val tokens = LocalRebornGlassTokens.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = item.packageName) {
        value = loadIcon(item.packageName)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.cardElevatedFill)
                .border(1.dp, tokens.borderTopLeft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.appLabel,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(
                    text = item.appLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = InsightsCalculator.formatDurationMinutes(item.minutesToday),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tokens.cardSubtleFill)
                    .border(0.5.dp, tokens.borderBottomRight, RoundedCornerShape(50)),
            ) {
                val fraction = item.shareFraction.coerceIn(0f, 1f)
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(PhoenixGradientColors)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentUnlockIntentNotesCard(logs: List<UnlockPassLog>) {
    val tokens = LocalRebornGlassTokens.current
    val timeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    val zone = ZoneId.systemDefault()

    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_unlock_reasons_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Recent Unlock Intentions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            logs.take(5).forEach { log ->
                val whenText = Instant.ofEpochMilli(log.grantedAtEpochMs)
                    .atZone(zone)
                    .format(timeFormatter)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.cardSubtleFill)
                        .border(1.dp, tokens.borderBottomRight, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${log.appLabel} • ${log.durationMinutes}m pass",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = whenText,
                                style = MaterialTheme.typography.labelSmall,
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
