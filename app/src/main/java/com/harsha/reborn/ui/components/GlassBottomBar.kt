package com.harsha.reborn.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.RebornMotion
import com.harsha.reborn.ui.theme.rebornPressScale

@Immutable
data class GlassNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val badgeCount: Int = 0,
    val testTag: String,
    val onClick: () -> Unit,
)

/**
 * Floating Liquid Glass pill bottom navigation bar with:
 * - Translucent frosted pill container + thin gradient border + shadow
 * - Active Burgundy gradient highlight indicator
 * - 48dp+ accessible touch targets, Material Symbols Rounded, haptics, and navigationBarsPadding
 */
@Composable
fun GlassBottomBar(
    items: List<GlassNavItem>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    val pillShape = RoundedCornerShape(32.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(tokens.borderTopLeft, tokens.borderBottomRight)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = pillShape,
                    ambientColor = tokens.shadowColor,
                    spotColor = tokens.shadowColor,
                )
                .clip(pillShape)
                .background(tokens.floatingBarFill, shape = pillShape)
                .border(BorderStroke(0.75.dp, borderBrush), shape = pillShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                GlassBottomBarTab(
                    item = item,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassBottomBarTab(
    item: GlassNavItem,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val itemShape = RoundedCornerShape(26.dp)

    val contentColor by animateColorAsState(
        targetValue = if (item.selected) {
            tokens.onAccentGradient
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = RebornMotion.snappySpring(),
        label = "nav_tab_color",
    )

    val bgBrush = if (item.selected) {
        Brush.linearGradient(colors = tokens.accentGradient)
    } else {
        SolidColor(Color.Transparent)
    }

    Column(
        modifier = modifier
            .heightIn(min = 50.dp)
            .rebornPressScale(interactionSource = interactionSource)
            .clip(itemShape)
            .background(brush = bgBrush, shape = itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Tab,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    item.onClick()
                },
            )
            .semantics { selected = item.selected }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIconWithBadge(
            item = item,
            tint = contentColor,
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Matching floating glass rail for expanded tablet / landscape viewports (>= 600dp).
 */
@Composable
fun GlassNavigationRail(
    items: List<GlassNavItem>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current
    val railShape = RoundedCornerShape(30.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(tokens.borderTopLeft, tokens.borderBottomRight)
    )

    Column(
        modifier = modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .navigationBarsPadding()
            .shadow(8.dp, shape = railShape, ambientColor = tokens.shadowColor, spotColor = tokens.shadowColor)
            .clip(railShape)
            .background(tokens.floatingBarFill, shape = railShape)
            .border(BorderStroke(0.75.dp, borderBrush), shape = railShape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        items.forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val itemShape = RoundedCornerShape(22.dp)
            val contentColor = if (item.selected) tokens.onAccentGradient else MaterialTheme.colorScheme.onSurfaceVariant
            val bgBrush = if (item.selected) {
                Brush.linearGradient(colors = tokens.accentGradient)
            } else {
                SolidColor(Color.Transparent)
            }
            Column(
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .heightIn(min = 56.dp)
                    .rebornPressScale(interactionSource = interactionSource)
                    .clip(itemShape)
                    .background(brush = bgBrush, shape = itemShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(),
                        role = Role.Tab,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            item.onClick()
                        },
                    )
                    .semantics { selected = item.selected }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag(item.testTag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NavIconWithBadge(item = item, tint = contentColor)
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NavIconWithBadge(
    item: GlassNavItem,
    tint: Color,
) {
    val icon = if (item.selected) item.selectedIcon else item.unselectedIcon
    if (item.badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Text(item.badgeCount.toString())
                }
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
