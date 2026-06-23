package ru.sapn.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Строгая тёмная палитра в духе бренда SAPN.
private val Accent = Color(0xFF3D7BFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0B0D12),
    surface = Color(0xFF12151C),
    onPrimary = Color.White,
    onBackground = Color(0xFFE6E8EC),
    onSurface = Color(0xFFE6E8EC),
)

private val LightColors = lightColorScheme(
    primary = Accent,
)

@Composable
fun SapnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
