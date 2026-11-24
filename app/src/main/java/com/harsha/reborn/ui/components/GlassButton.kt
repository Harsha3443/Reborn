package com.harsha.reborn.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.rebornPressScale

/**
 * Primary Liquid Glass action button using the Burgundy gradient (#A3203A -> #5C0F1E),
 * spring press scale (0.97f), haptic feedback, and high-contrast ivory ink (#F5EDEF).
 */
@Composable
fun GlassPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)

    val backgroundBrush = if (enabled) {
        Brush.linearGradient(colors = tokens.accentGradient)
    } else {
        SolidColor(tokens.cardSubtleFill)
    }
    val contentColor = if (enabled) {
        tokens.onAccentGradient
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val borderBrush = if (enabled) {
        Brush.linearGradient(
            colors = listOf(tokens.selectedBorderTopLeft, tokens.selectedBorderBottomRight)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(tokens.borderTopLeft, tokens.borderBottomRight)
        )
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .rebornPressScale(interactionSource = interactionSource, enabled = enabled)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = shape,
                ambientColor = tokens.buttonShadowStart,
                spotColor = tokens.buttonShadowEnd,
            )
            .clip(shape)
            .background(brush = backgroundBrush, shape = shape)
            .border(BorderStroke(0.75.dp, borderBrush), shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * Secondary frosted glass button with translucent glass fill, thin gradient border,
 * spring press scale (0.97f), and haptic feedback.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)

    val fill = when {
        !enabled -> tokens.cardSubtleFill.copy(alpha = 0.05f)
        tintColor != null -> tintColor
        else -> tokens.cardElevatedFill
    }
    val effectiveContentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.42f)
    val borderBrush = Brush.linearGradient(
        colors = listOf(tokens.borderTopLeft, tokens.borderBottomRight)
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .rebornPressScale(interactionSource = interactionSource, enabled = enabled)
            .clip(shape)
            .background(color = fill, shape = shape)
            .border(BorderStroke(0.75.dp, borderBrush), shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}
