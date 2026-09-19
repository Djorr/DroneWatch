package com.dronewatch.app.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronewatch.app.ui.MainViewModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RadarScreen(viewModel: MainViewModel) {
    val drones by viewModel.liveDrones.collectAsState()
    val userLoc = viewModel.getUserLocation()
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.background
    val onSurface = MaterialTheme.colorScheme.onSurface
    val variant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Radar",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Jij in het midden · ringen tot 2 km",
                style = MaterialTheme.typography.bodySmall,
                color = variant
            )
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("250 m", "500 m", "1 km", "2 km").forEachIndexed { i, label ->
                if (i > 0) Spacer(Modifier.weight(1f))
                Text(label, style = MaterialTheme.typography.labelSmall, color = variant)
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.06f),
                            surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val maxR = min(cx, cy) * 0.92f

                val ranges = listOf(250f, 500f, 1000f, 2000f)
                ranges.forEachIndexed { idx, range ->
                    val r = (range / 2000f) * maxR
                    drawCircle(
                        color = primary.copy(alpha = 0.18f + idx * 0.04f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Cross
                drawLine(
                    primary.copy(alpha = 0.25f),
                    Offset(cx - maxR, cy), Offset(cx + maxR, cy),
                    strokeWidth = 1f
                )
                drawLine(
                    primary.copy(alpha = 0.25f),
                    Offset(cx, cy - maxR), Offset(cx, cy + maxR),
                    strokeWidth = 1f
                )

                // User center
                drawCircle(color = primary.copy(alpha = 0.2f), radius = 18f, center = Offset(cx, cy))
                drawCircle(color = primary, radius = 8f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 3f, center = Offset(cx, cy))

                if (userLoc != null) {
                    drones.forEach { drone ->
                        val dist = drone.distanceTo(userLoc.first, userLoc.second) ?: return@forEach
                        if (dist > 2000) return@forEach
                        val bearing = drone.bearingFrom(userLoc.first, userLoc.second) ?: return@forEach
                        val angleRad = Math.toRadians(bearing - 90.0)
                        val r = ((dist / 2000.0) * maxR).toFloat()
                        val pos = Offset(
                            cx + (r * cos(angleRad)).toFloat(),
                            cy + (r * sin(angleRad)).toFloat()
                        )
                        val droneColor = if (drone.isDemo) Color(0xFFFF6D00) else Color(0xFFFF1744)
                        // glow
                        drawCircle(color = droneColor.copy(alpha = 0.25f), radius = 16f, center = pos)
                        drawCircle(color = droneColor, radius = 9f, center = pos)
                        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 3f, center = pos)
                    }
                }
            }

            when {
                userLoc == null -> {
                    Text(
                        "Wachten op GPS…",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    )
                }
                drones.isEmpty() -> {
                    Text(
                        "Geen drones in bereik",
                        color = variant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    )
                }
            }
        }
    }
}
