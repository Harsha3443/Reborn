package com.harsha.reborn.feature.smart

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.model.ChargerCondition
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.core.engine.OfflineCoordinates
import com.harsha.reborn.core.engine.RuleEvaluator
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import com.harsha.reborn.feature.blocking.MultiAppSelectorDialog
import com.harsha.reborn.ui.components.GlassAlertDialog
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionalRuleEditorDialog(
    initial: ConditionalRule?,
    installedApps: List<InstalledAppInfo>,
    defaultPackages: Set<String>,
    currentWifiSsid: String?,
    hasLocationPermission: Boolean,
    onSampleWifiSsid: () -> String?,
    onSampleCoordinates: () -> OfflineCoordinates?,
    onDismiss: () -> Unit,
    onSave: (ConditionalRule) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "Night Charging Focus") }
    var packages by remember {
        mutableStateOf(initial?.packageNames?.takeIf { it.isNotEmpty() } ?: defaultPackages)
    }
    var requireWifi by remember { mutableStateOf(initial?.requireWifi ?: false) }
    var wifiSsid by remember { mutableStateOf(initial?.wifiSsid ?: "") }
    var chargerCondition by remember { mutableStateOf(initial?.chargerCondition ?: ChargerCondition.ANY) }
    var useTimeWindow by remember { mutableStateOf(initial?.useTimeWindow ?: false) }
    var startMin by remember { mutableIntStateOf(initial?.startMinuteOfDay ?: 21 * 60) }
    var endMin by remember { mutableIntStateOf(initial?.endMinuteOfDay ?: 7 * 60) }
    var useLocation by remember { mutableStateOf(initial?.useLocation ?: false) }
    var locationLabel by remember { mutableStateOf(initial?.locationLabel ?: "Saved Area") }
    var locationLat by remember { mutableDoubleStateOf(initial?.locationLat ?: 0.0) }
    var locationLon by remember { mutableDoubleStateOf(initial?.locationLon ?: 0.0) }
    var locationRadius by remember { mutableIntStateOf(initial?.locationRadiusMeters ?: 300) }
    var locationNote by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val coords = onSampleCoordinates()
            if (coords != null) {
                locationLat = coords.latitude
                locationLon = coords.longitude
                locationNote = String.format(
                    Locale.US,
                    "Captured area: %.4f, %.4f",
                    coords.latitude,
                    coords.longitude,
                )
            } else {
                locationNote = "Permission granted. Open Maps once or enter coordinates below if last location is unavailable."
            }
        } else {
            locationNote = "Coarse location permission was not granted."
        }
    }

    if (showAppPicker) {
        MultiAppSelectorDialog(
            title = "Apps for Conditional Rule",
            installedApps = installedApps,
            initialSelected = packages,
            onDismiss = { showAppPicker = false },
            onConfirm = {
                packages = it
                showAppPicker = false
            },
        )
    }

    val hasCondition = requireWifi || chargerCondition != ChargerCondition.ANY || useTimeWindow || useLocation

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Conditional Rule" else "Edit Conditional Rule") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conditional_name_input"),
                )

                TextButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.testTag("conditional_pick_apps_button"),
                ) {
                    Text("Target Apps (${packages.size} chosen)")
                }

                // 1. Wi-Fi condition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = requireWifi,
                        onCheckedChange = { requireWifi = it },
                        modifier = Modifier.testTag("conditional_wifi_checkbox"),
                    )
                    Text("Only on Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                }
                if (requireWifi) {
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("Specific Wi-Fi Name (blank = any Wi-Fi)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = glassOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val activeSsid = currentWifiSsid ?: onSampleWifiSsid()
                    if (!activeSsid.isNullOrBlank()) {
                        TextButton(onClick = { wifiSsid = activeSsid }) {
                            Text("Use current Wi-Fi ($activeSsid)")
                        }
                    }
                }

                // 2. Charging condition
                Text("Battery / Charger", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ChargerCondition.ANY to "Any",
                        ChargerCondition.CHARGING to "Charging",
                        ChargerCondition.NOT_CHARGING to "On Battery",
                    ).forEach { (cond, label) ->
                        val isSelected = chargerCondition == cond
                        FilterChip(
                            selected = isSelected,
                            onClick = { chargerCondition = cond },
                            colors = glassFilterChipColors(),
                            border = glassFilterChipBorder(isSelected),
                            label = { Text(label) },
                            modifier = Modifier.testTag("conditional_charger_${cond.name.lowercase()}"),
                        )
                    }
                }

                // 3. Time of Day condition
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useTimeWindow,
                        onCheckedChange = { useTimeWindow = it },
                        modifier = Modifier.testTag("conditional_time_checkbox"),
                    )
                    Text("Time of day window", style = MaterialTheme.typography.bodyMedium)
                }
                if (useTimeWindow) {
                    ConditionalTimeRow("Start", startMin) { startMin = it }
                    ConditionalTimeRow("End", endMin) { endMin = it }
                }

                // 4. Optional Coarse Location zone
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useLocation,
                        onCheckedChange = { useLocation = it },
                        modifier = Modifier.testTag("conditional_location_checkbox"),
                    )
                    Text("Optional area / location zone", style = MaterialTheme.typography.bodyMedium)
                }
                if (useLocation) {
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Zone Label (e.g. Office / Home)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = glassOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlassButton(
                        onClick = {
                            if (hasLocationPermission) {
                                val coords = onSampleCoordinates()
                                if (coords != null) {
                                    locationLat = coords.latitude
                                    locationLon = coords.longitude
                                    locationNote = String.format(
                                        Locale.US,
                                        "Captured area: %.4f, %.4f",
                                        coords.latitude,
                                        coords.longitude,
                                    )
                                } else {
                                    locationNote = "Could not read recent coarse location yet."
                                }
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (locationLat != 0.0 || locationLon != 0.0) {
                                String.format(Locale.US, "Area %.3f, %.3f (Tap to update)", locationLat, locationLon)
                            } else {
                                "Use Current Approximate Area"
                            }
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(150, 300, 500, 1000).forEach { radius ->
                            val isSelected = locationRadius == radius
                            FilterChip(
                                selected = isSelected,
                                onClick = { locationRadius = radius },
                                colors = glassFilterChipColors(),
                                border = glassFilterChipBorder(isSelected),
                                label = { Text("${radius}m") },
                            )
                        }
                    }
                    locationNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    onSave(
                        ConditionalRule(
                            id = initial?.id ?: 0L,
                            name = name.trim().ifEmpty { "Smart Condition" },
                            packageNames = packages,
                            requireWifi = requireWifi,
                            wifiSsid = wifiSsid.trim(),
                            chargerCondition = chargerCondition,
                            useTimeWindow = useTimeWindow,
                            startMinuteOfDay = startMin,
                            endMinuteOfDay = endMin,
                            useLocation = useLocation,
                            locationLabel = locationLabel.trim(),
                            locationLat = locationLat,
                            locationLon = locationLon,
                            locationRadiusMeters = locationRadius,
                            isEnabled = initial?.isEnabled ?: true,
                        )
                    )
                },
                enabled = packages.isNotEmpty() && hasCondition,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.testTag("conditional_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConditionalTimeRow(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: ${RuleEvaluator.formatMinute(minuteOfDay)}", style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = { onChange((minuteOfDay - 30 + 1440) % 1440) }) { Text("-30m") }
            TextButton(onClick = { onChange((minuteOfDay + 30) % 1440) }) { Text("+30m") }
        }
    }
}

@Composable
fun FeedSelectorsConfigDialog(
    currentCustomJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
) {
    var jsonText by remember {
        mutableStateOf(currentCustomJson.ifBlank { FeedSelectorRegistry.DEFAULT_SELECTOR_JSON })
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Feed Selector Config (JSON)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Config-driven view IDs and labels for Shorts, Reels, and TikTok feeds. Invalid configs fail open safely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .testTag("feed_selector_json_input"),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = { jsonText = FeedSelectorRegistry.DEFAULT_SELECTOR_JSON },
                    modifier = Modifier.testTag("feed_selector_reset_button"),
                ) {
                    Text("Reset to Built-in Defaults")
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    val toSave = if (jsonText.trim() == FeedSelectorRegistry.DEFAULT_SELECTOR_JSON.trim()) {
                        ""
                    } else {
                        jsonText
                    }
                    if (onSave(toSave)) onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier.testTag("feed_selector_save_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
