package com.qymusic.player.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val level: Int,
)

/** 环境混响预设，对应 EnvironmentalReverb 的内置房间参数。 */
enum class ReverbPreset(val presetValue: String?) {
    OFF(null),
    // 这些名称与 EnvironmentalReverb.Settings(String) 内置的房间参数一一对应。
    SMALL_ROOM("smallroom"),
    MEDIUM_ROOM("mediumroom"),
    LARGE_ROOM("largeroom"),
    MEDIUM_HALL("mediumhall"),
    LARGE_HALL("largehall"),
    PLATE("plate"),
    ;

    companion object {
        fun fromValue(value: String?): ReverbPreset =
            entries.firstOrNull { it.presetValue == value } ?: OFF
    }
}

data class EqualizerUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1_500,
    val maxLevel: Int = 1_500,
    /** 界面固定的 10 段控制点。 */
    val bands: List<EqualizerBand> = emptyList(),
    /** 设备实际频段数，用于说明 10 段曲线是如何下发的。 */
    val nativeBandCount: Int = 0,
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = -1,
    val bassAvailable: Boolean = false,
    val bassStrength: Int = 0,
    val bassMaxStrength: Int = 1_000,
    val virtualizerAvailable: Boolean = false,
    val virtualizerStrength: Int = 0,
    val virtualizerMaxStrength: Int = VIRTUALIZER_MAX_STRENGTH,
    val loudnessAvailable: Boolean = false,
    val loudnessGainMb: Int = 0,
    val loudnessMaxGainMb: Int = LOUDNESS_MAX_GAIN_MB,
    val reverbAvailable: Boolean = false,
    val reverbPreset: ReverbPreset = ReverbPreset.OFF,
    val reverbLevel: Int = DEFAULT_REVERB_LEVEL,
)

/** 混响强度用百分比表示，100% 对应不衰减。 */
const val MIN_REVERB_LEVEL = 0
const val MAX_REVERB_LEVEL = 100
const val DEFAULT_REVERB_LEVEL = 70

/** 虚拟环绕强度按 AudioEffect 的 0..1000 表示。 */
const val VIRTUALIZER_MAX_STRENGTH = 1_000

/** 响度补偿上限 +12dB。 */
const val LOUDNESS_MAX_GAIN_MB = 12_000

