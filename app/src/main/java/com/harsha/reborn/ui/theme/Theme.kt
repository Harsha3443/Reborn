package com.harsha.reborn.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.harsha.reborn.core.data.model.ThemeMode

@Immutable
data class RebornGlassTokens(
    val isDark: Boolean,
    val isAmoled: Boolean,
    val supportsRealtimeBlur: Boolean,
    val backdropTop: Color,
    val backdropBottom: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val orbAlphaScale: Float,
    val cardFill: Color,
    val cardElevatedFill: Color,
    val cardSubtleFill: Color,
    val borderTopLeft: Color,
    val borderBottomRight: Color,
    val innerHighlight: Color,
    val selectedBorderTopLeft: Color,
    val selectedBorderBottomRight: Color,
    val floatingBarFill: Color,
    val modalSheetFill: Color,
    val modalScrim: Color,
    val shadowColor: Color,
    val buttonShadowStart: Color,
    val buttonShadowEnd: Color,
    val accentGradient: List<Color>,
    val onAccentGradient: Color,
    val accentText: Color,
    val accentTintFill: Color,
    val accentBorder: Color,
    val statusHealthy: Color,
    val statusWarning: Color,
    val statusCritical: Color,
)

private val DarkGlassTokens = RebornGlassTokens(
    isDark = true,
    isAmoled = false,
    supportsRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    backdropTop = BurgundyBgTop,
    backdropBottom = BurgundyBgBottom,
    orbPrimary = OrbBurgundyLight,
    orbSecondary = OrbBurgundyPrimary,
    orbTertiary = OrbBurgundyDeep,
    orbAlphaScale = 1f,
    cardFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlassWhite8 else GlassWhite10,
    cardElevatedFill = GlassWhite10,
    cardSubtleFill = GlassWhite6,
    borderTopLeft = GlassBorderTopLeft,
    borderBottomRight = GlassBorderBottomRight,
    innerHighlight = GlassInnerHighlight,
    selectedBorderTopLeft = GlassSelectedBorderTopLeft,
    selectedBorderBottomRight = GlassSelectedBorderBottomRight,
    floatingBarFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BurgundyBarFill.copy(alpha = 0.86f)
    } else {
        BurgundyBarFill.copy(alpha = 0.94f)
    },
    modalSheetFill = BurgundySheetFill.copy(alpha = 0.96f),
    modalScrim = GlassModalScrim,
    shadowColor = GlassBarShadow,
    buttonShadowStart = GlassButtonShadowStart,
    buttonShadowEnd = GlassButtonShadowEnd,
    accentGradient = BurgundyGradientColors,
    onAccentGradient = OnBurgundyGradient,
    accentText = BurgundyAccentTextDark,
    accentTintFill = BurgundyPrimary.copy(alpha = 0.18f),
    accentBorder = BurgundyLight.copy(alpha = 0.45f),
    statusHealthy = StatusHealthy,
    statusWarning = StatusWarning,
    statusCritical = StatusCritical,
)

private val AmoledGlassTokens = DarkGlassTokens.copy(
    isAmoled = true,
    backdropTop = AmoledBlack,
    backdropBottom = AmoledBurgundyBottom,
    orbAlphaScale = 0.55f,
    cardFill = GlassWhite7,
    cardElevatedFill = GlassWhite10,
    cardSubtleFill = GlassWhite6,
    floatingBarFill = AmoledBarFill.copy(alpha = 0.92f),
    modalSheetFill = AmoledSheetFill.copy(alpha = 0.96f),
)

private val LightGlassTokens = RebornGlassTokens(
    isDark = false,
    isAmoled = false,
    supportsRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    backdropTop = RoseAlabasterBg,
    backdropBottom = RoseAlabasterTint,
    orbPrimary = OrbBurgundyLight,
    orbSecondary = OrbBurgundyPrimary,
    orbTertiary = OrbBurgundyDeep,
    orbAlphaScale = 0.28f,
    cardFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LightGlassFill else LightGlassElevatedFill,
    cardElevatedFill = LightGlassElevatedFill,
    cardSubtleFill = InkBurgundy.copy(alpha = 0.06f),
    borderTopLeft = LightGlassBorderTopLeft,
    borderBottomRight = InkBurgundy.copy(alpha = 0.08f),
    innerHighlight = LightGlassInnerHighlight,
    selectedBorderTopLeft = GlassSelectedBorderTopLeft,
    selectedBorderBottomRight = GlassSelectedBorderBottomRight,
    floatingBarFill = LightGlassBarFill,
    modalSheetFill = LightSheetFill.copy(alpha = 0.96f),
    modalScrim = GlassModalScrim,
    shadowColor = InkBurgundy.copy(alpha = 0.08f),
    buttonShadowStart = GlassButtonShadowStart,
    buttonShadowEnd = GlassButtonShadowEnd,
    accentGradient = BurgundyGradientColors,
    onAccentGradient = OnBurgundyGradient,
    accentText = BurgundyPrimary,
    accentTintFill = BurgundyPrimary.copy(alpha = 0.10f),
    accentBorder = BurgundyPrimary.copy(alpha = 0.35f),
    statusHealthy = StatusHealthyDark,
    statusWarning = StatusWarningDark,
    statusCritical = StatusCriticalDark,
)

