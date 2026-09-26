package com.qymusic.player.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTuningTest {
    @Test
    fun cyclesThroughThreePlaybackModes() {
        assertEquals(PlaybackMode.SEQUENTIAL, PlaybackMode.SEQUENTIAL.next().next().next())
        assertEquals(PlaybackMode.REPEAT_ONE, PlaybackMode.SEQUENTIAL.next())
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.REPEAT_ONE.next())
    }

    @Test
    fun derivesPlaybackModeFromPlayerFlags() {
        assertEquals(
            PlaybackMode.SEQUENTIAL,
            playbackModeOf(Player.REPEAT_MODE_OFF, shuffleEnabled = false),
        )
        assertEquals(
            PlaybackMode.REPEAT_ONE,
            playbackModeOf(Player.REPEAT_MODE_ONE, shuffleEnabled = false),
        )
        assertEquals(
            PlaybackMode.SHUFFLE,
            playbackModeOf(Player.REPEAT_MODE_ALL, shuffleEnabled = true),
        )
        // 随机播放内部用「列表循环」保证不中断，仍应识别为随机。
        assertEquals(
            PlaybackMode.SHUFFLE,
            playbackModeOf(Player.REPEAT_MODE_ALL, shuffleEnabled = true),
        )
    }

    @Test
    fun convertsBetweenSemitonesAndPitchFactor() {
        assertEquals(1f, pitchFactorOfSemitones(0f), 0.0001f)
        assertEquals(2f, pitchFactorOfSemitones(12f), 0.0001f)
        assertEquals(0.5f, pitchFactorOfSemitones(-12f), 0.0001f)
        assertEquals(7f, semitonesOfPitchFactor(pitchFactorOfSemitones(7f)), 0.0001f)
    }

    @Test
    fun snapsPlaybackSpeedToStepAndRange() {
        assertEquals(1f, snapPlaybackSpeed(1.02f), 0.0001f)
        assertEquals(1.25f, snapPlaybackSpeed(1.23f), 0.0001f)
        assertEquals(MAX_PLAYBACK_SPEED, snapPlaybackSpeed(3f), 0.0001f)
        assertEquals(MIN_PLAYBACK_SPEED, snapPlaybackSpeed(0.1f), 0.0001f)
    }
}
