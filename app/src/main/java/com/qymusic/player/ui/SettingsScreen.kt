package com.qymusic.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.AppSettings
import com.qymusic.player.playback.AudioOutputInfo
import com.qymusic.player.data.LaunchScanTiming
import com.qymusic.player.data.LyricAlignment
import com.qymusic.player.data.LyricWordAnimationStyle
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.SettingsStore
import com.qymusic.player.data.ThemeMode
import com.qymusic.player.playback.EqualizerUiState
import com.qymusic.player.playback.ReverbPreset
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    folders: List<MusicFolder>,
    equalizerState: EqualizerUiState,
    audioOutput: AudioOutputInfo,
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (MusicFolder) -> Unit,
    onRescan: () -> Unit,
    onRescanOnLaunchChange: (Boolean) -> Unit,
    onLaunchScanTimingChange: (LaunchScanTiming) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onMusicReactiveBackgroundChange: (Boolean) -> Unit,
    onLyricAlignmentChange: (LyricAlignment) -> Unit,
    onLyricFontScaleChange: (Float) -> Unit,
    onLyricBoldChange: (Boolean) -> Unit,
    onLyricInactiveBlurChange: (Float) -> Unit,
    onLyricCenterStartEndChange: (Boolean) -> Unit,
    onLyricWordAnimationStyleChange: (LyricWordAnimationStyle) -> Unit,
    onAutoPlayOnLaunchChange: (Boolean) -> Unit,
    onPlaybackFadeEnabledChange: (Boolean) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerBandChange: (Int, Int) -> Unit,
    onEqualizerPresetChange: (Int) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onLoudnessGainChange: (Int) -> Unit,
    onReverbPresetChange: (ReverbPreset) -> Unit,
    onReverbLevelChange: (Int) -> Unit,
) {
    var categoryName by rememberSaveable { mutableStateOf(SettingsCategory.ROOT.name) }
    val category = SettingsCategory.valueOf(categoryName)

    BackHandler(enabled = category != SettingsCategory.ROOT) {
        categoryName = SettingsCategory.ROOT.name
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
            ) {
                SettingsTopBar(
                    title = stringResource(category.labelRes),
                    onBack = {
                        if (category == SettingsCategory.ROOT) {
                            onBack()
                        } else {
                            categoryName = SettingsCategory.ROOT.name
                        }
                    },
                )
                AnimatedContent(
                    targetState = category,
                    transitionSpec = {
                        if (targetState == SettingsCategory.ROOT) {
                            (
                                slideInHorizontally(tween(PAGE_MS)) { -it / 4 } +
                                    fadeIn(tween(FADE_MS))
                                ) togetherWith (
                                slideOutHorizontally(tween(PAGE_MS)) { it / 4 } +
                                    fadeOut(tween(FADE_MS))
                                )
                        } else {
                            (
                                slideInHorizontally(tween(PAGE_MS)) { it / 4 } +
                                    fadeIn(tween(FADE_MS))
                                ) togetherWith (
                                slideOutHorizontally(tween(PAGE_MS)) { -it / 4 } +
                                    fadeOut(tween(FADE_MS))
                                )
                        }
                    },
                    label = "settings-page",
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page == SettingsCategory.ABOUT) {
                        AboutSettingsContent(modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            when (page) {
                                SettingsCategory.ROOT -> {
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.settings_appearance),
                                        icon = Icons.Rounded.Palette,
                                        onClick = {
                                            categoryName = SettingsCategory.APPEARANCE.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.settings_background),
                                        icon = Icons.Rounded.Wallpaper,
                                        onClick = {
                                            categoryName = SettingsCategory.BACKGROUND.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.settings_scanning),
                                        icon = Icons.Rounded.FolderOpen,
                                        onClick = {
                                            categoryName = SettingsCategory.SCANNING.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.lyrics_settings),
                                        icon = Icons.Rounded.Lyrics,
                                        onClick = {
                                            categoryName = SettingsCategory.LYRICS.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.settings_playback),
                                        icon = Icons.Rounded.PlayArrow,
                                        onClick = {
                                            categoryName = SettingsCategory.PLAYBACK.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.settings_sound_effects),
                                        icon = Icons.Rounded.Equalizer,
                                        onClick = {
                                            categoryName = SettingsCategory.EFFECTS.name
                                        },
                                    )
                                    SettingsCategoryRow(
                                        title = stringResource(R.string.about),
                                        icon = Icons.Rounded.Info,
                                        onClick = {
                                            categoryName = SettingsCategory.ABOUT.name
                                        },
                                    )
                                }

                                SettingsCategory.APPEARANCE -> SettingsSection(
                                    title = stringResource(R.string.theme_settings),
                                    icon = Icons.Rounded.Palette,
                                ) {
                                    ChoiceSegmented(
                                        options = listOf(
                                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                            ThemeMode.DARK to stringResource(R.string.theme_dark),
                                        ),
                                        selected = settings.themeMode,
                                        onSelected = onThemeModeChange,
                                    )
                                }

                                SettingsCategory.BACKGROUND -> SettingsSection(
                                    title = stringResource(R.string.settings_background),
                                    icon = Icons.Rounded.Wallpaper,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onMusicReactiveBackgroundChange(
                                                        !settings.musicReactiveBackground,
                                                    )
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.music_reactive_background,
                                            ),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.musicReactiveBackground,
                                            onCheckedChange = onMusicReactiveBackgroundChange,
                                        )
                                    }
                                }

                                SettingsCategory.SCANNING -> SettingsSection(
                                    title = stringResource(R.string.directory_settings),
                                    icon = Icons.Rounded.FolderOpen,
                                ) {
                                    OutlinedButton(
                                        onClick = onAddFolder,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(text = stringResource(R.string.add_folder))
                                    }
                                    OutlinedButton(
                                        onClick = onRescan,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(text = stringResource(R.string.rescan))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onRescanOnLaunchChange(
                                                        !settings.rescanOnLaunch,
                                                    )
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.rescan_on_launch),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.rescanOnLaunch,
                                            onCheckedChange = onRescanOnLaunchChange,
                                        )
                                    }
                                    if (settings.rescanOnLaunch) {
                                        ChoiceSegmented(
                                            options = listOf(
                                                LaunchScanTiming.DURING_STARTUP to
                                                    stringResource(
                                                        R.string.rescan_timing_during_startup,
                                                    ),
                                                LaunchScanTiming.AFTER_STARTUP to
                                                    stringResource(
                                                        R.string.rescan_timing_after_startup,
                                                    ),
                                            ),
                                            selected = settings.launchScanTiming,
                                            onSelected = onLaunchScanTimingChange,
                                        )
                                    }
                                    folders.forEach { folder ->
                                        FolderSettingRow(
                                            folder = folder,
                                            onRemove = { onRemoveFolder(folder) },
                                        )
                                    }
                                }

                                SettingsCategory.LYRICS -> SettingsSection(
                                    title = stringResource(R.string.lyrics_settings),
                                    icon = Icons.Rounded.Lyrics,
                                ) {
                                    ChoiceSegmented(
                                        options = listOf(
                                            LyricAlignment.LEFT to
                                                stringResource(R.string.lyric_align_left),
                                            LyricAlignment.CENTER to
                                                stringResource(R.string.lyric_align_center),
                                            LyricAlignment.RIGHT to
                                                stringResource(R.string.lyric_align_right),
                                        ),
                                        selected = settings.lyricAlignment,
                                        onSelected = onLyricAlignmentChange,
                                    )
                                    SettingLabeledSlider(
                                        label = stringResource(R.string.lyric_font_size),
                                        value = settings.lyricFontScale,
                                        valueRange = SettingsStore.MIN_LYRIC_FONT_SCALE..
                                            SettingsStore.MAX_LYRIC_FONT_SCALE,
                                        valueFormatter = { value ->
                                            "${(value * 100).roundToInt()}%"
                                        },
                                        onValueChange = onLyricFontScaleChange,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onLyricBoldChange(!settings.lyricBold)
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lyric_bold),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.lyricBold,
                                            onCheckedChange = onLyricBoldChange,
                                        )
                                    }
                                    SettingLabeledSlider(
                                        label = stringResource(R.string.lyric_blur_inactive),
                                        value = settings.lyricInactiveBlurDp,
                                        valueRange = SettingsStore.MIN_LYRIC_INACTIVE_BLUR_DP..
                                            SettingsStore.MAX_LYRIC_INACTIVE_BLUR_DP,
                                        valueFormatter = { value -> "%.1f dp".format(value) },
                                        onValueChange = onLyricInactiveBlurChange,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onLyricCenterStartEndChange(
                                                        !settings.lyricCenterStartEnd,
                                                    )
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.lyric_center_start_end,
                                            ),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.lyricCenterStartEnd,
                                            onCheckedChange = onLyricCenterStartEndChange,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.lyric_word_animation),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    ChoiceSegmented(
                                        options = listOf(
                                            LyricWordAnimationStyle.HIGHLIGHT to
                                                stringResource(
                                                    R.string.lyric_word_animation_highlight,
                                                ),
                                            LyricWordAnimationStyle.STAR to
                                                stringResource(
                                                    R.string.lyric_word_animation_star,
                                                ),
                                        ),
                                        selected = settings.lyricWordAnimationStyle,
                                        onSelected = onLyricWordAnimationStyleChange,
                                    )
                                }

                                SettingsCategory.PLAYBACK -> SettingsSection(
                                    title = stringResource(R.string.settings_playback),
                                    icon = Icons.Rounded.PlayArrow,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onAutoPlayOnLaunchChange(
                                                        !settings.autoPlayOnLaunch,
                                                    )
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auto_play_on_launch),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.autoPlayOnLaunch,
                                            onCheckedChange = onAutoPlayOnLaunchChange,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                role = Role.Switch,
                                                onClick = {
                                                    onPlaybackFadeEnabledChange(
                                                        !settings.playbackFadeEnabled,
                                                    )
                                                },
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.playback_fade),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Switch(
                                            checked = settings.playbackFadeEnabled,
                                            onCheckedChange = onPlaybackFadeEnabledChange,
                                        )
                                    }
                                }

                                SettingsCategory.EFFECTS -> {
                                    SettingsSection(
                                        title = stringResource(R.string.equalizer),
                                        icon = Icons.Rounded.Equalizer,
                                    ) {
                                        EqualizerControls(
                                            state = equalizerState,
                                            onEnabledChange = onEqualizerEnabledChange,
                                            onPresetChange = onEqualizerPresetChange,
                                            onBandChange = onEqualizerBandChange,
                                            onBassStrengthChange = onBassStrengthChange,
                                            onVirtualizerStrengthChange =
                                                onVirtualizerStrengthChange,
                                            onLoudnessGainChange = onLoudnessGainChange,
                                            onReverbPresetChange = onReverbPresetChange,
                                            onReverbLevelChange = onReverbLevelChange,
                                        )
                                    }
                                    SettingsSection(
                                        title = stringResource(R.string.audio_output),
                                        icon = Icons.Rounded.Speaker,
                                    ) {
                                        OutputInfoRow(
                                            label = stringResource(R.string.output_device),
                                            value = audioOutput.deviceNames.joinToString("、")
                                                .ifBlank {
                                                    stringResource(
                                                        R.string.output_device_default,
                                                    )
                                                },
                                        )
                                        OutputInfoRow(
                                            label = stringResource(
                                                R.string.output_native_format,
                                            ),
                                            value = audioOutput.nativeDescription,
                                        )
                                        OutputInfoRow(
                                            label = stringResource(R.string.output_high_res),
                                            value = audioOutput.highResDescription,
                                        )
                                    }
                                }

                                SettingsCategory.ABOUT -> Unit
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private enum class SettingsCategory(val labelRes: Int) {
    ROOT(R.string.settings),
    APPEARANCE(R.string.settings_appearance),
    BACKGROUND(R.string.settings_background),
    SCANNING(R.string.settings_scanning),
    LYRICS(R.string.lyrics_settings),
    PLAYBACK(R.string.settings_playback),
    EFFECTS(R.string.settings_sound_effects),
    ABOUT(R.string.about),
}

private const val PAGE_MS = 260
private const val FADE_MS = 180

@Composable
private fun SettingsCategoryRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutputInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = { content() },
            )
        }
    }
}

@Composable
private fun FolderSettingRow(
    folder: MusicFolder,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.uri.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.remove_folder),
            )
        }
    }
}

@Composable
private fun <T> ChoiceSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            role = Role.RadioButton,
                            onClick = { onSelected(value) },
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                if (value != options.last().first) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingLabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
