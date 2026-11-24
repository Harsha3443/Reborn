package com.harsha.reborn.feature.blocking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.model.DailyLimitAfterAction
import com.harsha.reborn.core.data.model.DailyTimeLimitRule
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.data.model.LaunchLimitRule
import com.harsha.reborn.core.data.model.ScheduleProfile
import com.harsha.reborn.core.engine.RuleEvaluator
import com.harsha.reborn.ui.components.GlassAlertDialog
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassSliderColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import kotlin.math.roundToInt

private val DAY_LABELS = listOf(
    1 to "M",
    2 to "T",
    3 to "W",
    4 to "T",
    5 to "F",
    6 to "S",
    7 to "S",
)

@Composable
fun MultiAppSelectorDialog(
    title: String,
    installedApps: List<InstalledAppInfo>,
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelected) }
    var search by remember { mutableStateOf("") }
    val selectable = remember(installedApps, search) {
        val q = search.trim().lowercase()
        installedApps
            .filterNot { it.isProtectedSystemApp }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(selectable, key = { it.packageName }) { app ->
                        val checked = selected.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    selected = if (checked) selected - app.packageName else selected + app.packageName
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (checked) selected - app.packageName else selected + app.packageName
                                },
                            )
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = { onConfirm(selected) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("dialog_confirm_apps"),
            ) {
                Text("Done (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = glassTextButtonColors()) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorDialog(
    initial: ScheduleProfile?,
    installedApps: List<InstalledAppInfo>,
    defaultPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (ScheduleProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "Focus") }
    var days by remember { mutableStateOf(initial?.daysOfWeek ?: setOf(1, 2, 3, 4, 5)) }
    var startMin by remember { mutableIntStateOf(initial?.startMinuteOfDay ?: 9 * 60) }
    var endMin by remember { mutableIntStateOf(initial?.endMinuteOfDay ?: 17 * 60) }
    var packages by remember {
        mutableStateOf(
            initial?.packageNames?.takeIf { it.isNotEmpty() } ?: defaultPackages
        )
    }
    var showAppPicker by remember { mutableStateOf(false) }
    val tokens = LocalRebornGlassTokens.current

    if (showAppPicker) {
        MultiAppSelectorDialog(
            title = "Apps in ${name.ifBlank { "Schedule" }}",
            installedApps = installedApps,
            initialSelected = packages,
            onDismiss = { showAppPicker = false },
            onConfirm = {
                packages = it
                showAppPicker = false
            },
        )
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Schedule" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_name_input"),
                )

                Text("Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DAY_LABELS.forEach { (dayInt, label) ->
                        val isSelected = days.contains(dayInt)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                days = if (isSelected) days - dayInt else days + dayInt
                            },
                            colors = glassFilterChipColors(),
                            border = glassFilterChipBorder(isSelected),
                            label = { Text(label) },
                        )
                    }
                }

                TimeStepperRow(
                    label = "Start",
                    minuteOfDay = startMin,
                    onChange = { startMin = it },
                )
                TimeStepperRow(
                    label = "End",
                    minuteOfDay = endMin,
                    onChange = { endMin = it },
                )
                if (endMin < startMin) {
                    Text(
                        text = "Overnight schedule (crosses midnight into the next morning).",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.accentText,
                    )
                }

                TextButton(
                    onClick = { showAppPicker = true },
                    colors = glassTextButtonColors(),
                    modifier = Modifier.testTag("schedule_pick_apps_button"),
                ) {
                    Text("Select Apps (${packages.size} chosen)")
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    onSave(
                        ScheduleProfile(
                            id = initial?.id ?: 0L,
                            name = name.trim().ifEmpty { "Focus Schedule" },
                            daysOfWeek = days,
                            startMinuteOfDay = startMin,
                            endMinuteOfDay = endMin,
                            packageNames = packages,
                            isEnabled = initial?.isEnabled ?: true,
                        )
                    )
                },
                enabled = days.isNotEmpty() && packages.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.testTag("schedule_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = glassTextButtonColors()) { Text("Cancel") }
        },
    )
}

