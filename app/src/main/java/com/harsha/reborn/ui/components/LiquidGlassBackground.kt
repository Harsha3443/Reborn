package com.harsha.reborn.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion
import com.harsha.reborn.ui.theme.RebornMotion

/**
 * Full-bleed Liquid Glass ambient backdrop:
 * - Deep burgundy (#0D0609 -> #1A0A10) vertical gradient (or AMOLED / Light variants).
 * - Three soft burgundy color orbs slowly drifting.
 * - Real Modifier.blur (RenderEffect) on Android 12+ (API 31+) applied strictly to the orb layer
 *   so foreground text remains razor-sharp, with smooth radial fallbacks on older versions.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val reduceMotion = LocalRebornReduceMotion.current

    val driftProgress = if (!reduceMotion) {
        val transition = rememberInfiniteTransition(label = "liquid_glass_orb_drift")
        val anim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = RebornMotion.ORB_DRIFT_DURATION_MS,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "orb_phase",
        )
        anim
    } else {
        0.35f
    }

    val useRealBlur = tokens.supportsRealtimeBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val orbLayerModifier = Modifier
        .fillMaxSize()
        .then(
            if (useRealBlur) {
                Modifier.blur(radius = 86.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            }
        )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(tokens.backdropTop, tokens.backdropBottom),
                )
            ),
    ) {
        Canvas(modifier = orbLayerModifier) {
            val w = size.width
            val h = size.height
            val scale = tokens.orbAlphaScale
            val minDim = minOf(w, h)

            // Orb 1: Light burgundy top-left drifting gently
            val orb1Center = Offset(
                x = w * (0.14f + 0.14f * driftProgress),
                y = h * (0.12f + 0.08f * (1f - driftProgress)),
            )
            val orb1Radius = minDim * 0.46f
            drawOrb(
                color = tokens.orbPrimary,
                center = orb1Center,
                radius = orb1Radius,
                peakAlpha = (if (useRealBlur) 0.18f else 0.11f) * scale,
            )

            // Orb 2: Deep burgundy mid-right drifting oppositely
            val orb2Center = Offset(
                x = w * (0.88f - 0.16f * driftProgress),
                y = h * (0.42f + 0.12f * driftProgress),
            )
            val orb2Radius = minDim * 0.56f
            drawOrb(
                color = tokens.orbTertiary,
                center = orb2Center,
                radius = orb2Radius,
                peakAlpha = (if (useRealBlur) 0.24f else 0.15f) * scale,
            )

            // Orb 3: Primary burgundy lower-left glow
            val orb3Center = Offset(
                x = w * (0.24f + 0.18f * (1f - driftProgress)),
                y = h * (0.84f - 0.10f * driftProgress),
            )
            val orb3Radius = minDim * 0.48f
            drawOrb(
                color = tokens.orbSecondary,
                center = orb3Center,
                radius = orb3Radius,
                peakAlpha = (if (useRealBlur) 0.18f else 0.11f) * scale,
            )
        }

        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrb(
    color: Color,
    center: Offset,
    radius: Float,
    peakAlpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = peakAlpha),
                color.copy(alpha = peakAlpha * 0.42f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
