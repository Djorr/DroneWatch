package com.dronewatch.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// DroneWatch brand – deep blue / cyan radar feel
private val BluePrimary = Color(0xFF1565C0)
private val BlueLight = Color(0xFF42A5F5)
private val CyanAccent = Color(0xFF00BCD4)
private val AlertOrange = Color(0xFFFF6D00)
private val SuccessGreen = Color(0xFF00C853)
private val SurfaceDark = Color(0xFF0B1220)
private val SurfaceDarkElevated = Color(0xFF151D2E)
private val SurfaceLight = Color(0xFFF5F7FA)
private val SurfaceLightCard = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = CyanAccent,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F56),
    onSecondaryContainer = Color(0xFFA6F0F8),
    tertiary = AlertOrange,
    onTertiary = Color.White,
    background = SurfaceDark,
    onBackground = Color(0xFFE8EAED),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Color(0xFFA8B3C4),
    outline = Color(0xFF3D4A5C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = AlertOrange,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = Color(0xFF1A1C1E),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = SurfaceLightCard,
    onSurfaceVariant = Color(0xFF5A6570),
    outline = Color(0xFFC5CDD6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun DroneWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // brand colors preferred
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
