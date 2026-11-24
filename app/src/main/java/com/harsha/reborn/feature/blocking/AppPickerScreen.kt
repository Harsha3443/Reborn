package com.harsha.reborn.feature.blocking

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.core.data.model.AppCategory
import com.harsha.reborn.core.data.model.InstalledAppInfo
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens

@Composable
fun AppPickerScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("app_picker_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = "App Picker",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "${state.selectedPackages.size} selected of ${state.totalInstalledCount} apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.refreshApps()
                    },
                    modifier = Modifier.testTag("app_picker_refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh installed apps",
                    )
                }
            }

            if (state.isStrictSettingsLocked) {
                GlassCard(
                    cornerRadius = 20.dp,
                    tintColor = tokens.accentTintFill,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("app_picker_strict_locked_banner"),
                ) {
                    Row(
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
                            text = "Strict Mode locked — selected apps can be added, not removed.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search apps or package name...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onSearchQueryChange("") },
                            modifier = Modifier.testTag("app_picker_clear_search"),
                        ) {
                            Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = glassOutlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("app_picker_search_input"),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.availableCategories, key = { it.name }) { category ->
                    val label = if (category == AppCategory.SELECTED) {
                        "${category.displayName} (${state.selectedPackages.size})"
                    } else {
                        category.displayName
                    }
                    val selected = state.selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onCategorySelected(category) },
                        colors = glassFilterChipColors(),
                        border = glassFilterChipBorder(selected),
                        label = { Text(label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("category_chip_${category.name.lowercase()}"),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.filteredApps.size} shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { viewModel.selectAllFiltered() },
                        enabled = state.filteredApps.any { !it.isProtectedSystemApp },
                        colors = glassTextButtonColors(),
                        modifier = Modifier.testTag("app_picker_select_filtered"),
                    ) {
                        Text("Select Filtered")
                    }
                    TextButton(
                        onClick = { viewModel.clearAllSelected() },
                        enabled = state.selectedPackages.isNotEmpty() && !state.isStrictSettingsLocked,
                        colors = glassTextButtonColors(),
                        modifier = Modifier.testTag("app_picker_clear_all"),
                    ) {
                        Text("Clear")
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = tokens.accentText)
                    }
                }

                state.filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassCard(cornerRadius = 26.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = "No matching apps found",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Try clearing your search or choosing another category.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    ) {
                        items(state.filteredApps, key = { it.packageName }) { app ->
                            AppPickerRow(
                                app = app,
                                isSelected = state.selectedPackages.contains(app.packageName),
                                loadIcon = viewModel::loadIcon,
                                onToggle = { viewModel.toggleApp(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    isSelected: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onToggle: () -> Unit,
) {
    val tokens = LocalRebornGlassTokens.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadIcon(app.packageName)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(
                    if (app.isProtectedSystemApp) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onToggle)
                    }
                )
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .testTag("app_row_${app.packageName}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.cardSubtleFill),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = icon
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "${app.label} icon",
                        modifier = Modifier.size(30.dp),
                    )
                } else {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.category.displayName} • ${app.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (app.isProtectedSystemApp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Protected system app",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = app.protectionReason ?: "Protected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("app_checkbox_${app.packageName}"),
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)
    }
}
