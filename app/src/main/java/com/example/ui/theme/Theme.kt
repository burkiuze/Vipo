package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VipoDarkColorScheme = darkColorScheme(
    primary = VipoAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF262840),
    onPrimaryContainer = Color(0xFFE0E2FE),
    secondary = VipoCyan,
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF0C384D),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = VipoGreen,
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF0E3D28),
    onTertiaryContainer = Color(0xFFA7F3D0),
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
    onError = Color.Black
)

@Composable
fun VipoTheme(
    pureBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (pureBlack) {
        VipoDarkColorScheme.copy(
            background = Color(0xFF050507),
            surface = Color(0xFF0C0D11)
        )
    } else {
        VipoDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
