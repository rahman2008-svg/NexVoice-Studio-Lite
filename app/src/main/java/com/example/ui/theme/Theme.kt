package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable


private val CustomDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = DeepPurpleOnPrimary,
    secondary = MagneticViolet,
    onSecondary = TextPrimary,
    tertiary = ElectronicPink,
    onTertiary = DeepPurpleOnPrimary,
    background = ObsidianBack,
    onBackground = TextPrimary,
    surface = DeepSlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkNavyCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force modern dark UI
    dynamicColor: Boolean = false, // Enforce brand-identity colors
    content: @Composable () -> Unit,
) {
    // Custom dark schema is always served here to satisfy the dark UI requirements
    val colorScheme = CustomDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

