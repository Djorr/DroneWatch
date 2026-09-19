package com.dronewatch.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dronewatch.app.domain.model.ScannerState
import com.dronewatch.app.ui.MainViewModel
import com.dronewatch.app.ui.components.DroneCard
import com.dronewatch.app.ui.components.StatPill
import com.dronewatch.app.ui.components.StatusChip

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val home by viewModel.homeState.collectAsState()
    val userLoc = viewModel.getUserLocation()

    val statusColor = when (home.state) {
        ScannerState.SCANNING -> Color(0xFF00C853)
        ScannerState.DEMO -> Color(0xFFFF6D00)
        ScannerState.BLUETOOTH_DISABLED, ScannerState.PERMISSION_REQUIRED -> Color(0xFFFF9100)
        ScannerState.ERROR -> Color(0xFFFF5252)
        else -> Color(0xFF90A4AE)
    }
    val statusLabel = when (home.state) {
        ScannerState.SCANNING -> "Automatisch zoeken"
        ScannerState.DEMO -> "Demo mode"
        ScannerState.BLUETOOTH_DISABLED -> "Bluetooth uit"
        ScannerState.PERMISSION_REQUIRED -> "Permissie nodig"
        ScannerState.GPS_UNAVAILABLE -> "GPS onbeschikbaar"
        ScannerState.ERROR -> "Fout"
        ScannerState.INITIALIZING -> "Initialiseren…"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Top header band ──
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "DroneWatch",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Remote ID monitor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    StatusChip(
                        text = statusLabel.uppercase(),
                        color = statusColor,
                        pulsing = home.state == ScannerState.SCANNING || home.state == ScannerState.DEMO
                    )

                    if (home.isDemo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Virtueel – geen echte detecties",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF6D00)
                        )
                    }
                }
            }
        }

        // ── Summary stats ──
        item {
            val nearest = home.drones.firstOrNull()?.let { d ->
                userLoc?.let { d.distanceTo(it.first, it.second) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatPill(
                        label = "Drones",
                        value = "${home.drones.size}",
                        accent = MaterialTheme.colorScheme.primary
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatPill(
                        label = "Dichtstbij",
                        value = nearest?.let { formatDistance(it) } ?: "—",
                        accent = if (nearest != null && nearest < 500) Color(0xFFFF5252)
                        else MaterialTheme.colorScheme.secondary
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    val age = when {
                        home.lastScanAgeMs < 0 -> "—"
                        home.lastScanAgeMs < 1000 -> "<1s"
                        home.lastScanAgeMs < 60_000 -> "${home.lastScanAgeMs / 1000}s"
                        else -> "${home.lastScanAgeMs / 60_000}m"
                    }
                    StatPill(
                        label = "Laatste",
                        value = age,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Error banner ──
        item {
            AnimatedVisibility(
                visible = home.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                home.error?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ── Live list header ──
        item {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live drones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (home.state) {
                        ScannerState.SCANNING -> "Scannen actief"
                        ScannerState.DEMO -> "Demo"
                        else -> statusLabel
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Drone cards or empty state ──
        if (home.drones.isEmpty()) {
            item {
                EmptyScanState(state = home.state)
            }
        } else {
            items(home.drones, key = { it.id }) { drone ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    DroneCard(
                        drone = drone,
                        userLat = userLoc?.first,
                        userLon = userLoc?.second
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyScanState(state: ScannerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    ScannerState.BLUETOOTH_DISABLED -> Icons.Default.BluetoothDisabled
                    ScannerState.SCANNING, ScannerState.DEMO -> Icons.Default.Bluetooth
                    else -> Icons.Default.MyLocation
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when (state) {
                ScannerState.SCANNING -> "Zoeken naar drones…"
                ScannerState.DEMO -> "Demo wordt geladen…"
                ScannerState.BLUETOOTH_DISABLED -> "Zet Bluetooth aan"
                ScannerState.PERMISSION_REQUIRED -> "Geef permissies"
                else -> "Geen drones"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (state) {
                ScannerState.SCANNING ->
                    "Remote ID-signalen worden continu gescand.\nNieuwe drones verschijnen hier automatisch."
                ScannerState.BLUETOOTH_DISABLED ->
                    "Bluetooth is nodig om Remote ID-uitzendingen te ontvangen."
                else -> "Wachten op signalen of permissies."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    else -> String.format("%.1f km", meters / 1000.0)
}

fun formatSpeed(mps: Double?): String {
    if (mps == null) return "—"
    val kmh = mps * 3.6
    return String.format("%.0f km/u", kmh)
}
