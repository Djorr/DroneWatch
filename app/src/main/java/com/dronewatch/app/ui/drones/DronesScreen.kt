package com.dronewatch.app.ui.drones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronewatch.app.domain.model.DroneDetection
import com.dronewatch.app.ui.MainViewModel
import com.dronewatch.app.ui.components.DetailRow
import com.dronewatch.app.ui.components.DroneCard
import com.dronewatch.app.ui.components.SectionHeader
import com.dronewatch.app.ui.home.formatDistance
import com.dronewatch.app.ui.home.formatSpeed

enum class SortMode { DISTANCE, RSSI, LAST_UPDATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DronesScreen(viewModel: MainViewModel) {
    val drones by viewModel.liveDrones.collectAsState()
    val userLoc = viewModel.getUserLocation()
    var sortMode by remember { mutableStateOf(SortMode.DISTANCE) }
    var selected by remember { mutableStateOf<DroneDetection?>(null) }

    val sorted = remember(drones, sortMode, userLoc) {
        when (sortMode) {
            SortMode.DISTANCE -> drones.sortedBy {
                if (userLoc != null) it.distanceTo(userLoc.first, userLoc.second) ?: Double.MAX_VALUE
                else Double.MAX_VALUE
            }
            SortMode.RSSI -> drones.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            SortMode.LAST_UPDATE -> drones.sortedByDescending { it.timestamp }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Live drones",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (sorted.isEmpty()) "Geen actieve detecties"
                else "${sorted.size} actief · tik voor details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip("Afstand", sortMode == SortMode.DISTANCE) { sortMode = SortMode.DISTANCE }
                SortChip("Signaal", sortMode == SortMode.RSSI) { sortMode = SortMode.RSSI }
                SortChip("Update", sortMode == SortMode.LAST_UPDATE) { sortMode = SortMode.LAST_UPDATE }
            }
        }

        if (sorted.isEmpty()) {
            Text(
                "Geen drones in de live-lijst",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { drone ->
                    DroneCard(
                        drone = drone,
                        userLat = userLoc?.first,
                        userLon = userLoc?.second,
                        onClick = { selected = drone }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selected?.let { drone ->
        DroneDetailDialog(drone = drone, userLoc = userLoc, onDismiss = { selected = null })
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun DroneDetailDialog(
    drone: DroneDetection,
    userLoc: Pair<Double, Double>?,
    onDismiss: () -> Unit
) {
    val dist = if (userLoc != null) drone.distanceTo(userLoc.first, userLoc.second) else null
    val bearing = if (userLoc != null) drone.bearingFrom(userLoc.first, userLoc.second) else null
    val ageSec = (System.currentTimeMillis() - drone.timestamp) / 1000

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                drone.droneId ?: "Drone gedetecteerd",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                SectionHeader("Drone")
                DetailRow("ID", drone.droneId ?: "Niet beschikbaar")
                DetailRow("Afstand", dist?.let { formatDistance(it) } ?: "Niet beschikbaar")
                DetailRow("Hoogte", drone.altitudeMsl?.let { "${it.toInt()} m" } ?: "Niet beschikbaar")
                DetailRow("Snelheid", formatSpeed(drone.speedHorizontal))
                DetailRow("Richting", drone.heading?.let { "${it.toInt()}°" } ?: "Niet beschikbaar")
                DetailRow("Bearing", bearing?.let { "${it.toInt()}°" } ?: "Niet beschikbaar")
                DetailRow("RSSI", drone.rssi?.let { "$it dBm" } ?: "Niet beschikbaar")
                DetailRow("Update", "$ageSec sec geleden")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader("Positie")
                DetailRow("Latitude", drone.latitude?.let { "%.6f".format(it) } ?: "Niet beschikbaar")
                DetailRow("Longitude", drone.longitude?.let { "%.6f".format(it) } ?: "Niet beschikbaar")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader("Remote ID")
                DetailRow("Types", drone.messageTypes.joinToString { it.name }.ifEmpty { "—" })
                DetailRow("Transport", drone.transportType.name)
                DetailRow("Bron", if (drone.isDemo) "Demo" else "Remote ID broadcast")
                drone.description?.let { DetailRow("Info", it) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Sluiten")
            }
        }
    )
}
