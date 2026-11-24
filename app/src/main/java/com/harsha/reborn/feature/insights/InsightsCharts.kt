package com.harsha.reborn.feature.insights

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.model.DailyInsightPoint
import com.harsha.reborn.core.engine.InsightsCalculator
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion

@Composable
fun WeeklyScreenTimeChartCard(
    points: List<DailyInsightPoint>,
    averageMinutes: Int,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    val reduceMotion = LocalRebornReduceMotion.current

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(points) { revealed = true }
    val animProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 550),
        label = "screen_time_bar_reveal",
    )

    val safeIndex = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val selectedPoint = points.getOrNull(safeIndex)
    val maxMinutes = (points.maxOfOrNull { it.screenTimeMinutes } ?: 0).coerceAtLeast(60)
    val outlineColor = tokens.borderBottomRight
    val trackColor = tokens.cardSubtleFill
    val avgLineColor = tokens.accentText

    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_screentime_chart"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Weekly Screen Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Daily avg ${InsightsCalculator.formatDurationMinutes(averageMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedPoint != null) {
                    Text(
                        text = "${selectedPoint.dayLabel}: ${
                            InsightsCalculator.formatDurationMinutes(selectedPoint.screenTimeMinutes)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.accentText,
                    )
                }
            }

            if (points.isEmpty()) {
                Text(
                    text = "No daily data available yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .semantics {
                            contentDescription = "Weekly screen time bar chart for the last 7 days"
                        },
                ) {
                    val count = points.size
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.48f).coerceAtMost(36.dp.toPx())
                    val topPad = 8.dp.toPx()
                    val bottomPad = 6.dp.toPx()
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                    listOf(0f, 0.5f).forEach { frac ->
                        val y = topPad + chartHeight * frac
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.75.dp.toPx(),
                        )
                    }

                    if (averageMinutes > 0) {
                        val avgRatio = (averageMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
                        val avgY = topPad + chartHeight * (1f - avgRatio)
                        drawLine(
                            color = avgLineColor.copy(alpha = 0.65f),
                            start = Offset(0f, avgY),
                            end = Offset(size.width, avgY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                        )
                    }

                    points.forEachIndexed { idx, point ->
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, topPad),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        )

                        val fraction = (point.screenTimeMinutes.toFloat() / maxMinutes.toFloat())
                            .coerceIn(0f, 1f) * animProgress
                        val fillHeight = (chartHeight * fraction).coerceAtLeast(
                            if (point.screenTimeMinutes > 0) 6.dp.toPx() else 0f
                        )
                        if (fillHeight > 0f) {
                            val topY = topPad + (chartHeight - fillHeight)
                            val isSelected = idx == safeIndex
                            val brush = Brush.verticalGradient(
                                colors = if (isSelected) {
                                    tokens.accentGradient
                                } else {
                                    tokens.accentGradient.map { it.copy(alpha = 0.72f) }
                                },
                                startY = topY,
                                endY = topPad + chartHeight,
                            )
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(left, topY),
                                size = Size(barWidth, fillHeight),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            )
                        }
                    }
                }

                DaySelectorRow(
                    points = points,
                    selectedIndex = safeIndex,
                    onSelectDay = onSelectDay,
                    tagPrefix = "screentime_day",
                )
            }
        }
    }
}

