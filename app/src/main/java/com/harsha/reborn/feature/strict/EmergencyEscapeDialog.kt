package com.harsha.reborn.feature.strict

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsha.reborn.core.data.repository.StrictModeRepository
import com.harsha.reborn.ui.components.GlassAlertDialog
import com.harsha.reborn.ui.components.PhoenixProgressRing
import com.harsha.reborn.ui.components.glassOutlinedTextFieldColors
import com.harsha.reborn.ui.components.glassTextButtonColors
import com.harsha.reborn.ui.theme.LocalRebornGlassTokens
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EmergencyEscapeDialog(
    waitMinutes: Int,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmEscape: (String) -> Unit,
) {
    val totalWaitSeconds = (waitMinutes.coerceAtLeast(1) * 60)
    var remainingSeconds by remember(waitMinutes) { mutableIntStateOf(totalWaitSeconds) }
    var phraseInput by remember { mutableStateOf("") }
    val tokens = LocalRebornGlassTokens.current

    LaunchedEffect(waitMinutes) {
        remainingSeconds = totalWaitSeconds
        while (remainingSeconds > 0) {
            delay(1_000L)
            remainingSeconds -= 1
        }
    }

    val waitComplete = remainingSeconds <= 0
    val progress = 1f - (remainingSeconds.toFloat() / totalWaitSeconds.toFloat())
    val phraseMatches = phraseInput.trim() == StrictModeRepository.EMERGENCY_ESCAPE_PHRASE

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency Escape Hatch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Emergency escapes are deliberately slow and recorded in your Strict Mode log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!waitComplete) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(tokens.cardElevatedFill)
                            .border(1.dp, tokens.borderTopLeft, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PhoenixProgressRing(
                            progress = progress.coerceIn(0f, 1f),
                            size = 58.dp,
                            strokeWidth = 5.dp,
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%d:%02d",
                                    remainingSeconds / 60,
                                    remainingSeconds % 60,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.accentText,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Cool-down in progress",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Pause and decide whether ending your lock now is truly necessary.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Type this exact phrase to end Strict Mode:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(tokens.cardElevatedFill)
                            .border(1.dp, tokens.borderTopLeft, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = StrictModeRepository.EMERGENCY_ESCAPE_PHRASE,
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.accentText,
                        )
                    }
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        placeholder = { Text("Type confirmation phrase") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = glassOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_escape_phrase_input"),
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmEscape(phraseInput) },
                enabled = waitComplete && phraseMatches,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_emergency_escape_button"),
            ) {
                Text("End Strict Mode")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = glassTextButtonColors(),
                modifier = Modifier.testTag("cancel_emergency_escape_button"),
            ) {
                Text("Stay Locked")
            }
        },
    )
}
