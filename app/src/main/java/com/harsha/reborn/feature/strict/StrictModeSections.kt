package com.harsha.reborn.feature.strict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.model.StrictEventType
import com.harsha.reborn.core.data.model.StrictModeConfig
import com.harsha.reborn.core.data.model.StrictModeLogEntry
import com.harsha.reborn.ui.components.GlassAlertDialog
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPillBadge
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange
import com.harsha.reborn.ui.theme.StatusCritical
import com.harsha.reborn.ui.theme.StatusHealthy
import com.harsha.reborn.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun TrustedCodeConfirmDialog(
    code: String,
    context: Context,
    onDismiss: () -> Unit,
    onConfirmLock: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Trusted-Contact Code First") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Once Strict Mode locks, Reborn hides this code so only your friend has it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PhoenixOrange.copy(alpha = 0.16f))
                        .border(1.dp, PhoenixAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhoenixAmber,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        onClick = { copyText(context, code) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                    GlassButton(
                        onClick = { shareTrustedCode(context, code) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        },
        confirmButton = {
            GlassPrimaryButton(
                onClick = onConfirmLock,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("I Shared It — Lock Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun StrictLockedOverviewCard(
    config: StrictModeConfig,
    nowEpochMs: Long,
) {
    val remSec = ((config.endsAtEpochMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
    val hrs = remSec / 3600L
    val mins = (remSec % 3600L) / 60L
    val secs = remSec % 60L
    val endsAtText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        .format(Date(config.endsAtEpochMs))

    GlassCard(
        cornerRadius = 30.dp,
        elevated = true,
        tintColor = PhoenixOrange.copy(alpha = 0.14f),
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier.testTag("strict_active_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PhoenixProgressRing(
                    progress = (remSec.toFloat() / 7200f).coerceIn(0.12f, 1f),
                    size = 64.dp,
                    strokeWidth = 6.dp,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PhoenixAmber,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column {
                    Text(
                        text = "Strict Mode Locked",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhoenixAmber,
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = "Automatically unlocks at $endsAtText. Rules, friction, and system guard settings are locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StrictEarlyUnlockCard(
    config: StrictModeConfig,
    isChargerConnected: Boolean,
    unlockPinCandidate: String,
    onUnlockPinChange: (String) -> Unit,
    unlockCodeCandidate: String,
    onUnlockCodeChange: (String) -> Unit,
    onAttemptEarlyUnlock: () -> Unit,
) {
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = PhoenixAmber)
                Text(
                    text = "Unlock Early",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (config.requireChargerConnected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (isChargerConnected) StatusHealthy else StatusWarning,
                    )
                    Text(
                        text = if (isChargerConnected) {
                            "Charger connected ✓"
                        } else {
                            "Plug in a charger to satisfy unlock condition"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (config.requirePin) {
                OutlinedTextField(
                    value = unlockPinCandidate,
                    onValueChange = onUnlockPinChange,
                    label = { Text("Enter Strict Mode PIN") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strict_unlock_pin_input"),
                )
            }

            if (config.requireTrustedContactCode) {
                OutlinedTextField(
                    value = unlockCodeCandidate,
                    onValueChange = onUnlockCodeChange,
                    label = { Text("Enter Trusted-Contact Code") },
                    placeholder = { Text("e.g. ABCD-1234") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strict_unlock_code_input"),
                )
            }

            GlassPrimaryButton(
                onClick = onAttemptEarlyUnlock,
                enabled = !config.requireChargerConnected || isChargerConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strict_early_unlock_button"),
            ) {
                Text("Verify & Unlock Early")
            }
        }
    }
}

@Composable
internal fun StrictEmergencySectionCard(
    waitMinutes: Int,
    onStartEmergencyEscape: () -> Unit,
) {
    GlassCard(
        cornerRadius = 26.dp,
        tintColor = StatusCritical.copy(alpha = 0.08f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = StatusWarning)
                Text(
                    text = "Emergency Escape Hatch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Requires a ${waitMinutes}-minute uninterrupted cool-down and typing an affirmation phrase. Recorded in the log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton(
                onClick = onStartEmergencyEscape,
                contentColor = StatusCritical,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strict_emergency_escape_button"),
            ) {
                Text("Start Emergency Escape (${waitMinutes}m wait)")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StrictSetupDurationCard(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
) {
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = PhoenixOrange)
                Text(
                    text = "Lock Duration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Duration: ${formatDurationMinutes(durationMinutes)}",
                style = MaterialTheme.typography.titleSmall,
                color = PhoenixAmber,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(30, 60, 120, 240, 480, 1440).forEach { preset ->
                    val selected = durationMinutes == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onDurationChange(preset) },
                        colors = glassFilterChipColors(),
                        border = glassFilterChipBorder(selected),
                        label = { Text(formatDurationMinutes(preset)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("strict_duration_$preset"),
                    )
                }
            }
            Slider(
                value = durationMinutes.toFloat(),
                onValueChange = { onDurationChange((it / 15f).roundToInt().coerceAtLeast(1) * 15) },
                valueRange = 15f..1440f,
                colors = SliderDefaults.colors(
                    thumbColor = PhoenixAmber,
                    activeTrackColor = PhoenixOrange,
                ),
            )
        }
    }
}

@Composable
internal fun StrictSetupSafeguardsCard(
    requirePin: Boolean,
    onRequirePinChange: (Boolean) -> Unit,
    pinInput: String,
    onPinInputChange: (String) -> Unit,
    requireCharger: Boolean,
    onRequireChargerChange: (Boolean) -> Unit,
    isChargerConnected: Boolean,
    requireTrustedCode: Boolean,
    onRequireTrustedCodeChange: (Boolean) -> Unit,
    generatedTrustedCode: String,
    onRegenerateTrustedCode: () -> Unit,
    context: Context,
) {
    val tokens = LocalRebornGlassTokens.current
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Optional Early Unlock Safeguards",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Leave all unchecked for a pure duration lock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = requirePin,
                    onCheckedChange = onRequirePinChange,
                    modifier = Modifier.testTag("strict_require_pin_checkbox"),
                )
                Icon(Icons.Rounded.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Require PIN", style = MaterialTheme.typography.bodyMedium)
            }

            if (requirePin) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) onPinInputChange(it) },
                    label = { Text("4-8 digit PIN") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassOutlinedTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strict_setup_pin_input"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = requireCharger,
                    onCheckedChange = onRequireChargerChange,
                    modifier = Modifier.testTag("strict_require_charger_checkbox"),
                )
                Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Require Charger Connected", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (isChargerConnected) "Currently plugged in" else "Currently unplugged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = requireTrustedCode,
                    onCheckedChange = onRequireTrustedCodeChange,
                    modifier = Modifier.testTag("strict_require_trusted_checkbox"),
                )
                Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Require Trusted-Contact Code", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Offline code to send a friend before locking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (requireTrustedCode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(tokens.cardElevatedFill)
                        .border(1.dp, tokens.borderTopLeft, RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = generatedTrustedCode,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhoenixAmber,
                    )
                    Row {
                        IconButton(onClick = onRegenerateTrustedCode) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate code")
                        }
                        IconButton(onClick = { copyText(context, generatedTrustedCode) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StrictSetupProtectionsCard(
    blockSystemGuards: Boolean,
    onBlockSystemGuardsChange: (Boolean) -> Unit,
    disallowUnlockPasses: Boolean,
    onDisallowUnlockPassesChange: (Boolean) -> Unit,
    emergencyWaitMinutes: Int,
    onEmergencyWaitChange: (Int) -> Unit,
) {
    val view = LocalView.current
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Tamper & Settings Protections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lock Settings & System Guards", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Blocks Reborn rules/friction edits plus Accessibility, Force Stop, Device Admin & Uninstall screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = blockSystemGuards,
                    colors = glassSwitchColors(),
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onBlockSystemGuardsChange(it)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable Intent Unlock Passes", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Prevents temporary 5/10/15m passes while Strict Mode is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = disallowUnlockPasses,
                    colors = glassSwitchColors(),
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onDisallowUnlockPassesChange(it)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Emergency wait: ${emergencyWaitMinutes}m", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(
                        onClick = { if (emergencyWaitMinutes > 1) onEmergencyWaitChange(emergencyWaitMinutes - 1) },
                        enabled = emergencyWaitMinutes > 1,
                    ) { Text("-1m") }
                    TextButton(
                        onClick = { if (emergencyWaitMinutes < 30) onEmergencyWaitChange(emergencyWaitMinutes + 1) },
                        enabled = emergencyWaitMinutes < 30,
                    ) { Text("+1m") }
                }
            }
        }
    }
}

@Composable
internal fun DeviceAdminCard(
    isDeviceAdminActive: Boolean,
    isLocked: Boolean,
    onEnableDeviceAdmin: () -> Unit,
    onDisableDeviceAdmin: () -> Unit,
) {
    GlassCard(cornerRadius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isDeviceAdminActive) Icons.Rounded.CheckCircle else Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (isDeviceAdminActive) StatusHealthy else PhoenixAmber,
                )
                Text(
                    text = "Device Admin Uninstall Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (isDeviceAdminActive) {
                    "Active: Android prevents direct uninstallation of Reborn while Device Admin is enabled."
                } else {
                    "Optional safeguard: enable Device Admin to prevent impulsive uninstallation of Reborn."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isDeviceAdminActive) {
                GlassButton(
                    onClick = onEnableDeviceAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_device_admin_button"),
                ) {
                    Text("Enable Device Admin Guard")
                }
            } else if (!isLocked) {
                GlassButton(
                    onClick = onDisableDeviceAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disable_device_admin_button"),
                ) {
                    Text("Turn Off Device Admin")
                }
            }
        }
    }
}

@Composable
internal fun StrictLogEntryCard(log: StrictModeLogEntry) {
    val dateText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        .format(Date(log.timestampEpochMs))
    val badgeColor = when (log.eventType) {
        StrictEventType.EMERGENCY_ESCAPE -> StatusCritical.copy(alpha = 0.22f)
        StrictEventType.TIMER_ENDED -> StatusHealthy.copy(alpha = 0.20f)
        else -> null
    }
    GlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPillBadge(containerColor = badgeColor) {
                    Text(
                        text = log.eventType.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(log.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun formatDurationMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remMin = minutes % 60
    return when {
        hours > 0 && remMin > 0 -> "${hours}h ${remMin}m"
        hours > 0 -> "${hours}h"
        else -> "${remMin}m"
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Trusted Contact Code", text))
}

private fun shareTrustedCode(context: Context, code: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Here is my Reborn Strict Mode unlock code: $code. Hold onto it until my focus session ends!",
        )
    }
    runCatching {
        context.startActivity(Intent.createChooser(sendIntent, "Send Trusted-Contact Code"))
    }
}
