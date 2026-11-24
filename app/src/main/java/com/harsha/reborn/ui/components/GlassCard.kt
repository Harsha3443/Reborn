package com.harsha.reborn.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.rebornPressScale

/**
 * Translucent frosted Liquid Glass card:
 * - 8-14% alpha white fill (dark) / high-contrast frosted surface (light)
 * - 1dp diagonal gradient border (white 30% top-left -> 5% bottom-right)
 * - 24-32dp corner radius, soft ambient shadow, subtle inner top highlight
 * - Optional spring press-scale (0.97f) + haptic feedback when clickable
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    cornerRadius: Dp = 26.dp,
    elevated: Boolean = false,
    tintColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }

    val baseFill = when {
        tintColor != null -> tintColor
        elevated -> tokens.cardElevatedFill
        else -> tokens.cardFill
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(tokens.borderTopLeft, tokens.borderBottomRight),
    )

    val interactiveModifier = if (onClick != null) {
        Modifier
            .rebornPressScale(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp) else Modifier)
            .shadow(
                elevation = if (elevated) 8.dp else 4.dp,
                shape = shape,
                ambientColor = tokens.shadowColor,
                spotColor = tokens.shadowColor,
            )
            .clip(shape)
            .background(color = baseFill, shape = shape)
            .drawBehind {
                // Subtle top inner glass sheen
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tokens.innerHighlight,
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.28f,
                    )
                )
            }
            .border(BorderStroke(0.75.dp, borderBrush), shape = shape)
            .then(interactiveModifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
fun GlassPillBadge(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val fill = containerColor ?: tokens.cardElevatedFill
    val shape = RoundedCornerShape(50)
    val stroke = if (borderColor != null) {
        BorderStroke(0.75.dp, borderColor.copy(alpha = 0.55f))
    } else {
        BorderStroke(
            0.75.dp,
            Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)),
        )
    }
    Surface(
        shape = shape,
        color = fill,
        border = stroke,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val tokens = LocalRebornGlassTokens.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp)
                .shadow(16.dp, shape = shape, ambientColor = tokens.shadowColor, spotColor = tokens.shadowColor)
                .clip(shape)
                .background(tokens.modalSheetFill, shape = shape)
                .border(
                    width = 0.75.dp,
                    brush = Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)),
                    shape = shape,
                )
                .padding(24.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.material3.ProvideTextStyle(
                        value = MaterialTheme.typography.headlineSmall,
                        content = title,
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        text()
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun glassOutlinedTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalRebornGlassTokens.current.cardElevatedFill,
    unfocusedContainerColor = LocalRebornGlassTokens.current.cardSubtleFill,
    disabledContainerColor = LocalRebornGlassTokens.current.cardSubtleFill.copy(alpha = 0.4f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = LocalRebornGlassTokens.current.borderTopLeft,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun glassFilterChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    containerColor = LocalRebornGlassTokens.current.cardSubtleFill,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = LocalRebornGlassTokens.current.accentTintFill,
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = LocalRebornGlassTokens.current.accentText,
)

@Composable
fun glassFilterChipBorder(selected: Boolean): BorderStroke {
    val tokens = LocalRebornGlassTokens.current
    return if (selected) {
        BorderStroke(0.75.dp, tokens.accentBorder)
    } else {
        BorderStroke(0.75.dp, Brush.linearGradient(listOf(tokens.borderTopLeft, tokens.borderBottomRight)))
    }
}

@Composable
fun glassSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = LocalRebornGlassTokens.current.cardElevatedFill,
    uncheckedBorderColor = LocalRebornGlassTokens.current.borderTopLeft,
)

@Composable
fun glassSliderColors() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = LocalRebornGlassTokens.current.accentText,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = LocalRebornGlassTokens.current.cardElevatedFill,
)

@Composable
fun glassTextButtonColors() = androidx.compose.material3.ButtonDefaults.textButtonColors(
    contentColor = LocalRebornGlassTokens.current.accentText,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors() = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = LocalRebornGlassTokens.current.floatingBarFill,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
)

