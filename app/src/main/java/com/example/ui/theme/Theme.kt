package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VipoDarkColorScheme = darkColorScheme(
    primary = VipoInk,
    onPrimary = Color(0xFF0B0B0C),
    primaryContainer = VipoSurfaceContainer,
    onPrimaryContainer = VipoTextPrimary,
    secondary = VipoTextSecondary,
    onSecondary = VipoBackground,
    secondaryContainer = VipoSurfaceVariant,
    onSecondaryContainer = VipoTextPrimary,
    tertiary = VipoAccent,
    onTertiary = Color(0xFF0B0B0C),
    tertiaryContainer = VipoSurfaceVariant,
    onTertiaryContainer = VipoTextPrimary,
    background = VipoBackground,
    onBackground = VipoTextPrimary,
    surface = VipoSurface,
    onSurface = VipoTextPrimary,
    surfaceVariant = VipoSurfaceVariant,
    onSurfaceVariant = VipoTextSecondary,
    surfaceContainer = VipoSurfaceContainer,
    outline = VipoBorder,
    outlineVariant = VipoBorderSubtle,
    error = VipoRed,
    onError = Color(0xFF0B0B0C),
    errorContainer = Color(0xFF241718),
    onErrorContainer = VipoRed
)

@Composable
fun VipoTheme(
    pureBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (pureBlack) {
        VipoDarkColorScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF0A0A0B),
            surfaceVariant = Color(0xFF121214),
            surfaceContainer = Color(0xFF161618)
        )
    } else {
        VipoDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VipoShapes,
        content = content
    )
}
