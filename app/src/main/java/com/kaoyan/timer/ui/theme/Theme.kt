package com.kaoyan.timer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KaoyanColorScheme = darkColorScheme(
    background = Bg,
    surface = Card,
    surfaceVariant = Card2,
    primary = Good,
    onPrimary = Bg,
    secondary = Accent2,
    onSecondary = Bg,
    tertiary = Accent,
    onTertiary = Bg,
    onBackground = Fg,
    onSurface = Fg,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Accent
)

@Composable
fun KaoyanTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KaoyanColorScheme,
        typography = Typography,
        content = content
    )
}
