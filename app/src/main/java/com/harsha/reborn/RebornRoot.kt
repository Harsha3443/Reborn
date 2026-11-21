package com.harsha.reborn

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.harsha.reborn.feature.about.AboutScreen
import com.harsha.reborn.feature.blocking.AppPickerScreen
import com.harsha.reborn.feature.blocking.HomeScreen
import com.harsha.reborn.feature.blocking.HomeViewModel
import com.harsha.reborn.feature.blocking.RulesScreen
import com.harsha.reborn.feature.friction.FrictionScreen
import com.harsha.reborn.feature.insights.InsightsScreen
import com.harsha.reborn.feature.onboarding.OnboardingScreen
import com.harsha.reborn.feature.onboarding.PermissionHealthScreen
import com.harsha.reborn.feature.onboarding.PrivacyDisclosureScreen
import com.harsha.reborn.feature.smart.FeedBlockingScreen
import com.harsha.reborn.feature.strict.StrictModeScreen
import com.harsha.reborn.ui.components.GlassBottomBar
import com.harsha.reborn.ui.components.GlassNavItem
import com.harsha.reborn.ui.components.GlassNavigationRail
import com.harsha.reborn.ui.components.LiquidGlassBackdrop
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion
import com.harsha.reborn.ui.theme.RebornMotion
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object RulesRoute
@Serializable data object FrictionRoute
@Serializable data object StrictModeRoute
@Serializable data object InsightsRoute
@Serializable data object AppPickerRoute
@Serializable data object PermissionHealthRoute
@Serializable data object AboutRoute
@Serializable data object PrivacyDisclosureRoute
@Serializable data object FeedBlockingRoute

private enum class TopLevelTab(
    val route: Any,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Rounded.Home, Icons.Rounded.Home, "nav_tab_home"),
    RULES(RulesRoute, R.string.nav_rules, Icons.Rounded.Shield, Icons.Rounded.Shield, "nav_tab_rules"),
    APPS(AppPickerRoute, R.string.nav_apps, Icons.Rounded.Apps, Icons.Rounded.Apps, "nav_tab_apps"),
    HEALTH(
        PermissionHealthRoute,
        R.string.nav_health,
        Icons.Rounded.HealthAndSafety,
        Icons.Rounded.HealthAndSafety,
        "nav_tab_health",
    ),
    ABOUT(AboutRoute, R.string.nav_about, Icons.Rounded.Info, Icons.Rounded.Info, "nav_tab_about"),
}

