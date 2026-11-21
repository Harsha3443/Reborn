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
    val orbAlphaScale: Float,
    val cardFill: Color,
    val cardElevatedFill: Color,
    val cardSubtleFill: Color,
    val borderTopLeft: Color,
    val borderBottomRight: Color,
    val innerHighlight: Color,
    val floatingBarFill: Color,
    val modalSheetFill: Color,
    val shadowColor: Color,
    val accentGradient: List<Color>,
    val onAccentGradient: Color,
)

private val DarkGlassTokens = RebornGlassTokens(
    isDark = true,
    isAmoled = false,
    supportsRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    backdropTop = NavyMidnight,
    backdropBottom = NavyIndigo,
    orbAlphaScale = 1f,
    cardFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.White.copy(alpha = 0.13f)
    },
    cardElevatedFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color.White.copy(alpha = 0.13f)
    } else {
        Color.White.copy(alpha = 0.17f)
    },
    cardSubtleFill = Color.White.copy(alpha = 0.07f),
    borderTopLeft = Color.White.copy(alpha = 0.30f),
    borderBottomRight = Color.White.copy(alpha = 0.05f),
    innerHighlight = Color.White.copy(alpha = 0.14f),
    floatingBarFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(0xFF141B34).copy(alpha = 0.78f)
    } else {
        Color(0xFF141B34).copy(alpha = 0.92f)
    },
    modalSheetFill = Color(0xFF131A30).copy(alpha = 0.94f),
    shadowColor = Color.Black.copy(alpha = 0.42f),
    accentGradient = PhoenixGradientColors,
    onAccentGradient = OnPhoenixGradient,
)

private val AmoledGlassTokens = DarkGlassTokens.copy(
    isAmoled = true,
    backdropTop = Color.Black,
    backdropBottom = Color(0xFF070A16),
    orbAlphaScale = 0.72f,
    cardFill = Color.White.copy(alpha = 0.08f),
    cardElevatedFill = Color.White.copy(alpha = 0.12f),
    cardSubtleFill = Color.White.copy(alpha = 0.05f),
    floatingBarFill = Color(0xFF0A0D18).copy(alpha = 0.88f),
    modalSheetFill = Color(0xFF0B0E1A).copy(alpha = 0.96f),
)

private val LightGlassTokens = RebornGlassTokens(
    isDark = false,
    isAmoled = false,
    supportsRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    backdropTop = AlabasterBg,
    backdropBottom = AlabasterIndigoTint,
    orbAlphaScale = 0.45f,
    cardFill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color.White.copy(alpha = 0.68f)
    } else {
        Color.White.copy(alpha = 0.84f)
    },
    cardElevatedFill = Color.White.copy(alpha = 0.84f),
    cardSubtleFill = Color.White.copy(alpha = 0.52f),
    borderTopLeft = Color.White.copy(alpha = 0.92f),
    borderBottomRight = InkNavy.copy(alpha = 0.10f),
    innerHighlight = Color.White.copy(alpha = 0.65f),
    floatingBarFill = Color.White.copy(alpha = 0.84f),
    modalSheetFill = Color(0xFFFDFBF7).copy(alpha = 0.96f),
    shadowColor = InkNavy.copy(alpha = 0.12f),
    accentGradient = PhoenixGradientColors,
    onAccentGradient = OnPhoenixGradient,
)

val LocalRebornGlassTokens = staticCompositionLocalOf { DarkGlassTokens }

private val RebornDarkColorScheme = darkColorScheme(
    primary = PhoenixAmber,
    onPrimary = NavyMidnight,
    primaryContainer = Color(0xFF4D1024),
    onPrimaryContainer = PhoenixGoldSoft,
    secondary = PhoenixOrange,
    onSecondary = OnPhoenixGradient,
    secondaryContainer = Color(0xFF2B1A3A),
    onSecondaryContainer = TextStarlight,
    tertiary = OrbViolet,
    onTertiary = Color.White,
    background = NavyMidnight,
    onBackground = TextStarlight,
    surface = NavyDeep,
    onSurface = TextStarlight,
    surfaceVariant = NavySurface,
    onSurfaceVariant = TextMist,
    surfaceContainerLowest = NavyMidnight,
    surfaceContainerLow = NavyDeep,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceElevated,
    surfaceContainerHighest = Color(0xFF26335E),
    outline = NavyBorder,
    outlineVariant = Color(0xFF232E54),
    error = StatusCritical,
    onError = NavyMidnight,
    errorContainer = Color(0xFF531A22),
    onErrorContainer = Color(0xFFFFDAD9),
)

private val RebornAmoledColorScheme = RebornDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0E1B),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF060911),
    surfaceContainer = Color(0xFF0C1222),
    surfaceContainerHigh = Color(0xFF141D34),
    surfaceContainerHighest = Color(0xFF1C2745),
    outlineVariant = Color(0xFF1A223D),
)

private val RebornLightColorScheme = lightColorScheme(
    primary = PhoenixCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E0013),
    secondary = Color(0xFF9E2448),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5EC),
    onSecondaryContainer = Color(0xFF3A0717),
    tertiary = OrbViolet,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = InkNavy,
    surface = AlabasterSurface,
    onSurface = InkNavy,
    surfaceVariant = AlabasterContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = AlabasterContainer,
    surfaceContainerHigh = Color(0xFFE7E5E0),
    surfaceContainerHighest = Color(0xFFDDDCE0),
    outline = AlabasterBorder,
    outlineVariant = Color(0xFFE0E2EC),
    error = StatusCriticalDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
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
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF060912),
                    surfaceContainer = Color(0xFF0D1322),
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
            backdropTop = if (useAmoled) Color.Black else colorScheme.background,
            backdropBottom = if (useAmoled) Color(0xFF070A16) else colorScheme.surfaceContainerLow,
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
