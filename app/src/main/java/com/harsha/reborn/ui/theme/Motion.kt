package com.harsha.reborn.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

val LocalRebornReduceMotion = staticCompositionLocalOf { false }

object RebornMotion {
    const val PRESS_SCALE = 0.97f
    const val NAVIGATION_DURATION_MS = 260
    const val ORB_DRIFT_DURATION_MS = 18_000

    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> gentleTween(durationMillis: Int = 450) = tween<T>(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    )
}

@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { isSystemReduceMotionEnabled(context) }
}

fun isSystemReduceMotionEnabled(context: Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}

fun Modifier.rebornPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    val reduceMotion = LocalRebornReduceMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reduceMotion) RebornMotion.PRESS_SCALE else 1f,
        animationSpec = RebornMotion.snappySpring(),
        label = "glass_press_scale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
