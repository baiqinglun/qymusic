package com.qymusic.player.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val KaraokeBackground = Color(0xFF1C1B1B)
internal val KaraokeSurface = Color(0xFF272626)
internal val KaraokeSurfaceRaised = Color(0xFF343232)
internal val KaraokeCoral = Color(0xFFFF9B8A)
internal val KaraokeDivider = Color(0xFF454242)
internal val KaraokeMuted = Color(0xFFAAA5A3)

internal val KaraokeEditorColorScheme = darkColorScheme(
    primary = KaraokeCoral,
    onPrimary = Color(0xFF3B0D08),
    primaryContainer = Color(0xFF5E2A24),
    onPrimaryContainer = Color(0xFFFFDAD3),
    background = KaraokeBackground,
    onBackground = Color(0xFFF8F3F1),
    surface = KaraokeSurface,
    onSurface = Color(0xFFF8F3F1),
    surfaceVariant = KaraokeSurfaceRaised,
    onSurfaceVariant = KaraokeMuted,
    outline = KaraokeDivider,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F150F),
)
