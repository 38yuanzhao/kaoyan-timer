package com.kaoyan.timer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ColorBg = Color(0xFF0F1115)
val ColorCard = Color(0xFF1A1D24)
val ColorCard2 = Color(0xFF22262F)
val ColorFg = Color(0xFFE8EAED)
val ColorMuted = Color(0xFF8B90A0)
val ColorAccent = Color(0xFFFF6B5E)
val ColorAccent2 = Color(0xFF4F8CFF)
val ColorGood = Color(0xFF3ECF8E)
val ColorLine = Color(0xFF2A2F3A)

private val KaoyanColorScheme = darkColorScheme(
    background = ColorBg,
    surface = ColorCard,
    surfaceVariant = ColorCard2,
    primary = ColorGood,
    secondary = ColorAccent2,
    onPrimary = ColorBg,
    onBackground = ColorFg,
    onSurface = ColorFg,
    onSurfaceVariant = ColorMuted,
    outline = ColorLine,
    error = ColorAccent
)

@Composable
fun KaoyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaoyanColorScheme,
        content = content
    )
}
