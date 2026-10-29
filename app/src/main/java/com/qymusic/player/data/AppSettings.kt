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

enum class ThemeColor {
    MINT,
    BLUE,
    PURPLE,
    AMBER,
    CORAL,
    ROSE,
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

/**
 * 播放页同级功能：开启的以按钮形式直接展示，
 * 未开启的收进播放页右上角「⋮」展开菜单。
 */
enum class PlayerQuickAction {
    LYRIC_SETTINGS,
    BACKGROUND_SETTINGS,
    SLEEP_TIMER,
    MAGIC,
    CLIPS,
    DEFINE_CLIP,
    PLAYBACK_MODE,
    QUEUE,
    ADD_TO_PLAYLIST,
    EQUALIZER,
    KARAOKE,
}

/** 默认开启：播放控制类 5 个，页面功能类（含定义片段）在设置里自行开启。 */
val DEFAULT_PLAYER_QUICK_ACTIONS: List<PlayerQuickAction> = listOf(
    PlayerQuickAction.PLAYBACK_MODE,
    PlayerQuickAction.QUEUE,
    PlayerQuickAction.ADD_TO_PLAYLIST,
    PlayerQuickAction.EQUALIZER,
    PlayerQuickAction.KARAOKE,
)

enum class LaunchScanTiming {
    DURING_STARTUP,
    AFTER_STARTUP,
}

/** 集合页（艺术家 / 专辑 / 歌单 / 文件夹）的展示形式。 */
enum class CollectionViewMode {
    LIST,
    GRID,
}

/** 下载文件的命名格式。 */
enum class DownloadNamingFormat(val label: String) {
    TITLE_ARTIST("标题 - 歌手"),
    ARTIST_TITLE("歌手 - 标题"),
    TITLE("标题"),
}

data class PlaybackMemory(
    val trackId: String,
    val positionMs: Long,
)

/** 下载完成后的标签嵌入行为。 */
data class DownloadEmbedOptions(
    val writeMetadata: Boolean = true,
    val embedLyrics: Boolean = true,
    val embedCover: Boolean = true,
) {
    val anyEnabled: Boolean get() = writeMetadata || embedLyrics || embedCover
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.MINT,
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
    /** 播放页底部快捷按钮的显示与顺序。 */
    val playerQuickActions: List<PlayerQuickAction> = DEFAULT_PLAYER_QUICK_ACTIONS,
    /** 播放速度倍率，重启后仍然生效。 */
    val playbackSpeed: Float = 1f,
    /** 变调半音数，重启后仍然生效。 */
    val playbackPitchSemitones: Float = 0f,
    /** 暂停 / 播放时是否平滑改变音量，避免突然截断或爆响。 */
    val playbackFadeEnabled: Boolean = true,
    /** 打开应用后是否自动继续上次的歌曲。 */
    val autoPlayOnLaunch: Boolean = false,
    /** K 歌发布后是否保留当前草稿。 */
    val keepKaraokeDraftAfterPublish: Boolean = true,
    /** K 歌生成文件的目录；为空时使用系统 Music/QYMusic。 */
    val karaokeOutputTreeUri: String? = null,
    /** 音乐下载目录；为空时回退到系统 Download。 */
    val downloadDirTreeUri: String? = null,
    /** 下载文件命名格式。 */
    val downloadNamingFormat: DownloadNamingFormat = DownloadNamingFormat.TITLE_ARTIST,
    /** 抓取歌词偏好：true 优先逐字歌词，false 优先普通（仅行级时间轴）歌词。 */
    val lyricsFetchPreferWordLevel: Boolean = true,
    /** 下载完成后是否把标题 / 歌手 / 专辑写入音频标签。 */
    val downloadWriteMetadata: Boolean = true,
    /** 下载完成后是否通过元数据插件抓取并嵌入歌词。 */
    val downloadEmbedLyrics: Boolean = true,
    /** 下载完成后是否通过元数据插件抓取并嵌入封面。 */
    val downloadEmbedCover: Boolean = true,
    /** 打开应用时是否自动重扫已配置的音乐目录。 */
    val rescanOnLaunch: Boolean = false,
    /** 开启启动扫描时，扫描是在启动页期间还是进入曲库后进行。 */
    val launchScanTiming: LaunchScanTiming = LaunchScanTiming.DURING_STARTUP,
    /** 艺术家页的展示形式（列表 / 网格）。 */
    val artistViewMode: CollectionViewMode = CollectionViewMode.LIST,
    /** 专辑页的展示形式（列表 / 网格）。 */
    val albumViewMode: CollectionViewMode = CollectionViewMode.GRID,
    /** 歌单页的展示形式（列表 / 网格）。 */
    val playlistViewMode: CollectionViewMode = CollectionViewMode.GRID,
    /** 文件夹页的展示形式（列表 / 网格）。 */
    val foldersViewMode: CollectionViewMode = CollectionViewMode.LIST,
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setThemeColor(color: ThemeColor) {
        preferences.edit { putString(KEY_THEME_COLOR, color.name) }
        _settings.update { it.copy(themeColor = color) }
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

    fun setPlayerQuickActions(actions: List<PlayerQuickAction>) {
        val ordered = actions.distinct().ifEmpty { DEFAULT_PLAYER_QUICK_ACTIONS }
        preferences.edit {
            putString(
                KEY_PLAYER_QUICK_ACTIONS,
                ordered.joinToString(separator = ",") { it.name },
            )
        }
        _settings.update { it.copy(playerQuickActions = ordered) }
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

    fun setKeepKaraokeDraftAfterPublish(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_KEEP_KARAOKE_DRAFT_AFTER_PUBLISH, enabled) }
        _settings.update { it.copy(keepKaraokeDraftAfterPublish = enabled) }
    }

    fun setKaraokeOutputTreeUri(uri: String?) {
        preferences.edit {
            if (uri.isNullOrBlank()) {
                remove(KEY_KARAOKE_OUTPUT_TREE_URI)
            } else {
                putString(KEY_KARAOKE_OUTPUT_TREE_URI, uri)
            }
        }
        _settings.update { it.copy(karaokeOutputTreeUri = uri?.takeIf(String::isNotBlank)) }
    }

    fun setDownloadDirTreeUri(uri: String?) {
        preferences.edit {
            if (uri.isNullOrBlank()) {
                remove(KEY_DOWNLOAD_DIR_TREE_URI)
            } else {
                putString(KEY_DOWNLOAD_DIR_TREE_URI, uri)
            }
        }
        _settings.update { it.copy(downloadDirTreeUri = uri?.takeIf(String::isNotBlank)) }
    }

    fun setDownloadNamingFormat(format: DownloadNamingFormat) {
        preferences.edit { putString(KEY_DOWNLOAD_NAMING_FORMAT, format.name) }
        _settings.update { it.copy(downloadNamingFormat = format) }
    }

    fun setLyricsFetchPreferWordLevel(preferWordLevel: Boolean) {
        preferences.edit { putBoolean(KEY_LYRICS_FETCH_PREFER_WORD_LEVEL, preferWordLevel) }
        _settings.update { it.copy(lyricsFetchPreferWordLevel = preferWordLevel) }
    }

    fun setDownloadWriteMetadata(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_DOWNLOAD_WRITE_METADATA, enabled) }
        _settings.update { it.copy(downloadWriteMetadata = enabled) }
    }

    fun setDownloadEmbedLyrics(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_DOWNLOAD_EMBED_LYRICS, enabled) }
        _settings.update { it.copy(downloadEmbedLyrics = enabled) }
    }

    fun setDownloadEmbedCover(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_DOWNLOAD_EMBED_COVER, enabled) }
        _settings.update { it.copy(downloadEmbedCover = enabled) }
    }

    fun setRescanOnLaunch(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_RESCAN_ON_LAUNCH, enabled) }
        _settings.update { it.copy(rescanOnLaunch = enabled) }
    }

    fun setLaunchScanTiming(timing: LaunchScanTiming) {
        preferences.edit { putString(KEY_LAUNCH_SCAN_TIMING, timing.name) }
        _settings.update { it.copy(launchScanTiming = timing) }
    }

    fun setArtistViewMode(mode: CollectionViewMode) {
        preferences.edit { putString(KEY_ARTIST_VIEW_MODE, mode.name) }
        _settings.update { it.copy(artistViewMode = mode) }
    }

    fun setAlbumViewMode(mode: CollectionViewMode) {
        preferences.edit { putString(KEY_ALBUM_VIEW_MODE, mode.name) }
        _settings.update { it.copy(albumViewMode = mode) }
    }

    fun setPlaylistViewMode(mode: CollectionViewMode) {
        preferences.edit { putString(KEY_PLAYLIST_VIEW_MODE, mode.name) }
        _settings.update { it.copy(playlistViewMode = mode) }
    }

    fun setFoldersViewMode(mode: CollectionViewMode) {
        preferences.edit { putString(KEY_FOLDERS_VIEW_MODE, mode.name) }
        _settings.update { it.copy(foldersViewMode = mode) }
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
        themeColor = preferences.getString(KEY_THEME_COLOR, null)
            ?.let { runCatching { ThemeColor.valueOf(it) }.getOrNull() }
            ?: ThemeColor.MINT,
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
        playerQuickActions = preferences.getString(KEY_PLAYER_QUICK_ACTIONS, null)
            ?.split(',')
            ?.mapNotNull { token ->
                runCatching { PlayerQuickAction.valueOf(token.trim()) }.getOrNull()
            }
            ?.distinct()
            ?.ifEmpty { DEFAULT_PLAYER_QUICK_ACTIONS }
            ?: DEFAULT_PLAYER_QUICK_ACTIONS,
        playbackSpeed = preferences.getFloat(KEY_PLAYBACK_SPEED, 1f)
            .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED),
        playbackPitchSemitones = preferences.getFloat(KEY_PLAYBACK_PITCH_SEMITONES, 0f)
            .coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES),
        playbackFadeEnabled = preferences.getBoolean(KEY_PLAYBACK_FADE_ENABLED, true),
        autoPlayOnLaunch = preferences.getBoolean(KEY_AUTO_PLAY_ON_LAUNCH, false),
        keepKaraokeDraftAfterPublish = preferences.getBoolean(
            KEY_KEEP_KARAOKE_DRAFT_AFTER_PUBLISH,
            true,
        ),
        karaokeOutputTreeUri = preferences.getString(KEY_KARAOKE_OUTPUT_TREE_URI, null)
            ?.takeIf { it.isNotBlank() },
        downloadDirTreeUri = preferences.getString(KEY_DOWNLOAD_DIR_TREE_URI, null)
            ?.takeIf { it.isNotBlank() },
        downloadNamingFormat = preferences.getString(KEY_DOWNLOAD_NAMING_FORMAT, null)
            ?.let { runCatching { DownloadNamingFormat.valueOf(it) }.getOrNull() }
            ?: DownloadNamingFormat.TITLE_ARTIST,
        lyricsFetchPreferWordLevel = preferences.getBoolean(KEY_LYRICS_FETCH_PREFER_WORD_LEVEL, true),
        downloadWriteMetadata = preferences.getBoolean(KEY_DOWNLOAD_WRITE_METADATA, true),
        downloadEmbedLyrics = preferences.getBoolean(KEY_DOWNLOAD_EMBED_LYRICS, true),
        downloadEmbedCover = preferences.getBoolean(KEY_DOWNLOAD_EMBED_COVER, true),
        rescanOnLaunch = preferences.getBoolean(KEY_RESCAN_ON_LAUNCH, false),
        launchScanTiming = preferences.getString(KEY_LAUNCH_SCAN_TIMING, null)
            ?.let { runCatching { LaunchScanTiming.valueOf(it) }.getOrNull() }
            ?: LaunchScanTiming.DURING_STARTUP,
        artistViewMode = preferences.getString(KEY_ARTIST_VIEW_MODE, null)
            ?.let { runCatching { CollectionViewMode.valueOf(it) }.getOrNull() }
            ?: CollectionViewMode.LIST,
        albumViewMode = preferences.getString(KEY_ALBUM_VIEW_MODE, null)
            ?.let { runCatching { CollectionViewMode.valueOf(it) }.getOrNull() }
            ?: CollectionViewMode.GRID,
        playlistViewMode = preferences.getString(KEY_PLAYLIST_VIEW_MODE, null)
            ?.let { runCatching { CollectionViewMode.valueOf(it) }.getOrNull() }
            ?: CollectionViewMode.GRID,
        foldersViewMode = preferences.getString(KEY_FOLDERS_VIEW_MODE, null)
            ?.let { runCatching { CollectionViewMode.valueOf(it) }.getOrNull() }
            ?: CollectionViewMode.LIST,
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
        private const val KEY_THEME_COLOR = "theme_color"
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
        private const val KEY_PLAYER_QUICK_ACTIONS = "player_quick_actions"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_PLAYBACK_PITCH_SEMITONES = "playback_pitch_semitones"
        private const val KEY_PLAYBACK_FADE_ENABLED = "playback_fade_enabled"
        private const val KEY_AUTO_PLAY_ON_LAUNCH = "auto_play_on_launch"
        private const val KEY_KEEP_KARAOKE_DRAFT_AFTER_PUBLISH =
            "keep_karaoke_draft_after_publish"
        private const val KEY_KARAOKE_OUTPUT_TREE_URI = "karaoke_output_tree_uri"
        private const val KEY_DOWNLOAD_DIR_TREE_URI = "download_dir_tree_uri"
        private const val KEY_DOWNLOAD_NAMING_FORMAT = "download_naming_format"
        private const val KEY_LYRICS_FETCH_PREFER_WORD_LEVEL = "lyrics_fetch_prefer_word_level"
        private const val KEY_DOWNLOAD_WRITE_METADATA = "download_write_metadata"
        private const val KEY_DOWNLOAD_EMBED_LYRICS = "download_embed_lyrics"
        private const val KEY_DOWNLOAD_EMBED_COVER = "download_embed_cover"
        private const val KEY_RESCAN_ON_LAUNCH = "rescan_on_launch"
        private const val KEY_LAUNCH_SCAN_TIMING = "launch_scan_timing"
        private const val KEY_ARTIST_VIEW_MODE = "artist_view_mode"
        private const val KEY_ALBUM_VIEW_MODE = "album_view_mode"
        private const val KEY_PLAYLIST_VIEW_MODE = "playlist_view_mode"
        private const val KEY_FOLDERS_VIEW_MODE = "folders_view_mode"
        private const val KEY_LAST_PLAYBACK_TRACK_ID = "last_playback_track_id"
        private const val KEY_LAST_PLAYBACK_POSITION_MS = "last_playback_position_ms"
    }
}
