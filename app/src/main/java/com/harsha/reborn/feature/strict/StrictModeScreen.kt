package com.harsha.reborn.feature.strict

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.StatusCritical
import com.harsha.reborn.ui.theme.StatusHealthy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictModeScreen(
    onBack: () -> Unit,
    viewModel: StrictModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val isLocked = state.config.isEffectivelyLocked(state.nowEpochMs)

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDeviceAdminStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceAdminStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var durationMinutes by remember { mutableIntStateOf(120) }
    var requirePin by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var requireCharger by remember { mutableStateOf(false) }
    var requireTrustedCode by remember { mutableStateOf(false) }
    var blockSystemGuards by remember { mutableStateOf(true) }
    var disallowUnlockPasses by remember { mutableStateOf(true) }
    var emergencyWaitMinutes by remember { mutableIntStateOf(5) }
    var showTrustedConfirmDialog by remember { mutableStateOf(false) }

    var unlockPinCandidate by remember { mutableStateOf("") }
    var unlockCodeCandidate by remember { mutableStateOf("") }
    var showEmergencyEscapeDialog by remember { mutableStateOf(false) }

    if (showEmergencyEscapeDialog) {
        EmergencyEscapeDialog(
            waitMinutes = state.config.emergencyWaitMinutes,
            errorMessage = state.feedbackError,
            onDismiss = {
                viewModel.clearFeedback()
                showEmergencyEscapeDialog = false
            },
            onConfirmEscape = { phrase ->
                viewModel.executeEmergencyEscape(phrase) {
                    showEmergencyEscapeDialog = false
                }
            },
        )
    }

    if (showTrustedConfirmDialog) {
        TrustedCodeConfirmDialog(
            code = state.generatedTrustedCode,
            context = context,
            onDismiss = { showTrustedConfirmDialog = false },
            onConfirmLock = {
                showTrustedConfirmDialog = false
                viewModel.activateStrictMode(
                    durationMinutes = durationMinutes,
                    requirePin = requirePin,
                    pin = pinInput,
                    requireCharger = requireCharger,
                    requireTrustedCode = requireTrustedCode,
                    blockSystemGuards = blockSystemGuards,
                    disallowUnlockPasses = disallowUnlockPasses,
                    emergencyWaitMinutes = emergencyWaitMinutes,
                )
                pinInput = ""
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Strict Mode") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("strict_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.feedbackError != null || state.feedbackMessage != null) {
                    item {
                        val isErr = state.feedbackError != null
                        GlassCard(
                            cornerRadius = 20.dp,
                            tintColor = if (isErr) StatusCritical.copy(alpha = 0.16f) else StatusHealthy.copy(alpha = 0.14f),
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.feedbackError ?: state.feedbackMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isErr) StatusCritical else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
                            }
                        }
                    }
                }

                if (isLocked) {
                    item {
                        StrictLockedOverviewCard(
                            config = state.config,
                            nowEpochMs = state.nowEpochMs,
                        )
                    }

                    if (state.config.hasAnyEarlyUnlockMethod()) {
                        item {
                            StrictEarlyUnlockCard(
                                config = state.config,
                                isChargerConnected = state.isChargerConnected,
                                unlockPinCandidate = unlockPinCandidate,
                                onUnlockPinChange = { unlockPinCandidate = it },
                                unlockCodeCandidate = unlockCodeCandidate,
                                onUnlockCodeChange = { unlockCodeCandidate = it },
                                onAttemptEarlyUnlock = {
                                    viewModel.attemptEarlyUnlock(
                                        pinCandidate = unlockPinCandidate,
                                        trustedCodeCandidate = unlockCodeCandidate,
                                    )
                                },
                            )
                        }
                    } else {
                        item {
                            GlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                                Text(
                                    text = "Duration-only lock active: no early unlock shortcut is enabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        StrictEmergencySectionCard(
                            waitMinutes = state.config.emergencyWaitMinutes,
                            onStartEmergencyEscape = {
                                viewModel.clearFeedback()
                                showEmergencyEscapeDialog = true
                            },
                        )
                    }
                } else {
                    item {
                        StrictSetupDurationCard(
                            durationMinutes = durationMinutes,
                            onDurationChange = { durationMinutes = it },
                        )
                    }

                    item {
                        StrictSetupSafeguardsCard(
                            requirePin = requirePin,
                            onRequirePinChange = { requirePin = it },
                            pinInput = pinInput,
                            onPinInputChange = { pinInput = it },
                            requireCharger = requireCharger,
                            onRequireChargerChange = { requireCharger = it },
                            isChargerConnected = state.isChargerConnected,
                            requireTrustedCode = requireTrustedCode,
                            onRequireTrustedCodeChange = { requireTrustedCode = it },
                            generatedTrustedCode = state.generatedTrustedCode,
                            onRegenerateTrustedCode = viewModel::regenerateTrustedCode,
                            context = context,
                        )
                    }

                    item {
                        StrictSetupProtectionsCard(
                            blockSystemGuards = blockSystemGuards,
                            onBlockSystemGuardsChange = { blockSystemGuards = it },
                            disallowUnlockPasses = disallowUnlockPasses,
                            onDisallowUnlockPassesChange = { disallowUnlockPasses = it },
                            emergencyWaitMinutes = emergencyWaitMinutes,
                            onEmergencyWaitChange = { emergencyWaitMinutes = it },
                        )
                    }

                    item {
                        GlassPrimaryButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                if (requirePin && pinInput.trim().length < 4) {
                                    viewModel.activateStrictMode(
                                        durationMinutes = durationMinutes,
                                        requirePin = true,
                                        pin = pinInput,
                                        requireCharger = requireCharger,
                                        requireTrustedCode = requireTrustedCode,
                                        blockSystemGuards = blockSystemGuards,
                                        disallowUnlockPasses = disallowUnlockPasses,
                                        emergencyWaitMinutes = emergencyWaitMinutes,
                                    )
                                } else if (requireTrustedCode) {
                                    showTrustedConfirmDialog = true
                                } else {
                                    viewModel.activateStrictMode(
                                        durationMinutes = durationMinutes,
                                        requirePin = requirePin,
                                        pin = pinInput,
                                        requireCharger = requireCharger,
                                        requireTrustedCode = false,
                                        blockSystemGuards = blockSystemGuards,
                                        disallowUnlockPasses = disallowUnlockPasses,
                                        emergencyWaitMinutes = emergencyWaitMinutes,
                                    )
                                    pinInput = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("activate_strict_mode_button"),
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lock Strict Mode (${formatDurationMinutes(durationMinutes)})")
                        }
                    }
                }

                item {
                    DeviceAdminCard(
                        isDeviceAdminActive = state.isDeviceAdminActive,
                        isLocked = isLocked,
                        onEnableDeviceAdmin = { adminLauncher.launch(viewModel.getActivateDeviceAdminIntent()) },
                        onDisableDeviceAdmin = viewModel::deactivateDeviceAdmin,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = PhoenixAmber)
                            Text("Strict Mode Log", style = MaterialTheme.typography.titleMedium)
                        }
                        if (state.logs.isNotEmpty() && !isLocked) {
                            TextButton(onClick = viewModel::clearLogs) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }
                }

                if (state.logs.isEmpty()) {
                    item {
                        GlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = "Strict Mode locks, early unlocks, and emergency escapes are logged locally here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(state.logs, key = { "strict_log_${it.id}" }) { log ->
                        StrictLogEntryCard(log = log)
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}
