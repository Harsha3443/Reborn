package com.harsha.reborn.feature.smart

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange
import com.harsha.reborn.ui.theme.StatusHealthy
import com.harsha.reborn.ui.theme.StatusWarning
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedBlockingScreen(
    onBack: () -> Unit,
    onOpenPermissionHealth: () -> Unit = {},
    viewModel: FeedBlockingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showJsonDialog by remember { mutableStateOf(false) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importSelectorsFromUri(uri)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealth()
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

    if (showJsonDialog) {
        FeedSelectorsConfigDialog(
            currentCustomJson = state.customFeedSelectorsJson,
            onDismiss = { showJsonDialog = false },
            onSave = viewModel::saveCustomSelectorsJson,
        )
    }

    val enabledCount = state.appToggles.count { it.isEnabled }
    val totalCount = state.appToggles.size.coerceAtLeast(4)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(id = R.string.feed_blocking_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("feed_blocking_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.insights_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.feedbackMessage?.let { message ->
                    item {
                        GlassCard(
                            cornerRadius = 20.dp,
                            elevated = true,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feed_blocking_feedback_banner"),
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
                                TextButton(onClick = viewModel::clearFeedback) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                if (state.isStrictLocked) {
                    item {
                        GlassCard(
                            cornerRadius = 22.dp,
                            tintColor = PhoenixOrange.copy(alpha = 0.14f),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feed_strict_locked_banner"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Locked by Strict Mode (${state.strictRemainingMinutes}m left)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Feed Blocking toggles and selector config are locked until Strict Mode ends.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!state.isAccessibilityEnabled) {
                    item {
                        GlassCard(
                            cornerRadius = 22.dp,
                            tintColor = StatusWarning.copy(alpha = 0.14f),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = StatusWarning,
                                    )
                                    Text(
                                        text = "Reborn App Guard (Accessibility) is off. Enable it so Reborn can back out of short-video players automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                GlassPrimaryButton(
                                    onClick = onOpenPermissionHealth,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Open Permission Health")
                                }
                            }
                        }
                    }
                }

                // Overview card
                item {
                    GlassCard(
                        elevated = true,
                        cornerRadius = 28.dp,
                        contentPadding = PaddingValues(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PhoenixProgressRing(
                                progress = if (enabledCount == 0) 0.08f else (enabledCount.toFloat() / totalCount.toFloat()),
                                size = 68.dp,
                                strokeWidth = 6.dp,
                            ) {
                                Text(
                                    text = "$enabledCount/$totalCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Short-Video Shield",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    GlassPillBadge(
                                        containerColor = if (enabledCount > 0) {
                                            StatusHealthy.copy(alpha = 0.18f)
                                        } else {
                                            null
                                        },
                                    ) {
                                        Text(
                                            text = if (enabledCount > 0) "Active" else "Off",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (enabledCount > 0) StatusHealthy else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(id = R.string.feed_blocking_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Per-app feed toggles card
                item {
                    GlassCard(
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                )
                                Text(
                                    text = "Protected Feeds by App",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            state.appToggles.forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(tokens.cardSubtleFill)
                                        .border(1.dp, tokens.borderBottomRight, RoundedCornerShape(18.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = app.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (app.packageName.isNotBlank()) {
                                                Text(
                                                    text = app.packageName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            Text(
                                                text = app.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = app.isEnabled,
                                            enabled = !state.isStrictLocked,
                                            colors = glassSwitchColors(),
                                            onCheckedChange = { checked ->
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                viewModel.setAppFeedEnabled(app.profileId, checked)
                                            },
                                            modifier = Modifier.testTag("feed_toggle_${app.profileId}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Allow Reels from DMs card
                item {
                    GlassCard(
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forum,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.feed_allow_dms_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(id = R.string.feed_allow_dms_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = state.allowReelsFromDms,
                                enabled = !state.isStrictLocked,
                                colors = glassSwitchColors(),
                                onCheckedChange = { checked ->
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    viewModel.setAllowReelsFromDms(checked)
                                },
                                modifier = Modifier.testTag("feed_allow_dms_switch"),
                            )
                        }
                    }
                }

                // Config-driven Selector JSON asset & file import card
                item {
                    GlassCard(
                        cornerRadius = 26.dp,
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = PhoenixAmber,
                                )
                                Text(
                                    text = "Selector Rules Config",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Text(
                                text = if (state.usingCustomJson) {
                                    "Using custom imported JSON (${state.activeCatalog.profiles.size} profiles, schema v${state.activeCatalog.schemaVersion})."
                                } else {
                                    "Using built-in feed_selectors.json asset (${state.activeCatalog.profiles.size} profiles, schema v${state.activeCatalog.schemaVersion})."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            GlassPrimaryButton(
                                onClick = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                enabled = !state.isStrictLocked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feed_import_json_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.feed_import_json_button))
                            }

                            GlassButton(
                                onClick = { showJsonDialog = true },
                                enabled = !state.isStrictLocked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feed_view_json_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.feed_view_json_button))
                            }

                            if (state.usingCustomJson) {
                                GlassButton(
                                    onClick = viewModel::resetToDefaultJson,
                                    enabled = !state.isStrictLocked,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("feed_reset_json_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.feed_reset_json_button))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
