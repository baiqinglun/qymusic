package com.qymusic.player.playback

import androidx.media3.common.Player
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** 播放模式：顺序播放（默认）→ 单曲循环 → 随机播放。 */
enum class PlaybackMode {
    SEQUENTIAL,
    REPEAT_ONE,
    SHUFFLE,
    ;

    fun next(): PlaybackMode = when (this) {
        SEQUENTIAL -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }
}

/** 由播放器的 repeatMode / shuffleModeEnabled 反推当前播放模式。 */
fun playbackModeOf(repeatMode: Int, shuffleEnabled: Boolean): PlaybackMode = when {
    shuffleEnabled -> PlaybackMode.SHUFFLE
    repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.REPEAT_ONE
    else -> PlaybackMode.SEQUENTIAL
}

const val MIN_PLAYBACK_SPEED = 0.5f
const val MAX_PLAYBACK_SPEED = 2.0f
const val PLAYBACK_SPEED_STEP = 0.05f
const val MIN_PITCH_SEMITONES = -12f
const val MAX_PITCH_SEMITONES = 12f

private const val MIN_PITCH_FACTOR = 0.01f

/** 变调以半音为单位，换算成播放器使用的音高倍率。 */
fun pitchFactorOfSemitones(semitones: Float): Float = 2f.pow(semitones / 12f)

/** 播放器音高倍率换算回半音，用于界面显示。 */
fun semitonesOfPitchFactor(factor: Float): Float =
    12f * log2(factor.coerceAtLeast(MIN_PITCH_FACTOR))

/** 播放速度按 0.05 步进取整，避免出现 1.03x 这种毛刺值。 */
fun snapPlaybackSpeed(speed: Float): Float {
    val clamped = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    return ((clamped / PLAYBACK_SPEED_STEP).roundToInt() * PLAYBACK_SPEED_STEP)
        .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
}
