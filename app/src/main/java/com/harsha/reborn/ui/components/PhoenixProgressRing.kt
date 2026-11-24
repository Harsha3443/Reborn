package com.harsha.reborn.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsha.reborn.ui.theme.BurgundyGradientColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion
import com.harsha.reborn.ui.theme.RebornMotion

/**
 * Animated circular progress ring featuring the Burgundy gradient (#A3203A -> #5C0F1E)
 * over a subtle frosted glass track. Respects system reduce-motion preferences.
 */
@Composable
fun PhoenixProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    strokeWidth: Dp = 7.dp,
    sweepColors: List<Color> = BurgundyGradientColors,
    trackColor: Color? = null,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = LocalRebornGlassTokens.current
    val reduceMotion = LocalRebornReduceMotion.current
    val clamped = progress.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = if (reduceMotion) {
            RebornMotion.gentleTween(durationMillis = 0)
        } else {
            RebornMotion.expressiveSpring()
        },
        label = "phoenix_progress_ring",
    )

    val resolvedTrack = trackColor ?: tokens.borderTopLeft.copy(alpha = 0.5f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            // Subtle outer glow
            if (animatedProgress > 0.01f) {
                drawArc(
                    color = sweepColors.first().copy(alpha = 0.09f),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx * 1.6f, cap = StrokeCap.Round),
                )
            }

            // Glass track
            drawArc(
                color = resolvedTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Gradient progress arc rotated so sweep starts cleanly at 12 o'clock (-90deg)
            if (animatedProgress > 0.005f) {
                val gradientColors = if (sweepColors.size >= 2) {
                    sweepColors + sweepColors.first()
                } else {
                    listOf(sweepColors.first(), sweepColors.first())
                }
                rotate(degrees = -90f) {
                    drawArc(
                        brush = Brush.sweepGradient(colors = gradientColors),
                        startAngle = 0f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }
        }
        centerContent()
    }
}