@Composable
private fun TimeStepperRow(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ${RuleEvaluator.formatMinute(minuteOfDay)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { onChange((minuteOfDay - 30 + 1440) % 1440) },
                colors = glassTextButtonColors(),
            ) { Text("-30m") }
            TextButton(
                onClick = { onChange((minuteOfDay + 30) % 1440) },
                colors = glassTextButtonColors(),
            ) { Text("+30m") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyLimitEditorDialog(
    installedApps: List<InstalledAppInfo>,
    defaultPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (DailyTimeLimitRule) -> Unit,
) {
    var name by remember { mutableStateOf("Social Group") }
    var minutes by remember { mutableIntStateOf(30) }
    var afterAction by remember {
        mutableStateOf(DailyLimitAfterAction.BLOCK_APP)
    }
    var packages by remember { mutableStateOf(defaultPackages) }
    var showAppPicker by remember { mutableStateOf(false) }
    val tokens = LocalRebornGlassTokens.current

    if (showAppPicker) {
        MultiAppSelectorDialog(
            title = "Apps in Daily Limit",
            installedApps = installedApps,
            initialSelected = packages,
            onDismiss = { showAppPicker = false },
            onConfirm = {
                packages = it
                showAppPicker = false
            },
        )
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Daily Time Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("App or Group Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Limit: $minutes min / day",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.accentText,
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = (it / 5f).roundToInt() * 5 },
                    valueRange = 5f..240f,
                    colors = glassSliderColors(),
                )
                Text(
                    text = "When limit is reached:",
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isBlock = afterAction == DailyLimitAfterAction.BLOCK_APP
                    FilterChip(
                        selected = isBlock,
                        onClick = { afterAction = DailyLimitAfterAction.BLOCK_APP },
                        colors = glassFilterChipColors(),
                        border = glassFilterChipBorder(isBlock),
                        label = { Text("Block Screen") },
                        modifier = Modifier.testTag("daily_limit_action_block"),
                    )
                    FilterChip(
                        selected = !isBlock,
                        onClick = { afterAction = DailyLimitAfterAction.GRAYSCALE_DIM },
                        colors = glassFilterChipColors(),
                        border = glassFilterChipBorder(!isBlock),
                        label = { Text("Grayscale Dim") },
                        modifier = Modifier.testTag("daily_limit_action_grayscale"),
                    )
                }
                TextButton(
                    onClick = { showAppPicker = true },
                    colors = glassTextButtonColors(),
                    modifier = Modifier.testTag("daily_limit_pick_apps"),
                ) {
                    Text("Choose Apps (${packages.size} selected)")
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    onSave(
                        DailyTimeLimitRule(
                            name = name.trim().ifEmpty { "Daily Limit" },
                            packageNames = packages,
                            dailyLimitMinutes = minutes,
                            afterLimitAction = afterAction,
                            isEnabled = true,
                        )
                    )
                },
                enabled = packages.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.testTag("daily_limit_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = glassTextButtonColors()) { Text("Cancel") }
        },
    )
}

@Composable
fun LaunchLimitEditorDialog(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (LaunchLimitRule) -> Unit,
) {
    val selectable = remember(installedApps) { installedApps.filterNot { it.isProtectedSystemApp } }
    var selectedApp by remember { mutableStateOf(selectable.firstOrNull()) }
    var maxLaunches by remember { mutableIntStateOf(5) }
    var search by remember { mutableStateOf("") }

    val tokens = LocalRebornGlassTokens.current
    val filtered = remember(selectable, search) {
        val q = search.trim().lowercase()
        selectable.filter { q.isEmpty() || it.label.lowercase().contains(q) }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Daily Launch Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Max opens/day: $maxLaunches",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.accentText,
                    )
                    Row {
                        TextButton(
                            onClick = { if (maxLaunches > 1) maxLaunches-- },
                            colors = glassTextButtonColors(),
                        ) { Text("-1") }
                        TextButton(
                            onClick = { if (maxLaunches < 50) maxLaunches++ },
                            colors = glassTextButtonColors(),
                        ) { Text("+1") }
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter apps...") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { selectedApp = app }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedApp?.packageName == app.packageName,
                                onClick = { selectedApp = app },
                            )
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    val chosen = selectedApp ?: return@GlassPrimaryButton
                    onSave(
                        LaunchLimitRule(
                            packageName = chosen.packageName,
                            appLabel = chosen.label,
                            maxLaunchesPerDay = maxLaunches,
                            isEnabled = true,
                        )
                    )
                },
                enabled = selectedApp != null,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.testTag("launch_limit_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = glassTextButtonColors()) { Text("Cancel") }
        },
    )
}
