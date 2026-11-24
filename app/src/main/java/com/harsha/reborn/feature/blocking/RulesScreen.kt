package com.harsha.reborn.feature.blocking

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.DelayedDisableTargetType
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.PendingDisableRequest
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.engine.RuleEvaluator
import com.harsha.reborn.feature.smart.SmartRulesSection
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassSliderColors
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.rebornPressScale
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RulesScreen(
    onOpenFriction: () -> Unit = {},
    onOpenStrictMode: () -> Unit = {},
    onOpenFeedBlocking: () -> Unit = {},
    onOpenAppPicker: () -> Unit = {},
    selectedAppsCount: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LocalRebornGlassTokens.current
    val view = LocalView.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.feedbackMessage) {
        if (state.feedbackMessage != null) {
            delay(4_000L)
            viewModel.clearFeedback()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Blocking Rules",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    GlassButton(
                        onClick = onOpenAppPicker,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("rules_open_apps_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "Selected Apps",
                            tint = tokens.accentText,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedAppsCount > 0) "Apps ($selectedAppsCount)" else "Apps")
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                // Calm segmented pill strip
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(RulesTab.entries, key = { it.name }) { tab ->
                        val isSelected = selectedTab == tab
                        val interactionSource = remember { MutableInteractionSource() }
                        val pillShape = RoundedCornerShape(22.dp)
                        val bgBrush = if (isSelected) {
                            Brush.linearGradient(tokens.accentGradient)
                        } else {
                            SolidColor(tokens.cardSubtleFill)
                        }
                        val labelColor = if (isSelected) {
                            tokens.onAccentGradient
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        val border = if (isSelected) {
                            BorderStroke(0.75.dp, tokens.selectedBorderTopLeft)
                        } else {
                            BorderStroke(0.75.dp, tokens.borderTopLeft)
                        }

                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .rebornPressScale(interactionSource)
                                .clip(pillShape)
                                .background(brush = bgBrush, shape = pillShape)
                                .border(border, shape = pillShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.material3.ripple(),
                                    role = Role.Tab,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        viewModel.selectTab(tab)
                                    },
                                )
                                .semantics { selected = isSelected }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("rules_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            state.feedbackMessage?.let { message ->
                GlassCard(
                    cornerRadius = 20.dp,
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("rules_feedback_banner"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = viewModel::clearFeedback,
                            colors = glassTextButtonColors(),
                        ) {
                            Text("OK")
                        }
                    }
                }
            }

            if (state.isStrictSettingsLocked) {
                GlassCard(
                    cornerRadius = 20.dp,
                    tintColor = tokens.accentTintFill,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("rules_strict_locked_banner"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = tokens.accentText,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Strict Mode locked — active rules cannot be weakened.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = onOpenStrictMode,
                            colors = glassTextButtonColors(),
                        ) {
                            Text("View")
                        }
                    }
                }
            }

            if (state.pendingDisables.isNotEmpty()) {
                PendingDisablesQueueBanner(
                    pending = state.pendingDisables,
                    nowEpochMs = state.nowEpochMs,
                    onCancel = viewModel::cancelPendingDisable,
                )
            }

            when (selectedTab) {
                RulesTab.QUICK_BLOCK -> QuickBlockSection(
                    state = state,
                    onStart = viewModel::startQuickBlock,
                    onStop = viewModel::stopQuickBlock,
                    onOpenFriction = onOpenFriction,
                )
                RulesTab.SCHEDULES -> SchedulesSection(
                    state = state,
                    onSave = viewModel::saveSchedule,
                    onToggle = viewModel::toggleSchedule,
                    onDelete = viewModel::deleteSchedule,
                )
                RulesTab.LIMITS -> LimitsSection(
                    state = state,
                    onSaveDailyLimit = viewModel::saveDailyTimeLimit,
                    onToggleDailyLimit = viewModel::toggleDailyTimeLimit,
                    onDeleteDailyLimit = viewModel::deleteDailyTimeLimit,
                    onSaveLaunchLimit = viewModel::saveLaunchLimit,
                    onToggleLaunchLimit = viewModel::toggleLaunchLimit,
                    onDeleteLaunchLimit = viewModel::deleteLaunchLimit,
                )
                RulesTab.ALLOWLIST -> AllowlistSection(
                    state = state,
                    onSetEnabled = viewModel::setAllowlistEnabled,
                    onToggleApp = viewModel::toggleAllowlistPackage,
                )
                RulesTab.SMART -> SmartRulesSection(
                    state = state,
                    onSaveConditionalRule = viewModel::saveConditionalRule,
                    onToggleConditionalRule = viewModel::toggleConditionalRule,
                    onDeleteConditionalRule = viewModel::deleteConditionalRule,
                    onSetFeedBlockingEnabled = viewModel::setInAppFeedBlockingEnabled,
                    onToggleFeedProfile = viewModel::toggleFeedProfile,
                    onSaveFeedSelectorsJson = viewModel::saveCustomFeedSelectorsJson,
                    onSetBrowserBlockingEnabled = viewModel::setBrowserBlockingEnabled,
                    onAddBrowserKeywordRule = viewModel::addBrowserKeywordRule,
                    onToggleBrowserKeywordRule = viewModel::toggleBrowserKeywordRule,
                    onDeleteBrowserKeywordRule = viewModel::deleteBrowserKeywordRule,
                    onSetGrayscaleDimEnabled = viewModel::setGrayscaleDimOnLimitEnabled,
                    onOpenOverlaySettings = viewModel::openOverlayPermissionSettings,
                    onSampleWifiSsid = viewModel::sampleCurrentWifiSsid,
                    onSampleCoordinates = viewModel::sampleCurrentCoordinates,
                    onOpenFeedBlocking = onOpenFeedBlocking,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickBlockSection(
    state: RulesUiState,
    onStart: (Set<String>, Int) -> Unit,
    onStop: () -> Unit,
    onOpenFriction: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val quick = state.snapshot.quickBlock
    val isActiveNow = quick != null && quick.isActive && state.nowEpochMs < quick.endTimeEpochMs
    var selectedPackages by remember(state.defaultSelectedPackages) {
        mutableStateOf(
            quick?.packageNames?.takeIf { it.isNotEmpty() } ?: state.defaultSelectedPackages
        )
    }
    var durationMinutes by remember { mutableIntStateOf(30) }
    var showAppSelector by remember { mutableStateOf(false) }

    if (showAppSelector) {
        MultiAppSelectorDialog(
            title = "Apps for Quick Block",
            installedApps = state.installedApps,
            initialSelected = selectedPackages,
            onDismiss = { showAppSelector = false },
            onConfirm = {
                selectedPackages = it
                showAppSelector = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (isActiveNow && quick != null) {
            item {
                val remainingSec = ((quick.endTimeEpochMs - state.nowEpochMs) / 1000L).coerceAtLeast(0L)
                val mins = remainingSec / 60L
                val secs = remainingSec % 60L
                val totalMs = (quick.endTimeEpochMs - quick.startTimeEpochMs).coerceAtLeast(60_000L)
                val remProgress = ((quick.endTimeEpochMs - state.nowEpochMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                GlassCard(
                    cornerRadius = 28.dp,
                    elevated = true,
                    tintColor = tokens.accentTintFill,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.testTag("quick_block_active_card"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PhoenixProgressRing(
                                progress = remProgress,
                                size = 60.dp,
                                strokeWidth = 5.5.dp,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Quick Block Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.accentText,
                                )
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d left", mins, secs),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Text(
                            text = "Blocking ${quick.packageNames.size} selected app${if (quick.packageNames.size == 1) "" else "s"}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val pendingQuick = state.pendingDisables.firstOrNull {
                            it.targetType == DelayedDisableTargetType.QUICK_BLOCK
                        }
                        if (pendingQuick != null) {
                            val rem = ((pendingQuick.executeAfterEpochMs - state.nowEpochMs) / 1000L).coerceAtLeast(0L)
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Scheduled to turn off in %02d:%02d",
                                    rem / 60L,
                                    rem % 60L,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.accentText,
                            )
                        }

                        GlassButton(
                            onClick = onStop,
                            enabled = !state.isStrictSettingsLocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_block_stop_button"),
                        ) {
                            Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when {
                                    state.isStrictSettingsLocked -> "Locked by Strict Mode"
                                    pendingQuick != null -> "Cool-down in Progress"
                                    state.frictionSettings.delayedDisableEnabled ->
                                        "Stop Quick Block (${state.frictionSettings.delayedDisableMinutes}m wait)"
                                    else -> "Stop Quick Block"
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard(
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Focus Burst",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$durationMinutes min",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.accentText,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60, 120).forEach { preset ->
                            val isSelected = durationMinutes == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = { durationMinutes = preset },
                                colors = glassFilterChipColors(),
                                border = glassFilterChipBorder(isSelected),
                                label = {
                                    Text(if (preset >= 60) "${preset / 60}h" else "${preset}m")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("quick_duration_$preset"),
                            )
                        }
                    }

                    Slider(
                        value = durationMinutes.toFloat(),
                        onValueChange = { durationMinutes = (it / 5f).roundToInt().coerceAtLeast(1) * 5 },
                        valueRange = 5f..180f,
                        colors = glassSliderColors(),
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAppSelector = true }
                            .padding(vertical = 8.dp)
                            .testTag("quick_block_choose_apps_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apps to block",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "${selectedPackages.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Choose apps to block",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    GlassPrimaryButton(
                        onClick = { onStart(selectedPackages, durationMinutes) },
                        enabled = selectedPackages.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("quick_block_start_button"),
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Quick Block ($durationMinutes min)")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenFriction)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag("rules_open_friction_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Mindful Friction (${state.frictionSettings.pauseSeconds}s pause)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open Mindful Friction",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SchedulesSection(
    state: RulesUiState,
    onSave: (ScheduleProfile) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editingProfile by remember { mutableStateOf<ScheduleProfile?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    if (isCreating || editingProfile != null) {
        ScheduleEditorDialog(
            initial = editingProfile,
            installedApps = state.installedApps,
            defaultPackages = state.defaultSelectedPackages,
            onDismiss = {
                isCreating = false
                editingProfile = null
            },
            onSave = { saved ->
                onSave(saved)
                isCreating = false
                editingProfile = null
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            GlassPrimaryButton(
                onClick = { isCreating = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_schedule_button"),
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Schedule Profile")
            }
        }

        if (state.snapshot.schedules.isEmpty()) {
            item {
                GlassCard(cornerRadius = 26.dp, contentPadding = PaddingValues(18.dp)) {
                    Text(
                        text = "Create recurring time blocks like Work Hours or Bedtime to shield distracting apps automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                GlassCard(
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        state.snapshot.schedules.forEachIndexed { idx, profile ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = tokens.borderBottomRight,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .testTag("schedule_card_${profile.id}"),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            tint = tokens.accentText,
                                        )
                                        Column {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${RuleEvaluator.formatMinute(profile.startMinuteOfDay)} – ${RuleEvaluator.formatMinute(profile.endMinuteOfDay)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = profile.isEnabled,
                                        colors = glassSwitchColors(),
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onToggle(profile.id, it)
                                        },
                                        modifier = Modifier.testTag("schedule_switch_${profile.id}"),
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${formatDaysSummary(profile.daysOfWeek)} • ${profile.packageNames.size} apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (profile.packageNames.isEmpty()) {
                                            tokens.statusWarning
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { editingProfile = profile },
                                            colors = glassTextButtonColors(),
                                            modifier = Modifier.testTag("schedule_edit_${profile.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        IconButton(
                                            onClick = { onDelete(profile.id) },
                                            modifier = Modifier.testTag("schedule_delete_${profile.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete schedule",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitsSection(
    state: RulesUiState,
    onSaveDailyLimit: (DailyTimeLimitRule) -> Unit,
    onToggleDailyLimit: (Long, Boolean) -> Unit,
    onDeleteDailyLimit: (Long) -> Unit,
    onSaveLaunchLimit: (LaunchLimitRule) -> Unit,
    onToggleLaunchLimit: (Long, Boolean) -> Unit,
    onDeleteLaunchLimit: (Long) -> Unit,
) {
    var showAddDailyLimit by remember { mutableStateOf(false) }
    var showAddLaunchLimit by remember { mutableStateOf(false) }
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    if (showAddDailyLimit) {
        DailyLimitEditorDialog(
            installedApps = state.installedApps,
            defaultPackages = state.defaultSelectedPackages,
            onDismiss = { showAddDailyLimit = false },
            onSave = {
                onSaveDailyLimit(it)
                showAddDailyLimit = false
            },
        )
    }

    if (showAddLaunchLimit) {
        LaunchLimitEditorDialog(
            installedApps = state.installedApps,
            onDismiss = { showAddLaunchLimit = false },
            onSave = {
                onSaveLaunchLimit(it)
                showAddLaunchLimit = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            GlassCard(
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Daily Time Limits", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { showAddDailyLimit = true },
                            colors = glassTextButtonColors(),
                            modifier = Modifier.testTag("add_daily_limit_button"),
                        ) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (state.snapshot.dailyTimeLimits.isEmpty()) {
                        Text(
                            text = "Cap total minutes per day for one app or an entire group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.snapshot.dailyTimeLimits.forEachIndexed { idx, rule ->
                            if (idx > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                            }
                            val usedMin = rule.packageNames.sumOf { pkg ->
                                state.usageSnapshot.dailyMinutesByPackage[pkg] ?: 0
                            }
                            val progress = (usedMin.toFloat() / rule.dailyLimitMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PhoenixProgressRing(
                                    progress = progress,
                                    size = 44.dp,
                                    strokeWidth = 4.dp,
                                ) {
                                    Text(
                                        text = "${(progress * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    val dimSuffix = if (
                                        state.snapshot.smartRulesConfig.grayscaleDimOnLimitEnabled ||
                                        rule.afterLimitAction == DailyLimitAfterAction.GRAYSCALE_DIM
                                    ) {
                                        " • Grayscale Dim"
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "$usedMin / ${rule.dailyLimitMinutes}m • ${rule.packageNames.size} apps$dimSuffix",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    colors = glassSwitchColors(),
                                    onCheckedChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onToggleDailyLimit(rule.id, it)
                                    },
                                )
                                IconButton(onClick = { onDeleteDailyLimit(rule.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete limit")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Daily Launch Limits", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { showAddLaunchLimit = true },
                            colors = glassTextButtonColors(),
                            modifier = Modifier.testTag("add_launch_limit_button"),
                        ) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (state.snapshot.launchLimits.isEmpty()) {
                        Text(
                            text = "Restrict how many times an app can be opened per day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.snapshot.launchLimits.forEachIndexed { idx, rule ->
                            if (idx > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                            }
                            val opensToday = (state.usageSnapshot.dailyLaunchesByPackage[rule.packageName] ?: 0)
                                .coerceAtMost(rule.maxLaunchesPerDay)
                            val progress = (opensToday.toFloat() / rule.maxLaunchesPerDay.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PhoenixProgressRing(
                                    progress = progress,
                                    size = 44.dp,
                                    strokeWidth = 4.dp,
                                ) {
                                    Text(
                                        text = "$opensToday",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "$opensToday / ${rule.maxLaunchesPerDay} opens today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    colors = glassSwitchColors(),
                                    onCheckedChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        onToggleLaunchLimit(rule.id, it)
                                    },
                                )
                                IconButton(onClick = { onDeleteLaunchLimit(rule.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete launch limit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowlistSection(
    state: RulesUiState,
    onSetEnabled: (Boolean) -> Unit,
    onToggleApp: (InstalledAppInfo, Boolean) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val allowed = state.snapshot.allowlist.allowedPackages
    val filteredApps = remember(state.installedApps, search) {
        val q = search.trim().lowercase()
        state.installedApps
            .filterNot { it.isProtectedSystemApp }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
    }
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GlassCard(
                cornerRadius = 26.dp,
                tintColor = if (state.snapshot.allowlist.isEnabled) tokens.statusHealthy.copy(alpha = 0.12f) else null,
                contentPadding = PaddingValues(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = if (state.snapshot.allowlist.isEnabled) tokens.statusHealthy else tokens.accentText,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allowlist Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Blocks every app except the ${allowed.size} you allow below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.snapshot.allowlist.isEnabled,
                        colors = glassSwitchColors(),
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSetEnabled(it)
                        },
                        modifier = Modifier.testTag("allowlist_mode_switch"),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search apps to allow...") },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = glassOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(filteredApps, key = { it.packageName }) { app ->
            val isAllowed = allowed.contains(app.packageName)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onToggleApp(app, isAllowed) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = app.category.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = isAllowed,
                        onCheckedChange = { onToggleApp(app, isAllowed) },
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
            }
        }

        item {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Phone, Emergency, Settings, and Launcher are always allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PendingDisablesQueueBanner(
    pending: List<PendingDisableRequest>,
    nowEpochMs: Long,
    onCancel: (Long) -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(
        cornerRadius = 22.dp,
        tintColor = tokens.accentTintFill,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("rules_pending_disables_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = tokens.accentText,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Delayed Rule Disables (${pending.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            pending.forEach { item ->
                val remSec = ((item.executeAfterEpochMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.targetLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Disables in %02d:%02d",
                                remSec / 60L,
                                remSec % 60L,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GlassButton(
                        onClick = { onCancel(item.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("rules_cancel_pending_${item.id}"),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun formatDaysSummary(days: Set<Int>): String {
    if (days.size == 7) return "Every day"
    if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == setOf(6, 7)) return "Weekends"
    val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    return days.sorted().mapNotNull { names[it] }.joinToString(", ")
}
