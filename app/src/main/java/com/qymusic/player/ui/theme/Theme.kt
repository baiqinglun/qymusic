package com.qymusic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.qymusic.player.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Night,
    primaryContainer = AccentDark,
    onPrimaryContainer = Accent,
    background = Night,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightMuted,
    outline = NightBorder,
    error = androidx.compose.ui.graphics.Color(0xFFFF8A80),
)

private val LightColorScheme = lightColorScheme(
    primary = DayAccent,
    onPrimary = DaySurface,
    primaryContainer = DayAccentContainer,
    onPrimaryContainer = DayText,
    background = Day,
    onBackground = DayText,
    surface = DaySurface,
    onSurface = DayText,
    surfaceVariant = DaySurfaceHigh,
    onSurfaceVariant = DayMuted,
    outline = DayBorder,
)

@Composable
fun QYMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = QYTypography,
        content = content,
    )
}