@Composable
fun RebornRoot(
    onboardingCompleted: Boolean,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val reduceMotion = LocalRebornReduceMotion.current

    val showChrome = currentDestination != null &&
        !currentDestination.hasRoute<OnboardingRoute>() &&
        !currentDestination.hasRoute<PrivacyDisclosureRoute>() &&
        !currentDestination.hasRoute<FrictionRoute>() &&
        !currentDestination.hasRoute<StrictModeRoute>() &&
        !currentDestination.hasRoute<InsightsRoute>() &&
        !currentDestination.hasRoute<FeedBlockingRoute>()

    val homeViewModel: HomeViewModel = hiltViewModel()
    val healthSnapshot by homeViewModel.healthSnapshot.collectAsStateWithLifecycle()
    val missingPermissionsCount = healthSnapshot.totalCount - healthSnapshot.grantedCount

    val startDestination: Any = if (onboardingCompleted) HomeRoute else OnboardingRoute

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navItems = TopLevelTab.entries.map { tab ->
        val selected = when (tab) {
            TopLevelTab.HOME -> currentDestination?.hasRoute<HomeRoute>() == true
            TopLevelTab.RULES -> currentDestination?.hasRoute<RulesRoute>() == true
            TopLevelTab.APPS -> currentDestination?.hasRoute<AppPickerRoute>() == true
            TopLevelTab.HEALTH -> currentDestination?.hasRoute<PermissionHealthRoute>() == true
            TopLevelTab.ABOUT -> currentDestination?.hasRoute<AboutRoute>() == true
        }
        GlassNavItem(
            label = stringResource(id = tab.labelResId),
            selectedIcon = tab.selectedIcon,
            unselectedIcon = tab.unselectedIcon,
            selected = selected,
            badgeCount = if (tab == TopLevelTab.HEALTH) missingPermissionsCount else 0,
            testTag = tab.testTag,
            onClick = { navigateToTab(tab.route) },
        )
    }

    LiquidGlassBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (showChrome) {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        if (showChrome && useNavigationRail) {
                            GlassNavigationRail(items = navItems)
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            enterTransition = {
                                if (reduceMotion) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(tween(RebornMotion.NAVIGATION_DURATION_MS)) +
                                        slideInHorizontally(tween(RebornMotion.NAVIGATION_DURATION_MS)) { it / 16 }
                                }
                            },
                            exitTransition = {
                                if (reduceMotion) ExitTransition.None else fadeOut(tween(180))
                            },
                            popEnterTransition = {
                                if (reduceMotion) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(tween(RebornMotion.NAVIGATION_DURATION_MS)) +
                                        slideInHorizontally(tween(RebornMotion.NAVIGATION_DURATION_MS)) { -it / 16 }
                                }
                            },
                            popExitTransition = {
                                if (reduceMotion) ExitTransition.None else fadeOut(tween(180))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .then(
                                    if (showChrome && !useNavigationRail) {
                                        Modifier.padding(bottom = 0.dp)
                                    } else {
                                        Modifier
                                    }
                                ),
                        ) {
                            composable<OnboardingRoute> {
                                OnboardingScreen(
                                    onFinished = {
                                        navController.navigate(HomeRoute) {
                                            popUpTo<OnboardingRoute> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenPrivacyDisclosure = {
                                        navController.navigate(PrivacyDisclosureRoute)
                                    },
                                )
                            }

                            composable<HomeRoute> {
                                HomeScreen(
                                    onOpenPermissionHealth = { navigateToTab(PermissionHealthRoute) },
                                    onOpenAppPicker = { navigateToTab(AppPickerRoute) },
                                    onOpenRules = { navigateToTab(RulesRoute) },
                                    onOpenPrivacyDisclosure = {
                                        navController.navigate(PrivacyDisclosureRoute)
                                    },
                                    onOpenFriction = {
                                        navController.navigate(FrictionRoute)
                                    },
                                    onOpenStrictMode = {
                                        navController.navigate(StrictModeRoute)
                                    },
                                    onOpenInsights = {
                                        navController.navigate(InsightsRoute)
                                    },
                                    onOpenFeedBlocking = {
                                        navController.navigate(FeedBlockingRoute)
                                    },
                                )
                            }

                            composable<RulesRoute> {
                                RulesScreen(
                                    onOpenFriction = {
                                        navController.navigate(FrictionRoute)
                                    },
                                    onOpenStrictMode = {
                                        navController.navigate(StrictModeRoute)
                                    },
                                    onOpenFeedBlocking = {
                                        navController.navigate(FeedBlockingRoute)
                                    },
                                )
                            }

                            composable<FrictionRoute> {
                                FrictionScreen(
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable<StrictModeRoute> {
                                StrictModeScreen(
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable<InsightsRoute> {
                                InsightsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenPermissionHealth = { navigateToTab(PermissionHealthRoute) },
                                )
                            }

                            composable<AppPickerRoute> {
                                AppPickerScreen()
                            }

                            composable<PermissionHealthRoute> {
                                PermissionHealthScreen(
                                    onOpenPrivacyDisclosure = {
                                        navController.navigate(PrivacyDisclosureRoute)
                                    },
                                )
                            }

                            composable<AboutRoute> {
                                AboutScreen(
                                    onOpenPrivacyDisclosure = {
                                        navController.navigate(PrivacyDisclosureRoute)
                                    },
                                    onReplayOnboarding = {
                                        navController.navigate(OnboardingRoute)
                                    },
                                    onOpenFeedBlocking = {
                                        navController.navigate(FeedBlockingRoute)
                                    },
                                )
                            }

                            composable<PrivacyDisclosureRoute> {
                                PrivacyDisclosureScreen(
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable<FeedBlockingRoute> {
                                FeedBlockingScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenPermissionHealth = { navigateToTab(PermissionHealthRoute) },
                                )
                            }
                        }
                    }

                    if (showChrome && !useNavigationRail) {
                        GlassBottomBar(
                            items = navItems,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}
