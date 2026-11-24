package com.harsha.reborn.feature.onboarding

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.harsha.reborn.core.engine.PermissionItemStatus
import com.harsha.reborn.core.engine.RebornPermissionType
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.LocalRebornReduceMotion

private const val TOTAL_STEPS = 6 // 0 = Welcome, 1..5 = 5 permissions

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenPrivacyDisclosure: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val step by viewModel.currentStep.collectAsStateWithLifecycle()
    val health by viewModel.healthSnapshot.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LocalRebornGlassTokens.current
    val reduceMotion = LocalRebornReduceMotion.current
    val view = LocalView.current

    // Refresh live permission state whenever user returns from Android system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    BackHandler(enabled = step > 0) {
        viewModel.previousStep()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Top bar with back & skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.previousStep()
                        },
                        modifier = Modifier.testTag("onboarding_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous step",
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                GlassPillBadge {
                    Text(
                        text = if (step == 0) "Welcome" else "Step $step of ${TOTAL_STEPS - 1}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                TextButton(
                    onClick = { viewModel.completeOnboarding(onFinished) },
                    colors = glassTextButtonColors(),
                    modifier = Modifier.testTag("onboarding_skip_button"),
                ) {
                    Text("Skip")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frosted Burgundy gradient progress bar
            val progress = ((step + 1).toFloat() / TOTAL_STEPS.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tokens.cardSubtleFill)
                    .border(1.dp, tokens.borderTopLeft.copy(alpha = 0.25f), RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(tokens.accentGradient)),
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboarding_step_transition",
            ) { currentStep ->
                if (currentStep == 0) {
                    WelcomeStepContent(
                        onOpenPrivacyDisclosure = onOpenPrivacyDisclosure,
                    )
                } else {
                    val permissionItem = health.items.getOrNull(currentStep - 1)
                    if (permissionItem != null) {
                        PermissionStepContent(
                            item = permissionItem,
                            oemName = viewModel.oemGuidance.manufacturerDisplayName,
                            oemSteps = viewModel.oemGuidance.steps,
                            onGrantClick = {
                                if (permissionItem.type == RebornPermissionType.NOTIFICATIONS &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.openPermissionSettings(permissionItem.type)
                                }
                            },
                            onOpenOemSettings = { viewModel.openOemBatterySettings() },
                            onOpenPrivacyDisclosure = onOpenPrivacyDisclosure,
                        )
                    }
                }
            }

            // Bottom primary CTA
            val isLastStep = step == TOTAL_STEPS - 1
            val currentItem = if (step > 0) health.items.getOrNull(step - 1) else null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassPrimaryButton(
                    onClick = {
                        if (isLastStep) {
                            viewModel.completeOnboarding(onFinished)
                        } else {
                            viewModel.nextStep(TOTAL_STEPS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_continue_button"),
                ) {
                    Text(
                        text = when {
                            step == 0 -> "Get Started"
                            isLastStep -> "Finish Setup"
                            currentItem?.isGranted == true -> "Continue"
                            else -> "Continue Anyway"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(
    onOpenPrivacyDisclosure: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhoenixProgressRing(
            progress = 1f,
            size = 136.dp,
            strokeWidth = 5.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                tokens.accentGradient.first().copy(alpha = 0.28f),
                                tokens.accentText.copy(alpha = 0.12f),
                                Color.Transparent,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_reborn_logo),
                    contentDescription = "Reborn Phoenix emblem",
                    modifier = Modifier.size(84.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(id = R.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.accentText,
        )

        Spacer(modifier = Modifier.height(18.dp))

        GlassPillBadge(
            containerColor = tokens.statusHealthy.copy(alpha = 0.14f),
            borderColor = tokens.statusHealthy.copy(alpha = 0.45f),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = tokens.statusHealthy,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(id = R.string.offline_promise_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            cornerRadius = 28.dp,
            elevated = true,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Why we ask for permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Over the next 5 quick steps, Reborn explains every permission plainly. Everything stays strictly on your phone—no accounts, no cloud, and no internet access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onOpenPrivacyDisclosure,
                    colors = glassTextButtonColors(),
                    modifier = Modifier.testTag("onboarding_privacy_link"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Read Accessibility & Privacy Promise")
                }
            }
        }
    }
}

@Composable
private fun PermissionStepContent(
    item: PermissionItemStatus,
    oemName: String,
    oemSteps: List<String>,
    onGrantClick: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onOpenPrivacyDisclosure: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhoenixProgressRing(
            progress = if (item.isGranted) 1f else 0.35f,
            size = 94.dp,
            strokeWidth = 6.dp,
            sweepColors = if (item.isGranted) {
                listOf(tokens.statusHealthy, tokens.statusHealthy)
            } else {
                tokens.accentGradient
            },
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isGranted) {
                            tokens.statusHealthy.copy(alpha = 0.16f)
                        } else {
                            tokens.accentTintFill
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = permissionIcon(item.type),
                    contentDescription = null,
                    tint = if (item.isGranted) tokens.statusHealthy else tokens.accentText,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassPillBadge(
            containerColor = if (item.isGranted) tokens.statusHealthy.copy(alpha = 0.16f) else null,
            borderColor = if (item.isGranted) tokens.statusHealthy.copy(alpha = 0.5f) else null,
        ) {
            if (item.isGranted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = tokens.statusHealthy,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = if (item.isGranted) {
                    "Granted"
                } else if (item.isCritical) {
                    "Required for blocking"
                } else {
                    "Recommended for reliability"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isGranted) tokens.statusHealthy else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(
            cornerRadius = 26.dp,
            elevated = true,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Why Reborn needs this",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.accentText,
                )
                Text(
                    text = item.whyNeeded,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (!item.isGranted) {
                    GlassPrimaryButton(
                        onClick = onGrantClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permission_${item.type.name.lowercase()}"),
                    ) {
                        Text("Grant ${item.title}")
                    }
                } else {
                    GlassButton(
                        onClick = onGrantClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_permission_${item.type.name.lowercase()}"),
                    ) {
                        Text("Review in System Settings")
                    }
                }

                if (item.type == RebornPermissionType.ACCESSIBILITY) {
                    TextButton(
                        onClick = onOpenPrivacyDisclosure,
                        colors = glassTextButtonColors(),
                        modifier = Modifier.testTag("accessibility_disclosure_button"),
                    ) {
                        Text("How Accessibility data is handled")
                    }
                }
            }
        }

        if (item.type == RebornPermissionType.BATTERY_OPTIMIZATION) {
            Spacer(modifier = Modifier.height(14.dp))
            GlassCard(
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$oemName Tip",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.accentText,
                    )
                    oemSteps.forEach { step ->
                        Text(
                            text = "• $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onOpenOemSettings,
                        colors = glassTextButtonColors(),
                        modifier = Modifier.testTag("onboarding_oem_battery_button"),
                    ) {
                        Text("Open Manufacturer Battery Settings")
                    }
                }
            }
        }
    }
}

fun permissionIcon(type: RebornPermissionType): ImageVector = when (type) {
    RebornPermissionType.ACCESSIBILITY -> Icons.Rounded.AccessibilityNew
    RebornPermissionType.USAGE_ACCESS -> Icons.Rounded.QueryStats
    RebornPermissionType.OVERLAY -> Icons.Rounded.Layers
    RebornPermissionType.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    RebornPermissionType.BATTERY_OPTIMIZATION -> Icons.Rounded.BatteryChargingFull
}