val LocalRebornGlassTokens = staticCompositionLocalOf { DarkGlassTokens }

private val RebornDarkColorScheme = darkColorScheme(
    primary = BurgundyLight,
    onPrimary = TextIvory,
    primaryContainer = BurgundyPrimary,
    onPrimaryContainer = BurgundySoft,
    secondary = BurgundyPrimary,
    onSecondary = TextIvory,
    secondaryContainer = BurgundyDark,
    onSecondaryContainer = TextIvory,
    tertiary = BurgundyRose,
    onTertiary = BurgundyBgTop,
    background = BurgundyBgTop,
    onBackground = TextIvory,
    surface = BurgundyDeepSurface,
    onSurface = TextIvory,
    surfaceVariant = BurgundySurface,
    onSurfaceVariant = TextMauveMist,
    surfaceContainerLowest = BurgundyBgTop,
    surfaceContainerLow = BurgundyDeepSurface,
    surfaceContainer = BurgundySurface,
    surfaceContainerHigh = BurgundySurfaceElevated,
    surfaceContainerHighest = BurgundySurfaceHighest,
    outline = BurgundyBorder,
    outlineVariant = BurgundyBorderSubtle,
    error = StatusCritical,
    onError = RoseAlabasterSurface,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

private val RebornAmoledColorScheme = RebornDarkColorScheme.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = AmoledBurgundySurface,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledBurgundyLow,
    surfaceContainer = AmoledBurgundySurface,
    surfaceContainerHigh = AmoledBurgundyElevated,
    surfaceContainerHighest = AmoledBurgundyHighest,
    outlineVariant = AmoledBurgundyBorder,
)

private val RebornLightColorScheme = lightColorScheme(
    primary = BurgundyPrimary,
    onPrimary = TextIvory,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = BurgundyDeepest,
    secondary = BurgundyLight,
    onSecondary = TextIvory,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = BurgundyDeepest,
    tertiary = BurgundyDark,
    onTertiary = TextIvory,
    background = RoseAlabasterBg,
    onBackground = InkBurgundy,
    surface = RoseAlabasterSurface,
    onSurface = InkBurgundy,
    surfaceVariant = RoseAlabasterContainer,
    onSurfaceVariant = InkMutedBurgundy,
    surfaceContainerLowest = RoseAlabasterSurface,
    surfaceContainerLow = RoseAlabasterBg,
    surfaceContainer = RoseAlabasterContainer,
    surfaceContainerHigh = RoseAlabasterContainerHigh,
    surfaceContainerHighest = RoseAlabasterContainerHighest,
    outline = RoseAlabasterBorder,
    outlineVariant = RoseAlabasterBorderSubtle,
    error = StatusCriticalDark,
    onError = RoseAlabasterSurface,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

@Composable
fun RebornTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val useAmoled = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (useAmoled) {
                base.copy(
                    background = AmoledBlack,
                    surface = AmoledBlack,
                    surfaceContainerLowest = AmoledBlack,
                    surfaceContainerLow = AmoledBurgundyLow,
                    surfaceContainer = AmoledBurgundySurface,
                )
            } else {
                base
            }
        }
        useAmoled -> RebornAmoledColorScheme
        useDarkTheme -> RebornDarkColorScheme
        else -> RebornLightColorScheme
    }

    val baseGlassTokens = when {
        useAmoled -> AmoledGlassTokens
        useDarkTheme -> DarkGlassTokens
        else -> LightGlassTokens
    }
    val glassTokens = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseGlassTokens.copy(
            backdropTop = if (useAmoled) AmoledBlack else colorScheme.background,
            backdropBottom = if (useAmoled) AmoledBurgundyBottom else colorScheme.surfaceContainerLow,
        )
    } else {
        baseGlassTokens
    }

    val reduceMotion = rememberReduceMotionEnabled()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(
        LocalRebornGlassTokens provides glassTokens,
        LocalRebornReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RebornTypography,
            shapes = RebornShapes,
            content = content,
        )
    }
}
