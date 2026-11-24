package com.harsha.reborn.feature.about

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.harsha.reborn.core.data.model.ThemeMode
import com.harsha.reborn.ui.components.GlassButton
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.GlassPrimaryButton
import com.harsha.reborn.ui.components.glassFilterChipBorder
import com.harsha.reborn.ui.components.glassFilterChipColors
import com.harsha.reborn.ui.components.glassSwitchColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onOpenPrivacyDisclosure: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenFeedBlocking: () -> Unit = {},
    onOpenPermissionHealth: () -> Unit = {},
    missingPermissionsCount: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val view = LocalView.current
    val tokens = LocalRebornGlassTokens.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(uri)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(id = R.string.nav_settings),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} • 100% Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Glass Surface #1: Navigation rows (Health, Feed Blocking, Privacy, Onboarding)
            item {
                val healthStatus = if (missingPermissionsCount == 0) {
                    "All permissions active"
                } else {
                    "$missingPermissionsCount permission${if (missingPermissionsCount == 1) "" else "s"} missing"
                }
                val healthColor = if (missingPermissionsCount == 0) tokens.statusHealthy else tokens.statusWarning

                GlassCard(
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsNavRow(
                            icon = Icons.Rounded.HealthAndSafety,
                            iconTint = healthColor,
                            title = "Permission Health",
                            subtitle = healthStatus,
                            subtitleColor = healthColor,
                            testTag = "settings_permission_health_row",
                            onClick = onOpenPermissionHealth,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = tokens.borderBottomRight,
                        )
                        SettingsNavRow(
                            icon = Icons.Rounded.SmartDisplay,
                            iconTint = tokens.accentText,
                            title = stringResource(id = R.string.feed_blocking_title),
                            subtitle = "YouTube Shorts, Reels & Spotlight",
                            testTag = "about_feed_blocking_button",
                            onClick = onOpenFeedBlocking,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = tokens.borderBottomRight,
                        )
                        SettingsNavRow(
                            icon = Icons.Rounded.PrivacyTip,
                            iconTint = tokens.accentText,
                            title = stringResource(id = R.string.about_privacy_disclosure_button),
                            subtitle = stringResource(id = R.string.offline_promise_badge),
                            testTag = "about_privacy_disclosure_button",
                            onClick = onOpenPrivacyDisclosure,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = tokens.borderBottomRight,
                        )
                        SettingsNavRow(
                            icon = Icons.Rounded.Replay,
                            iconTint = tokens.accentText,
                            title = stringResource(id = R.string.about_replay_onboarding_button),
                            subtitle = "Walk through permissions and focus setup",
                            testTag = "about_replay_onboarding_button",
                            onClick = onReplayOnboarding,
                        )
                    }
                }
            }

            // Glass Surface #2: Appearance + Local Backup & Restore
            item {
                GlassCard(
                    cornerRadius = 26.dp,
                    contentPadding = PaddingValues(18.dp),
                    modifier = Modifier.testTag("about_backup_card"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Appearance
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Text(
                                    text = stringResource(id = R.string.appearance_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val labelRes = when (mode) {
                                        ThemeMode.DARK -> R.string.theme_dark_navy
                                        ThemeMode.AMOLED -> R.string.theme_amoled
                                        ThemeMode.LIGHT -> R.string.theme_warm_light
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                    }
                                    val selected = prefs.themeMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            viewModel.setThemeMode(mode)
                                        },
                                        label = { Text(stringResource(id = labelRes)) },
                                        colors = glassFilterChipColors(),
                                        border = glassFilterChipBorder(selected),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("theme_chip_${mode.name.lowercase()}"),
                                    )
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.dynamic_color_title),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = stringResource(id = R.string.dynamic_color_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = prefs.dynamicColorEnabled,
                                        onCheckedChange = { checked ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            viewModel.setDynamicColorEnabled(checked)
                                        },
                                        colors = glassSwitchColors(),
                                        modifier = Modifier.testTag("dynamic_color_switch"),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = tokens.borderBottomRight)

                        // Local Backup & Restore
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    tint = tokens.accentText,
                                )
                                Text(
                                    text = stringResource(id = R.string.backup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }

                            Text(
                                text = stringResource(id = R.string.backup_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            GlassPrimaryButton(
                                onClick = {
                                    val fileName = "reborn-backup-${LocalDate.now()}.json"
                                    exportLauncher.launch(fileName)
                                },
                                enabled = backupStatus !is BackupUiStatus.Working,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_export_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.backup_export_button))
                            }

                            GlassButton(
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                enabled = backupStatus !is BackupUiStatus.Working,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_restore_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.backup_restore_button))
                            }

                            when (val status = backupStatus) {
                                BackupUiStatus.Idle, BackupUiStatus.Working -> Unit
                                is BackupUiStatus.Success -> {
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.statusHealthy,
                                        modifier = Modifier.testTag("backup_status_banner"),
                                    )
                                }
                                is BackupUiStatus.Error -> {
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.statusCritical,
                                        modifier = Modifier.testTag("backup_status_banner"),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.privacy_guarantee_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                indication = ripple(),
                interactionSource = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
