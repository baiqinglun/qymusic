package com.qymusic.player.ui.theme

import androidx.compose.ui.graphics.Color
import com.qymusic.player.data.ThemeColor

val Night = Color(0xFF12161B)
val NightSurface = Color(0xFF1A1F25)
val NightSurfaceHigh = Color(0xFF232930)
val NightBorder = Color(0xFF343D46)
val NightText = Color(0xFFF4F7F8)
val NightMuted = Color(0xFFAAB3BD)
val Accent = Color(0xFF64D9A5)
val AccentDark = Color(0xFF173F31)

val Day = Color(0xFFF5F7F6)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceHigh = Color(0xFFE9EFEC)
val DayBorder = Color(0xFFD3DCD7)
val DayText = Color(0xFF121A16)
val DayMuted = Color(0xFF56635C)
val DayAccent = Color(0xFF0C6B47)
val DayAccentContainer = Color(0xFFD6F3E4)

internal data class ThemeAccentPalette(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
)

internal fun ThemeColor.accentPalette(): ThemeAccentPalette = when (this) {
    ThemeColor.MINT -> ThemeAccentPalette(
        darkPrimary = Accent,
        darkOnPrimary = Color(0xFF062117),
        darkPrimaryContainer = AccentDark,
        darkOnPrimaryContainer = Color(0xFFA7F3D0),
        lightPrimary = DayAccent,
        lightOnPrimary = Color.White,
        lightPrimaryContainer = DayAccentContainer,
        lightOnPrimaryContainer = Color(0xFF06442C),
    )

    ThemeColor.BLUE -> ThemeAccentPalette(
        darkPrimary = Color(0xFF70C7FF),
        darkOnPrimary = Color(0xFF00243A),
        darkPrimaryContainer = Color(0xFF143C52),
        darkOnPrimaryContainer = Color(0xFFC6E7FF),
        lightPrimary = Color(0xFF0A628C),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFCDE9F8),
        lightOnPrimaryContainer = Color(0xFF00344E),
    )

    ThemeColor.PURPLE -> ThemeAccentPalette(
        darkPrimary = Color(0xFFC7A8FF),
        darkOnPrimary = Color(0xFF2B0B5E),
        darkPrimaryContainer = Color(0xFF46306A),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
        lightPrimary = Color(0xFF6D4CA8),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF28104E),
    )

    ThemeColor.AMBER -> ThemeAccentPalette(
        darkPrimary = Color(0xFFFFC16B),
        darkOnPrimary = Color(0xFF3A1F00),
        darkPrimaryContainer = Color(0xFF5A3A08),
        darkOnPrimaryContainer = Color(0xFFFFDDB5),
        lightPrimary = Color(0xFF8A4F00),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFE0B2),
        lightOnPrimaryContainer = Color(0xFF2A1700),
    )

    ThemeColor.CORAL -> ThemeAccentPalette(
        darkPrimary = Color(0xFFFF9B8A),
        darkOnPrimary = Color(0xFF3B0D08),
        darkPrimaryContainer = Color(0xFF5E2A24),
        darkOnPrimaryContainer = Color(0xFFFFDAD3),
        lightPrimary = Color(0xFFA33A28),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFDAD3),
        lightOnPrimaryContainer = Color(0xFF3B0D08),
    )

    ThemeColor.ROSE -> ThemeAccentPalette(
        darkPrimary = Color(0xFFFF9CCB),
        darkOnPrimary = Color(0xFF4A002B),
        darkPrimaryContainer = Color(0xFF5D2342),
        darkOnPrimaryContainer = Color(0xFFFFD9E8),
        lightPrimary = Color(0xFFA13368),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFD9E8),
        lightOnPrimaryContainer = Color(0xFF3E0024),
    )
}

internal fun ThemeColor.previewColor(): Color = accentPalette().darkPrimary

internal fun ThemeColor.previewContentColor(): Color = accentPalette().darkOnPrimary