class EqualizerController(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var reverb: EnvironmentalReverb? = null
    private var audioSessionId: Int = 0
    /** 设备实际频段：下标 + 中心频率。 */
    private var deviceBands: List<Pair<Int, Int>> = emptyList()
    private var bandLevelRange: IntRange = -1_500..1_500

    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == audioSessionId) return
        releaseEqualizer()
        audioSessionId = sessionId

        runCatching {
            val effect = Equalizer(0, sessionId)
            val levelRange = effect.bandLevelRange
            val deviceBandList = buildList {
                for (index in 0 until effect.numberOfBands.toInt()) {
                    // getCenterFreq 返回的是毫赫兹，换算成 Hz 再参与曲线插值。
                    add(index to (effect.getCenterFreq(index.toShort()) / 1_000).toInt())
                }
            }
            val minLevel = levelRange[0].toInt()
            val maxLevel = levelRange[1].toInt()
            val savedLevels = preferences.getString(KEY_BAND_LEVELS, null)
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                .orEmpty()
            val canonicalLevels = decodeCanonicalLevels(savedLevels, deviceBandList, minLevel, maxLevel)
            val appliedLevels = applyCanonicalToDevice(
                effect,
                deviceBandList,
                canonicalLevels,
                minLevel,
                maxLevel,
            )
            Log.i(
                LOG_TAG,
                "均衡器就绪：设备频段 ${deviceBandList.size} 个，" +
                    "中心频率 ${deviceBandList.joinToString(",") { it.second.toString() }}Hz，" +
                    "电平范围 $minLevel..$maxLevel，10 段控制点 " +
                    "${canonicalLevels.joinToString(",")}，下发到设备 " +
                    appliedLevels.joinToString(","),
            )
            effect.enabled = preferences.getBoolean(KEY_ENABLED, false)
            val presetCount = effect.numberOfPresets.toInt()
            val presets = buildList {
                for (index in 0 until presetCount) {
                    add(effect.getPresetName(index.toShort()).toString())
                }
            }
            equalizer = effect
            deviceBands = deviceBandList
            bandLevelRange = minLevel..maxLevel
            _state.value = EqualizerUiState(
                available = true,
                enabled = effect.enabled,
                minLevel = minLevel,
                maxLevel = maxLevel,
                bands = canonicalBands(canonicalLevels),
                nativeBandCount = deviceBandList.size,
                presets = presets,
                selectedPreset = preferences.getInt(KEY_PRESET, -1),
            )
        }.onFailure {
            _state.value = EqualizerUiState(
                enabled = preferences.getBoolean(KEY_ENABLED, false),
            )
        }

        attachAdditionalEffects(sessionId)
    }

    /** 低音增强、虚拟环绕、响度补偿与环境混响：任何一个不可用都不影响均衡器本身。 */
    private fun attachAdditionalEffects(sessionId: Int) {
        val bassMaxStrength = BASS_MAX_STRENGTH
        var bassAvailable = false
        runCatching {
            val effect = BassBoost(0, sessionId)
            val strength = preferences.getInt(KEY_BASS_STRENGTH, 0)
                .coerceIn(0, bassMaxStrength)
            effect.setStrength(strength.toShort())
            effect.enabled = strength > 0
            bassBoost = effect
            bassAvailable = true
        }

        var virtualizerAvailable = false
        runCatching {
            val effect = Virtualizer(0, sessionId)
            val strength = preferences.getInt(KEY_VIRTUALIZER_STRENGTH, 0)
                .coerceIn(0, VIRTUALIZER_MAX_STRENGTH)
            effect.setStrength(strength.toShort())
            effect.enabled = strength > 0
            virtualizer = effect
            virtualizerAvailable = true
        }

        var loudnessAvailable = false
        runCatching {
            val effect = LoudnessEnhancer(sessionId)
            val gain = preferences.getInt(KEY_LOUDNESS_GAIN, 0)
                .coerceIn(0, LOUDNESS_MAX_GAIN_MB)
            effect.setTargetGain(gain)
            effect.enabled = gain > 0
            loudnessEnhancer = effect
            loudnessAvailable = true
        }

        var reverbAvailable = false
        runCatching {
            val effect = EnvironmentalReverb(0, sessionId)
            val preset = ReverbPreset.fromValue(preferences.getString(KEY_REVERB_PRESET, null))
            applyReverbPreset(effect, preset)
            effect.reverbLevel = reverbLevelInMillibels(
                preferences.getInt(KEY_REVERB_LEVEL, DEFAULT_REVERB_LEVEL),
            ).toShort()
            effect.enabled = preset != ReverbPreset.OFF
            reverb = effect
            reverbAvailable = true
        }

        Log.i(
            LOG_TAG,
            "附加音效可用性：低音增强=$bassAvailable，虚拟环绕=$virtualizerAvailable，" +
                "响度补偿=$loudnessAvailable，环境混响=$reverbAvailable",
        )

        _state.value = _state.value.copy(
            bassAvailable = bassAvailable,
            bassStrength = preferences.getInt(KEY_BASS_STRENGTH, 0).coerceIn(0, bassMaxStrength),
            bassMaxStrength = bassMaxStrength,
            virtualizerAvailable = virtualizerAvailable,
            virtualizerStrength = preferences.getInt(KEY_VIRTUALIZER_STRENGTH, 0)
                .coerceIn(0, VIRTUALIZER_MAX_STRENGTH),
            virtualizerMaxStrength = VIRTUALIZER_MAX_STRENGTH,
            loudnessAvailable = loudnessAvailable,
            loudnessGainMb = preferences.getInt(KEY_LOUDNESS_GAIN, 0)
                .coerceIn(0, LOUDNESS_MAX_GAIN_MB),
            loudnessMaxGainMb = LOUDNESS_MAX_GAIN_MB,
            reverbAvailable = reverbAvailable,
            reverbPreset = ReverbPreset.fromValue(preferences.getString(KEY_REVERB_PRESET, null)),
            reverbLevel = preferences.getInt(KEY_REVERB_LEVEL, DEFAULT_REVERB_LEVEL)
                .coerceIn(MIN_REVERB_LEVEL, MAX_REVERB_LEVEL),
        )
    }

    private fun applyReverbPreset(effect: EnvironmentalReverb, preset: ReverbPreset) {
        val value = preset.presetValue ?: return
        effect.setProperties(EnvironmentalReverb.Settings(value))
    }

    private fun reverbLevelInMillibels(percent: Int): Int =
        -9_600 + percent.coerceIn(MIN_REVERB_LEVEL, MAX_REVERB_LEVEL) * 96

    private fun canonicalBands(levels: IntArray): List<EqualizerBand> =
        EQ_BAND_CENTERS_HZ.mapIndexed { index, frequencyHz ->
            EqualizerBand(
                index = index,
                frequencyHz = frequencyHz,
                level = levels.getOrElse(index) { 0 },
            )
        }

    /**
     * 把存档换算成 10 段控制点。
     * 兼容旧的「按设备频段保存」的数据：先还原成设备曲线，再采样到 10 段。
     */
    private fun decodeCanonicalLevels(
        saved: List<Int>,
        deviceBandList: List<Pair<Int, Int>>,
        minLevel: Int,
        maxLevel: Int,
    ): IntArray = when {
        saved.size == EQ_BAND_CENTERS_HZ.size ->
            IntArray(EQ_BAND_CENTERS_HZ.size) { saved[it].coerceIn(minLevel, maxLevel) }

        saved.size == deviceBandList.size && deviceBandList.isNotEmpty() -> {
            val centers = deviceBandList.map { it.second }.toIntArray()
            val levels = IntArray(deviceBandList.size) { saved[it].coerceIn(minLevel, maxLevel) }
            IntArray(EQ_BAND_CENTERS_HZ.size) { index ->
                sampleBandLevel(centers, levels, EQ_BAND_CENTERS_HZ[index])
                    .coerceIn(minLevel, maxLevel)
            }
        }

        else -> IntArray(EQ_BAND_CENTERS_HZ.size)
    }

    /** 把 10 段曲线按对数频率插值下发到设备实际频段。 */
    private fun applyCanonicalToDevice(
        effect: Equalizer,
        deviceBandList: List<Pair<Int, Int>>,
        levels: IntArray,
        minLevel: Int,
        maxLevel: Int,
    ): List<Int> =
        deviceBandList.map { (index, centerHz) ->
            val level = sampleBandLevel(EQ_BAND_CENTERS_HZ, levels, centerHz)
                .coerceIn(minLevel, maxLevel)
            runCatching { effect.setBandLevel(index.toShort(), level.toShort()) }
            level
        }

    private fun applyCanonicalToDevice(bands: List<EqualizerBand>) {
        val effect = equalizer ?: return
        val levels = IntArray(bands.size) { bands[it].level }
        applyCanonicalToDevice(
            effect = effect,
            deviceBandList = deviceBands,
            levels = levels,
            minLevel = bandLevelRange.first,
            maxLevel = bandLevelRange.last,
        )
    }

    /** 读取设备频段当前电平，采样回 10 段控制点（用于套用系统预设后同步界面）。 */
    private fun canonicalLevelsFromDevice(effect: Equalizer): IntArray {
        if (deviceBands.isEmpty()) return IntArray(EQ_BAND_CENTERS_HZ.size)
        val centers = deviceBands.map { it.second }.toIntArray()
        val levels = IntArray(deviceBands.size) { index ->
            runCatching { effect.getBandLevel(deviceBands[index].first.toShort()).toInt() }
                .getOrDefault(0)
        }
        return IntArray(EQ_BAND_CENTERS_HZ.size) { index ->
            sampleBandLevel(centers, levels, EQ_BAND_CENTERS_HZ[index])
        }
    }

    private fun persistCanonicalLevels(bands: List<EqualizerBand>) {
        preferences.edit {
            putString(KEY_BAND_LEVELS, bands.joinToString(",") { it.level.toString() })
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        runCatching { equalizer?.enabled = enabled }
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val current = _state.value
        if (bandIndex !in current.bands.indices) return
        val normalized = level.coerceIn(current.minLevel, current.maxLevel)
        val updatedBands = current.bands.map { band ->
            if (band.index == bandIndex) band.copy(level = normalized) else band
        }
        // 10 段控制点插值下发到设备实际频段。
        applyCanonicalToDevice(updatedBands)
        persistCanonicalLevels(updatedBands)
        preferences.edit { putInt(KEY_PRESET, -1) }
        _state.value = current.copy(
            bands = updatedBands,
            selectedPreset = -1,
        )
    }

    fun setPreset(presetIndex: Int) {
        val current = _state.value
        if (presetIndex !in current.presets.indices) return
        runCatching {
            equalizer?.usePreset(presetIndex.toShort())
        }
        preferences.edit { putInt(KEY_PRESET, presetIndex) }

        val effect = equalizer
        val updatedBands = if (effect == null) {
            current.bands
        } else {
            canonicalBands(canonicalLevelsFromDevice(effect))
        }
        persistCanonicalLevels(updatedBands)
        _state.value = current.copy(
            bands = updatedBands,
            selectedPreset = presetIndex,
        )
    }

    fun setBassStrength(strength: Int) {
        val current = _state.value
        val normalized = strength.coerceIn(0, current.bassMaxStrength)
        runCatching {
            bassBoost?.let { effect ->
                effect.setStrength(normalized.toShort())
                effect.enabled = normalized > 0
            }
        }
        preferences.edit { putInt(KEY_BASS_STRENGTH, normalized) }
        _state.value = current.copy(bassStrength = normalized)
    }

    fun setVirtualizerStrength(strength: Int) {
        val current = _state.value
        val normalized = strength.coerceIn(0, current.virtualizerMaxStrength)
        runCatching {
            virtualizer?.let { effect ->
                effect.setStrength(normalized.toShort())
                effect.enabled = normalized > 0
            }
        }
        preferences.edit { putInt(KEY_VIRTUALIZER_STRENGTH, normalized) }
        _state.value = current.copy(virtualizerStrength = normalized)
    }

    fun setLoudnessGain(gainMb: Int) {
        val current = _state.value
        val normalized = gainMb.coerceIn(0, current.loudnessMaxGainMb)
        runCatching {
            loudnessEnhancer?.let { effect ->
                effect.setTargetGain(normalized)
                effect.enabled = normalized > 0
            }
        }
        preferences.edit { putInt(KEY_LOUDNESS_GAIN, normalized) }
        _state.value = current.copy(loudnessGainMb = normalized)
    }

    fun setReverbPreset(preset: ReverbPreset) {
        runCatching {
            reverb?.let { effect ->
                applyReverbPreset(effect, preset)
                effect.reverbLevel = reverbLevelInMillibels(_state.value.reverbLevel).toShort()
                effect.enabled = preset != ReverbPreset.OFF
            }
        }
        preferences.edit { putString(KEY_REVERB_PRESET, preset.presetValue) }
        _state.value = _state.value.copy(reverbPreset = preset)
    }

    fun setReverbLevel(level: Int) {
        val normalized = level.coerceIn(MIN_REVERB_LEVEL, MAX_REVERB_LEVEL)
        runCatching {
            reverb?.reverbLevel = reverbLevelInMillibels(normalized).toShort()
        }
        preferences.edit { putInt(KEY_REVERB_LEVEL, normalized) }
        _state.value = _state.value.copy(reverbLevel = normalized)
    }

    fun release() {
        releaseEqualizer()
        audioSessionId = 0
        _state.value = _state.value.copy(available = false)
    }

    private fun releaseEqualizer() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        runCatching {
            virtualizer?.enabled = false
            virtualizer?.release()
        }
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        runCatching {
            reverb?.enabled = false
            reverb?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        reverb = null
        deviceBands = emptyList()
    }

    private companion object {
        const val LOG_TAG = "QYMusicEqualizer"
        const val PREFERENCES_NAME = "qy_music_equalizer"
        const val KEY_ENABLED = "enabled"
        const val KEY_BAND_LEVELS = "band_levels"
        const val KEY_PRESET = "preset"
        const val KEY_BASS_STRENGTH = "bass_strength"
        const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
        const val KEY_LOUDNESS_GAIN = "loudness_gain"
        const val KEY_REVERB_PRESET = "reverb_preset"
        const val KEY_REVERB_LEVEL = "reverb_level"
        const val BASS_MAX_STRENGTH = 1_000
    }
}

object EqualizerControllerProvider {
    @Volatile
    private var instance: EqualizerController? = null

    fun get(context: Context): EqualizerController =
        instance ?: synchronized(this) {
            instance ?: EqualizerController(context.applicationContext).also {
                instance = it
            }
        }
}
