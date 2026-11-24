package com.harsha.reborn.feature.insights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onOpenPermissionHealth: () -> Unit,
    showBackButton: Boolean = true,
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
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("insights_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(id = R.string.insights_back_cd),
                            )
                        }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    StreakAndTodayOverviewCard(
                        streak = snapshot.streak,
                        todayScreenTimeMinutes = snapshot.todayScreenTimeMinutes,
                        blockedTodayCount = snapshot.blockedTodayCount,
                        unlockAttemptsTodayCount = snapshot.unlockAttemptsTodayCount,
                    )
                }

                if (!snapshot.usageAccessGranted) {
                    item {
                        UsageAccessNoticeCard(onGrantClick = onOpenPermissionHealth)
                    }
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
            }
        }
    }
}

@Composable
private fun StreakAndTodayOverviewCard(
    streak: PhoenixStreakSummary,
    todayScreenTimeMinutes: Int,
    blockedTodayCount: Int,
    unlockAttemptsTodayCount: Int,
) {
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
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        size = 54.dp,
                        strokeWidth = 5.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(tokens.cardSubtleFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_reborn_logo),
                                contentDescription = "Phoenix streak emblem",
                                modifier = Modifier.size(26.dp),
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
                            text = if (streak.bestStreak > 0) {
                                "Best streak: ${streak.bestStreak}d"
                            } else {
                                streak.encouragingSubtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("insights_today_metrics"),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InsightsFlatMetric(
                    value = InsightsCalculator.formatDurationMinutes(todayScreenTimeMinutes),
                    label = "Screen time",
                    modifier = Modifier.weight(1f),
                )
                InsightsFlatMetric(
                    value = blockedTodayCount.toString(),
                    label = "Blocked",
                    valueColor = tokens.accentText,
                    modifier = Modifier.weight(1f),
                )
                InsightsFlatMetric(
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
private fun InsightsFlatMetric(
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
private fun UsageAccessNoticeCard(onGrantClick: () -> Unit) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        tintColor = tokens.statusWarning.copy(alpha = 0.12f),
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
                    tint = tokens.statusWarning,
                )
                Text(
                    text = "Usage Access is off—screen time and top apps cannot be read yet.",
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
private fun TopAppsTodayCard(
    topApps: List<AppScreenTimeItem>,
    usageAccessGranted: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_top_apps_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        "Grant Usage Access above to view today's app breakdown."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                topApps.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                    }
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
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.cardSubtleFill),
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.accentText,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tokens.cardSubtleFill),
            ) {
                val fraction = item.shareFraction.coerceIn(0f, 1f)
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(tokens.accentGradient)),
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Recent Unlock Intentions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            logs.take(5).forEachIndexed { idx, log ->
                if (idx > 0) {
                    HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                }
                val whenText = Instant.ofEpochMilli(log.grantedAtEpochMs)
                    .atZone(zone)
                    .format(timeFormatter)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${log.appLabel} • ${log.durationMinutes}m pass",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = whenText,
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
