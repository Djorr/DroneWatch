package com.dronewatch.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronewatch.app.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val adCount by viewModel.advertisementCount.collectAsState()
    val decoded by viewModel.decodedMessageCount.collectAsState()
    val state by viewModel.scannerState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Instellingen",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Scannen, waarschuwingen & privacy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        SettingsCard {
            SettingSwitch(
                title = "Continu scannen",
                subtitle = "Automatisch zoeken bij openen van de app",
                checked = settings.continuousScanningEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(continuousScanningEnabled = enabled) }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingSwitch(
                title = "Demo mode",
                subtitle = "Virtuele drones om de UI te testen",
                checked = settings.demoModeEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(demoModeEnabled = enabled) }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingSwitch(
                title = "Waarschuwingen",
                subtitle = "Notificatie bij drone binnen bereik",
                checked = settings.alertEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(alertEnabled = enabled) }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard {
            Text(
                "Waarschuwingsafstand: ${settings.alertDistanceMeters} m",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = settings.alertDistanceMeters.toFloat(),
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(alertDistanceMeters = v.toInt()) }
                },
                valueRange = 100f..2000f,
                steps = 18
            )
            Text(
                "Stale timeout: ${settings.staleTimeoutSeconds} s",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = settings.staleTimeoutSeconds.toFloat(),
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(staleTimeoutSeconds = v.toInt()) }
                },
                valueRange = 5f..30f,
                steps = 24
            )
            Text(
                "Lost timeout: ${settings.lostTimeoutSeconds} s",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = settings.lostTimeoutSeconds.toFloat(),
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(lostTimeoutSeconds = v.toInt()) }
                },
                valueRange = 15f..120f,
                steps = 20
            )
            SettingSwitch(
                title = "Batterijbesparing",
                subtitle = "Lagere scanfrequentie",
                checked = settings.batterySaverMode,
                onCheckedChange = { enabled ->
                    viewModel.updateSettings { it.copy(batterySaverMode = enabled) }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard {
            Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Remote ID-data wordt standaard alleen lokaal opgeslagen en niet naar een server gestuurd. " +
                        "Alleen noodzakelijke permissies (Bluetooth, locatie) worden gevraagd.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard {
            Text("Debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Status: $state", style = MaterialTheme.typography.bodyMedium)
            Text("Advertisements: $adCount", style = MaterialTheme.typography.bodyMedium)
            Text("Gedecodeerd: $decoded", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val log = viewModel.exportDebugLog()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("DroneWatch Debug", log))
                    Toast.makeText(context, "Debug log gekopieerd", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export debug log")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Detectie hangt af van drone, firmware, Remote ID, telefoonhardware, afstand, obstakels en interferentie. Geen vaste maximale afstand gegarandeerd.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
