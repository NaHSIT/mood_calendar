package com.example.mdd_calender.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF203759),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE8FF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF252832),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF2B2F3A),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF343B49),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF424957),
    primary = PrimaryAccentDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = TextPrimaryDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFEAF1FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF173E78),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFEDF5F3),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF28564D),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF5F7FB),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF0F3F8),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFEDF1F7),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE2E7EF),
    primary = PrimaryAccent,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = SurfaceLight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF667085)
)

@Composable
fun Mdd_calenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color by default for a consistent premium aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
