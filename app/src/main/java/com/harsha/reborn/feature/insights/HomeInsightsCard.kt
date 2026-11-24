package com.harsha.reborn.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens

@Composable
fun HomeInsightsSummaryCard(
    snapshot: InsightsDashboardSnapshot,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        onClick = onOpenInsights,
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_insights_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = tokens.accentText,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.home_insights_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (snapshot.streak.daysReborn > 0) {
                    Text(
                        text = "${snapshot.streak.daysReborn}d reborn",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.accentText,
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FlatMiniStat(
                    label = "Screen time",
                    value = InsightsCalculator.formatDurationMinutes(snapshot.todayScreenTimeMinutes),
                    modifier = Modifier.weight(1f),
                )
                FlatMiniStat(
                    label = "Blocked",
                    value = snapshot.blockedTodayCount.toString(),
                    valueTint = tokens.accentText,
                    modifier = Modifier.weight(1f),
                )
                FlatMiniStat(
                    label = "Unlocks",
                    value = snapshot.unlockAttemptsTodayCount.toString(),
                    valueTint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

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
private fun FlatMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTint: Color = MaterialTheme.colorScheme.onSurface,
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
            color = valueTint,
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
