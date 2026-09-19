package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VipoDarkColorScheme = darkColorScheme(
    primary = VipoInk,
    onPrimary = Color(0xFF0A0A0B),
    primaryContainer = VipoSurfaceContainer,
    onPrimaryContainer = VipoTextPrimary,
    secondary = VipoTextSecondary,
    onSecondary = VipoBackground,
    secondaryContainer = VipoSurfaceVariant,
    onSecondaryContainer = VipoTextPrimary,
    tertiary = VipoAccent,
    onTertiary = Color(0xFF0A0A0B),
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
    onError = Color(0xFF0A0A0B),
    errorContainer = Color(0xFF2A1A1B),
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
            surface = Color(0xFF101013),
            surfaceVariant = Color(0xFF17171B),
            surfaceContainer = Color(0xFF1D1D22)
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
