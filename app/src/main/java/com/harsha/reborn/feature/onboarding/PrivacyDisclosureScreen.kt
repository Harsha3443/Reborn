package com.harsha.reborn.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsha.reborn.ui.components.GlassCard
import com.harsha.reborn.ui.components.glassTopAppBarColors
import com.harsha.reborn.ui.theme.PhoenixAmber
import com.harsha.reborn.ui.theme.PhoenixOrange
import com.harsha.reborn.ui.theme.StatusHealthy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDisclosureScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Accessibility") },
                colors = glassTopAppBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("privacy_back_button"),
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DisclosureCard(
                    icon = Icons.Rounded.Lock,
                    iconTint = StatusHealthy,
                    title = "100% Offline by Architecture",
                    bullets = listOf(
                        "Reborn does NOT declare android.permission.INTERNET in its manifest.",
                        "Zero analytics SDKs, zero crash reporters, zero ads, and zero cloud accounts.",
                        "All selected apps, schedules, logs, and settings stay strictly inside your phone's local storage.",
                    ),
                )

                DisclosureCard(
                    icon = Icons.Rounded.Visibility,
                    iconTint = PhoenixOrange,
                    title = "What AccessibilityService Is Used For",
                    bullets = listOf(
                        "Detects foreground app transitions (TYPE_WINDOW_STATE_CHANGED) so Reborn knows when a blocked app opens.",
                        "In later opt-in features, checks visible browser URL bars or short-feed containers solely on-device when you enable keyword or feed blocking.",
                        "In Strict Mode (when explicitly turned on by you), prevents opening Reborn's own Force Stop / Accessibility disable screens until your timer ends.",
                    ),
                )

                DisclosureCard(
                    icon = Icons.Rounded.VisibilityOff,
                    iconTint = PhoenixAmber,
                    title = "What Reborn Never Collects or Reads",
                    bullets = listOf(
                        "Never reads passwords, PINs entered into other apps, banking fields, or private chats.",
                        "Never logs keystrokes or captures screenshots.",
                        "Never blocks Phone/Dialer, Emergency calls, or your Home launcher.",
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DisclosureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    bullets: List<String>,
) {
    GlassCard(
        cornerRadius = 26.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