@Composable
fun WeeklyBlocksVsUnlocksChartCard(
    points: List<DailyInsightPoint>,
    totalBlocked: Int,
    totalUnlocks: Int,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    val reduceMotion = LocalRebornReduceMotion.current

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(points) { revealed = true }
    val animProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 550),
        label = "blocks_unlocks_reveal",
    )

    val safeIndex = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val selectedPoint = points.getOrNull(safeIndex)
    val maxCount = (points.maxOfOrNull { maxOf(it.blockedCount, it.unlockAttemptsCount) } ?: 0)
        .coerceAtLeast(4)
    val outlineColor = tokens.borderBottomRight
    val blockedColor = tokens.accentGradient.first()
    val unlockColor = MaterialTheme.colorScheme.onSurfaceVariant

    GlassCard(
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_blocks_unlocks_chart"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(
                    text = "Blocks vs. Unlock Passes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$totalBlocked blocked • $totalUnlocks unlock passes this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendPill(color = blockedColor, label = "Blocked urges")
                LegendPill(color = unlockColor, label = "Unlock passes")
            }

            if (points.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .semantics {
                            contentDescription = "Weekly grouped bar chart comparing blocked attempts and unlock passes"
                        },
                ) {
                    val count = points.size
                    val slotWidth = size.width / count
                    val pairWidth = (slotWidth * 0.56f).coerceAtMost(38.dp.toPx())
                    val gap = 4.dp.toPx()
                    val singleBarWidth = ((pairWidth - gap) / 2f).coerceAtLeast(6.dp.toPx())
                    val topPad = 8.dp.toPx()
                    val bottomPad = 6.dp.toPx()
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = topPad + chartHeight * frac
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.75.dp.toPx(),
                        )
                    }

                    points.forEachIndexed { idx, point ->
                        val startX = idx * slotWidth + (slotWidth - pairWidth) / 2f
                        val isSelected = idx == safeIndex
                        val alpha = if (isSelected) 1f else 0.72f

                        val blockedFrac = (point.blockedCount.toFloat() / maxCount.toFloat())
                            .coerceIn(0f, 1f) * animProgress
                        val blockedH = (chartHeight * blockedFrac).coerceAtLeast(
                            if (point.blockedCount > 0) 6.dp.toPx() else 2.dp.toPx()
                        )
                        drawRoundRect(
                            color = if (point.blockedCount > 0) blockedColor.copy(alpha = alpha) else outlineColor,
                            topLeft = Offset(startX, topPad + chartHeight - blockedH),
                            size = Size(singleBarWidth, blockedH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        )

                        val unlockFrac = (point.unlockAttemptsCount.toFloat() / maxCount.toFloat())
                            .coerceIn(0f, 1f) * animProgress
                        val unlockH = (chartHeight * unlockFrac).coerceAtLeast(
                            if (point.unlockAttemptsCount > 0) 6.dp.toPx() else 2.dp.toPx()
                        )
                        drawRoundRect(
                            color = if (point.unlockAttemptsCount > 0) unlockColor.copy(alpha = alpha) else outlineColor,
                            topLeft = Offset(startX + singleBarWidth + gap, topPad + chartHeight - unlockH),
                            size = Size(singleBarWidth, unlockH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        )
                    }
                }

                DaySelectorRow(
                    points = points,
                    selectedIndex = safeIndex,
                    onSelectDay = onSelectDay,
                    tagPrefix = "blocks_day",
                )

                if (selectedPoint != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                    Text(
                        text = "${selectedPoint.dayLabel} (${selectedPoint.dateIso}): " +
                            "${selectedPoint.blockedCount} blocked • " +
                            "${selectedPoint.unlockAttemptsCount} unlock attempts" +
                            if (selectedPoint.isRebornDay) " • Reborn day" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySelectorRow(
    points: List<DailyInsightPoint>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit,
    tagPrefix: String,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        points.forEachIndexed { idx, point ->
            val selected = idx == selectedIndex
            val shape = RoundedCornerShape(14.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(
                        if (selected) {
                            tokens.accentTintFill
                        } else {
                            Color.Transparent
                        }
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                0.75.dp,
                                tokens.accentBorder,
                                shape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelectDay(idx)
                    }
                    .padding(vertical = 8.dp)
                    .testTag("${tagPrefix}_$idx"),
            ) {
                Text(
                    text = point.dayLabel.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (point.isRebornDay) tokens.accentText else tokens.borderTopLeft
                        ),
                )
            }
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
