package com.harsha.reborn.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.harsha.reborn.core.data.model.InsightsDashboardSnapshot
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange
import com.harsha.reborn.ui.theme.StatusHealthy

@Composable
fun HomeInsightsSummaryCard(
    snapshot: InsightsDashboardSnapshot,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        onClick = onOpenInsights,
        cornerRadius = 28.dp,
        elevated = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_insights_card"),
    ) {
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
                    PhoenixProgressRing(
                        progress = (snapshot.streak.daysReborn.coerceAtLeast(1) / 7f).coerceIn(0.15f, 1f),
                        size = 38.dp,
                        strokeWidth = 3.5.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = PhoenixOrange,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.home_insights_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                GlassPillBadge(
                    containerColor = PhoenixOrange.copy(alpha = 0.20f),
                    borderColor = PhoenixAmber.copy(alpha = 0.45f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = PhoenixAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (snapshot.streak.daysReborn > 0) {
                            "${snapshot.streak.daysReborn}d reborn"
                        } else {
                            "Begin again"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PhoenixAmber,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStatChip(
                    label = "Screen time",
                    value = InsightsCalculator.formatDurationMinutes(snapshot.todayScreenTimeMinutes),
                    modifier = Modifier.weight(1f),
                )
                MiniStatChip(
                    label = "Blocked",
                    value = snapshot.blockedTodayCount.toString(),
                    valueTint = StatusHealthy,
                    modifier = Modifier.weight(1f),
                )
                MiniStatChip(
                    label = "Unlocks",
                    value = snapshot.unlockAttemptsTodayCount.toString(),
                    valueTint = PhoenixAmber,
                    modifier = Modifier.weight(1f),
                )
            }

            val topLine = snapshot.topAppsToday.take(2).joinToString(" • ") { item ->
                "${item.appLabel} (${InsightsCalculator.formatDurationMinutes(item.minutesToday)})"
            }
            if (topLine.isNotEmpty()) {
                Text(
                    text = "Top today: $topLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = snapshot.streak.encouragingSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            GlassButton(
                onClick = onOpenInsights,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_insights_button"),
            ) {
                Text(stringResource(id = R.string.home_open_insights_button))
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
private fun MiniStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTint: Color? = null,
) {
    val tokens = LocalRebornGlassTokens.current
    val chipShape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(chipShape)
            .background(tokens.cardSubtleFill, shape = chipShape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)),
                shape = chipShape,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueTint ?: MaterialTheme.colorScheme.onSurface,
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
