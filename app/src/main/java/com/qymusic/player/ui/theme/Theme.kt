package com.qymusic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.qymusic.player.data.ThemeColor
import com.qymusic.player.data.ThemeMode

private fun createDarkColorScheme(themeColor: ThemeColor) = darkColorScheme(
    primary = themeColor.accentPalette().darkPrimary,
    onPrimary = themeColor.accentPalette().darkOnPrimary,
    primaryContainer = themeColor.accentPalette().darkPrimaryContainer,
    onPrimaryContainer = themeColor.accentPalette().darkOnPrimaryContainer,
    background = Night,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightMuted,
    outline = NightBorder,
    error = androidx.compose.ui.graphics.Color(0xFFFF8A80),
)

private fun createLightColorScheme(themeColor: ThemeColor) = lightColorScheme(
    primary = themeColor.accentPalette().lightPrimary,
    onPrimary = themeColor.accentPalette().lightOnPrimary,
    primaryContainer = themeColor.accentPalette().lightPrimaryContainer,
    onPrimaryContainer = themeColor.accentPalette().lightOnPrimaryContainer,
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
    themeColor: ThemeColor = ThemeColor.MINT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) {
            createDarkColorScheme(themeColor)
        } else {
            createLightColorScheme(themeColor)
        },
        typography = QYTypography,
        content = content,
    )
}
