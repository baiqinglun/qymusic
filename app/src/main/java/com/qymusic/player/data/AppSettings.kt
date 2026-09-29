package com.qymusic.player.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LyricAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

enum class LyricWordAnimationStyle {
    HIGHLIGHT,
    STAR,
}

enum class LaunchScanTiming {
    DURING_STARTUP,
    AFTER_STARTUP,
}

data class PlaybackMemory(
    val trackId: String,
    val positionMs: Long,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 播放页背景是否随音乐节奏缓慢呼吸、移动。 */
    val musicReactiveBackground: Boolean = false,
    val pinnedArtists: Set<String> = emptySet(),
    val pinnedAlbums: Set<String> = emptySet(),
    val pinnedPlaylistIds: Set<Long> = emptySet(),
    val lyricAlignment: LyricAlignment = LyricAlignment.CENTER,
    val lyricFontScale: Float = 1f,
    val lyricBold: Boolean = false,
    val lyricInactiveBlurDp: Float = 0f,
    /** 是否让正在播放的歌词行固定在视口中央。 */
    val lyricCenterStartEnd: Boolean = false,
    /** 逐字歌词的动画样式。 */
    val lyricWordAnimationStyle: LyricWordAnimationStyle =
        LyricWordAnimationStyle.HIGHLIGHT,
    /** 播放速度倍率，重启后仍然生效。 */
    val playbackSpeed: Float = 1f,
    /** 变调半音数，重启后仍然生效。 */
    val playbackPitchSemitones: Float = 0f,
    /** 暂停 / 播放时是否平滑改变音量，避免突然截断或爆响。 */
    val playbackFadeEnabled: Boolean = true,
    /** 打开应用后是否自动继续上次的歌曲。 */
    val autoPlayOnLaunch: Boolean = false,
    /** 打开应用时是否自动重扫已配置的音乐目录。 */
    val rescanOnLaunch: Boolean = false,
    /** 开启启动扫描时，扫描是在启动页期间还是进入曲库后进行。 */
    val launchScanTiming: LaunchScanTiming = LaunchScanTiming.DURING_STARTUP,
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setMusicReactiveBackground(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_MUSIC_REACTIVE_BACKGROUND, enabled) }
        _settings.update { it.copy(musicReactiveBackground = enabled) }
    }

    fun setArtistPinned(name: String, pinned: Boolean) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val updated = if (pinned) {
            _settings.value.pinnedArtists + normalized
        } else {
            _settings.value.pinnedArtists - normalized
        }
        preferences.edit { putStringSet(KEY_PINNED_ARTISTS, updated) }
        _settings.update { it.copy(pinnedArtists = updated) }
    }

    fun setAlbumPinned(name: String, pinned: Boolean) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val updated = if (pinned) {
            _settings.value.pinnedAlbums + normalized
        } else {
            _settings.value.pinnedAlbums - normalized
        }
        preferences.edit { putStringSet(KEY_PINNED_ALBUMS, updated) }
        _settings.update { it.copy(pinnedAlbums = updated) }
    }

    fun setPlaylistPinned(playlistId: Long, pinned: Boolean) {
        val updated = if (pinned) {
            _settings.value.pinnedPlaylistIds + playlistId
        } else {
            _settings.value.pinnedPlaylistIds - playlistId
        }
        preferences.edit {
            putStringSet(
                KEY_PINNED_PLAYLISTS,
                updated.map(Long::toString).toSet(),
            )
        }
        _settings.update { it.copy(pinnedPlaylistIds = updated) }
    }

    fun setLyricAlignment(alignment: LyricAlignment) {
        preferences.edit { putString(KEY_LYRIC_ALIGNMENT, alignment.name) }
        _settings.update { it.copy(lyricAlignment = alignment) }
    }

    fun setLyricFontScale(scale: Float) {
        val normalized = scale.coerceIn(MIN_LYRIC_FONT_SCALE, MAX_LYRIC_FONT_SCALE)
        preferences.edit { putFloat(KEY_LYRIC_FONT_SCALE, normalized) }
        _settings.update { it.copy(lyricFontScale = normalized) }
    }

    fun setLyricBold(bold: Boolean) {
        preferences.edit { putBoolean(KEY_LYRIC_BOLD, bold) }
        _settings.update { it.copy(lyricBold = bold) }
    }

    fun setLyricInactiveBlur(blurDp: Float) {
        val normalized = blurDp.coerceIn(
            MIN_LYRIC_INACTIVE_BLUR_DP,
            MAX_LYRIC_INACTIVE_BLUR_DP,
        )
        preferences.edit { putFloat(KEY_LYRIC_INACTIVE_BLUR_DP, normalized) }
        _settings.update { it.copy(lyricInactiveBlurDp = normalized) }
    }

    fun setLyricCenterStartEnd(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_LYRIC_CENTER_START_END, enabled) }
        _settings.update { it.copy(lyricCenterStartEnd = enabled) }
    }

    fun setLyricWordAnimationStyle(style: LyricWordAnimationStyle) {
        preferences.edit { putString(KEY_LYRIC_WORD_ANIMATION_STYLE, style.name) }
        _settings.update { it.copy(lyricWordAnimationStyle = style) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val normalized = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        preferences.edit { putFloat(KEY_PLAYBACK_SPEED, normalized) }
        _settings.update { it.copy(playbackSpeed = normalized) }
    }

    fun setPlaybackPitchSemitones(semitones: Float) {
        val normalized = semitones.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        preferences.edit { putFloat(KEY_PLAYBACK_PITCH_SEMITONES, normalized) }
        _settings.update { it.copy(playbackPitchSemitones = normalized) }
    }

    fun setPlaybackFadeEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_PLAYBACK_FADE_ENABLED, enabled) }
        _settings.update { it.copy(playbackFadeEnabled = enabled) }
    }

    fun setAutoPlayOnLaunch(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_PLAY_ON_LAUNCH, enabled) }
        _settings.update { it.copy(autoPlayOnLaunch = enabled) }
    }

    fun setRescanOnLaunch(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_RESCAN_ON_LAUNCH, enabled) }
        _settings.update { it.copy(rescanOnLaunch = enabled) }
    }

    fun setLaunchScanTiming(timing: LaunchScanTiming) {
        preferences.edit { putString(KEY_LAUNCH_SCAN_TIMING, timing.name) }
        _settings.update { it.copy(launchScanTiming = timing) }
    }

    fun loadLastPlayback(): PlaybackMemory? {
        val trackId = preferences.getString(KEY_LAST_PLAYBACK_TRACK_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return PlaybackMemory(
            trackId = trackId,
            positionMs = preferences.getLong(KEY_LAST_PLAYBACK_POSITION_MS, 0L)
                .coerceAtLeast(0L),
        )
    }

    fun saveLastPlayback(trackId: String, positionMs: Long) {
        if (trackId.isBlank()) return
        preferences.edit {
            putString(KEY_LAST_PLAYBACK_TRACK_ID, trackId)
            putLong(KEY_LAST_PLAYBACK_POSITION_MS, positionMs.coerceAtLeast(0L))
        }
    }

    private fun loadSettings(): AppSettings = AppSettings(
        themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        musicReactiveBackground = preferences.getBoolean(KEY_MUSIC_REACTIVE_BACKGROUND, false),
        pinnedArtists = preferences.getStringSet(KEY_PINNED_ARTISTS, emptySet())
            ?.toSet()
            .orEmpty(),
        pinnedAlbums = preferences.getStringSet(KEY_PINNED_ALBUMS, emptySet())
            ?.toSet()
            .orEmpty(),
        pinnedPlaylistIds = preferences.getStringSet(KEY_PINNED_PLAYLISTS, emptySet())
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            .orEmpty(),
        lyricAlignment = preferences.getString(KEY_LYRIC_ALIGNMENT, null)
            ?.let { runCatching { LyricAlignment.valueOf(it) }.getOrNull() }
            ?: LyricAlignment.CENTER,
        lyricFontScale = preferences.getFloat(KEY_LYRIC_FONT_SCALE, 1f)
            .coerceIn(MIN_LYRIC_FONT_SCALE, MAX_LYRIC_FONT_SCALE),
        lyricBold = preferences.getBoolean(KEY_LYRIC_BOLD, false),
        lyricInactiveBlurDp = preferences.getFloat(KEY_LYRIC_INACTIVE_BLUR_DP, 0f)
            .coerceIn(MIN_LYRIC_INACTIVE_BLUR_DP, MAX_LYRIC_INACTIVE_BLUR_DP),
        lyricCenterStartEnd = preferences.getBoolean(KEY_LYRIC_CENTER_START_END, false),
        lyricWordAnimationStyle = preferences.getString(
            KEY_LYRIC_WORD_ANIMATION_STYLE,
            null,
        )
            ?.let { runCatching { LyricWordAnimationStyle.valueOf(it) }.getOrNull() }
            ?: LyricWordAnimationStyle.HIGHLIGHT,
        playbackSpeed = preferences.getFloat(KEY_PLAYBACK_SPEED, 1f)
            .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED),
        playbackPitchSemitones = preferences.getFloat(KEY_PLAYBACK_PITCH_SEMITONES, 0f)
            .coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES),
        playbackFadeEnabled = preferences.getBoolean(KEY_PLAYBACK_FADE_ENABLED, true),
        autoPlayOnLaunch = preferences.getBoolean(KEY_AUTO_PLAY_ON_LAUNCH, false),
        rescanOnLaunch = preferences.getBoolean(KEY_RESCAN_ON_LAUNCH, false),
        launchScanTiming = preferences.getString(KEY_LAUNCH_SCAN_TIMING, null)
            ?.let { runCatching { LaunchScanTiming.valueOf(it) }.getOrNull() }
            ?: LaunchScanTiming.DURING_STARTUP,
    )

    companion object {
        const val MIN_LYRIC_FONT_SCALE = 0.8f
        const val MAX_LYRIC_FONT_SCALE = 2.0f
        const val MIN_LYRIC_INACTIVE_BLUR_DP = 0f
        const val MAX_LYRIC_INACTIVE_BLUR_DP = 8f
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 2.0f
        const val MIN_PITCH_SEMITONES = -12f
        const val MAX_PITCH_SEMITONES = 12f

        private const val PREFERENCES_NAME = "qy_music_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MUSIC_REACTIVE_BACKGROUND = "music_reactive_background"
        private const val KEY_PINNED_ARTISTS = "pinned_artists"
        private const val KEY_PINNED_ALBUMS = "pinned_albums"
        private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"
        private const val KEY_LYRIC_ALIGNMENT = "lyric_alignment"
        private const val KEY_LYRIC_FONT_SCALE = "lyric_font_scale"
        private const val KEY_LYRIC_BOLD = "lyric_bold"
        private const val KEY_LYRIC_INACTIVE_BLUR_DP = "lyric_inactive_blur_dp"
        private const val KEY_LYRIC_CENTER_START_END = "lyric_center_start_end"
        private const val KEY_LYRIC_WORD_ANIMATION_STYLE = "lyric_word_animation_style"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_PLAYBACK_PITCH_SEMITONES = "playback_pitch_semitones"
        private const val KEY_PLAYBACK_FADE_ENABLED = "playback_fade_enabled"
        private const val KEY_AUTO_PLAY_ON_LAUNCH = "auto_play_on_launch"
        private const val KEY_RESCAN_ON_LAUNCH = "rescan_on_launch"
        private const val KEY_LAUNCH_SCAN_TIMING = "launch_scan_timing"
        private const val KEY_LAST_PLAYBACK_TRACK_ID = "last_playback_track_id"
        private const val KEY_LAST_PLAYBACK_POSITION_MS = "last_playback_position_ms"
    }
}
