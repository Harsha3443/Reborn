package com.harsha.reborn.feature.smart

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.model.ConditionalRule
import com.harsha.reborn.core.engine.OfflineCoordinates
import com.harsha.reborn.core.engine.RuleEvaluator
import com.harsha.reborn.core.engine.feed.FeedSelectorRegistry
import com.harsha.reborn.feature.blocking.RulesUiState
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartRulesSection(
    state: RulesUiState,
    onSaveConditionalRule: (ConditionalRule) -> Unit,
    onToggleConditionalRule: (Long, Boolean) -> Unit,
    onDeleteConditionalRule: (Long) -> Unit,
    onSetFeedBlockingEnabled: (Boolean) -> Unit,
    onToggleFeedProfile: (String, Boolean) -> Unit,
    onSaveFeedSelectorsJson: (String) -> Boolean,
    onSetBrowserBlockingEnabled: (Boolean) -> Unit,
    onAddBrowserKeywordRule: (String, Boolean) -> Unit,
    onToggleBrowserKeywordRule: (Long, Boolean) -> Unit,
    onDeleteBrowserKeywordRule: (Long) -> Unit,
    onSetGrayscaleDimEnabled: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onSampleWifiSsid: () -> String?,
    onSampleCoordinates: () -> OfflineCoordinates?,
    onOpenFeedBlocking: () -> Unit = {},
) {
    var editingConditional by remember { mutableStateOf<ConditionalRule?>(null) }
    var creatingConditional by remember { mutableStateOf(false) }
    var showFeedJsonDialog by remember { mutableStateOf(false) }
    var browserInput by remember { mutableStateOf("") }
    var browserIsDomain by remember { mutableStateOf(true) }
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    val smart = state.snapshot.smartRulesConfig
    val activeCatalog = remember(smart.customFeedSelectorsJson) {
        FeedSelectorRegistry.loadCatalog(smart.customFeedSelectorsJson)
    }

    if (creatingConditional || editingConditional != null) {
        ConditionalRuleEditorDialog(
            initial = editingConditional,
            installedApps = state.installedApps,
            defaultPackages = state.defaultSelectedPackages,
            currentWifiSsid = state.currentWifiSsid,
            hasLocationPermission = state.hasCoarseLocationPermission,
            onSampleWifiSsid = onSampleWifiSsid,
            onSampleCoordinates = onSampleCoordinates,
            onDismiss = {
                creatingConditional = false
                editingConditional = null
            },
            onSave = {
                onSaveConditionalRule(it)
                creatingConditional = false
                editingConditional = null
            },
        )
    }

    if (showFeedJsonDialog) {
        FeedSelectorsConfigDialog(
            currentCustomJson = smart.customFeedSelectorsJson,
            onDismiss = { showFeedJsonDialog = false },
            onSave = onSaveFeedSelectorsJson,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Glass Surface #1: Smart Shields (Grayscale Dim, Short Feeds, Browser Filter)
        item {
            GlassCard(
                cornerRadius = 26.dp,
                contentPadding = PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 1. Grayscale Dim
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Contrast,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Column {
                                    Text(
                                        text = "Grayscale Dim After Limit",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Desaturate limited apps instead of a hard block.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = smart.grayscaleDimOnLimitEnabled,
                                colors = glassSwitchColors(),
                                onCheckedChange = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSetGrayscaleDimEnabled(it)
                                },
                                modifier = Modifier.testTag("smart_grayscale_dim_switch"),
                            )
                        }

                        if (!state.isOverlayGranted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Needs 'Display over other apps' permission.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.statusWarning,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = onOpenOverlaySettings,
                                    colors = glassTextButtonColors(),
                                ) { Text("Grant") }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                    // 2. Short Feeds
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Column {
                                    Text(
                                        text = "In-App Short Feeds",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Block Shorts, Reels & Spotlight while keeping messages.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = smart.inAppFeedBlockingEnabled,
                                colors = glassSwitchColors(),
                                onCheckedChange = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSetFeedBlockingEnabled(it)
                                },
                                modifier = Modifier.testTag("smart_feed_blocking_switch"),
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            activeCatalog.profiles.forEach { profile ->
                                val enabled = profile.id in smart.enabledFeedProfileIds
                                FilterChip(
                                    selected = enabled,
                                    onClick = { onToggleFeedProfile(profile.id, !enabled) },
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(enabled),
                                    label = { Text(profile.displayName) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("smart_feed_profile_${profile.id}"),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { showFeedJsonDialog = true },
                                colors = glassTextButtonColors(),
                                modifier = Modifier.testTag("smart_edit_feed_selectors_button"),
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Selector JSON")
                            }
                            TextButton(
                                onClick = onOpenFeedBlocking,
                                colors = glassTextButtonColors(),
                                modifier = Modifier.testTag("smart_open_feed_blocking_button"),
                            ) {
                                Text("Feed Blocking Settings")
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                    // 3. Browser Filter
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Column {
                                    Text(
                                        text = "Browser Website & Keyword Filter",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Local address-bar check across major Android browsers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = smart.browserBlockingEnabled,
                                colors = glassSwitchColors(),
                                onCheckedChange = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSetBrowserBlockingEnabled(it)
                                },
                                modifier = Modifier.testTag("smart_browser_blocking_switch"),
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = browserIsDomain,
                                onClick = { browserIsDomain = true },
                                colors = glassFilterChipColors(),
                                border = glassFilterChipBorder(browserIsDomain),
                                label = { Text("Domain") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("smart_browser_type_domain"),
                            )
                            FilterChip(
                                selected = !browserIsDomain,
                                onClick = { browserIsDomain = false },
                                colors = glassFilterChipColors(),
                                border = glassFilterChipBorder(!browserIsDomain),
                                label = { Text("Keyword") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("smart_browser_type_keyword"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = browserInput,
                                onValueChange = { browserInput = it },
                                placeholder = { Text(if (browserIsDomain) "reddit.com" else "breaking news") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = glassOutlinedTextFieldColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("smart_browser_pattern_input"),
                            )
                            GlassPrimaryButton(
                                onClick = {
                                    onAddBrowserKeywordRule(browserInput, browserIsDomain)
                                    browserInput = ""
                                },
                                enabled = browserInput.trim().length >= 2,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("smart_browser_add_button"),
                            ) {
                                Text("Add")
                            }
                        }

                        if (state.snapshot.browserKeywordRules.isEmpty()) {
                            Text(
                                text = "No blocked websites or keywords yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.snapshot.browserKeywordRules.forEach { rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.pattern,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = if (rule.isDomain) "Website domain" else "URL / search keyword",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = rule.isEnabled,
                                        colors = glassSwitchColors(),
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onToggleBrowserKeywordRule(rule.id, it)
                                        },
                                    )
                                    IconButton(onClick = { onDeleteBrowserKeywordRule(rule.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete browser rule")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Glass Surface #2: Conditional Rules
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = tokens.accentText)
                            Text("Conditional Rules", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(
                            onClick = { creatingConditional = true },
                            colors = glassTextButtonColors(),
                            modifier = Modifier.testTag("add_conditional_rule_button"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Rule")
                        }
                    }

                    if (state.snapshot.conditionalRules.isEmpty()) {
                        Text(
                            text = "Combine Wi-Fi, charger state, time of day, or an area zone to block distracting apps automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.snapshot.conditionalRules.forEachIndexed { idx, rule ->
                            if (idx > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${RuleEvaluator.buildConditionalDetail(rule)} • ${rule.packageNames.size} apps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = rule.isEnabled,
                                        colors = glassSwitchColors(),
                                        onCheckedChange = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onToggleConditionalRule(rule.id, it)
                                        },
                                        modifier = Modifier.testTag("conditional_switch_${rule.id}"),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = { editingConditional = rule },
                                        colors = glassTextButtonColors(),
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    IconButton(onClick = { onDeleteConditionalRule(rule.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete conditional rule")
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
