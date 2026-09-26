package com.qymusic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerCurveTest {
    @Test
    fun keepsLevelsAtExactCenters() {
        val levels = intArrayOf(-100, 0, 100, 200, 300, 400, 500, 600, 700, 800)
        EQ_BAND_CENTERS_HZ.forEachIndexed { index, frequencyHz ->
            assertEquals(levels[index], sampleBandLevel(EQ_BAND_CENTERS_HZ, levels, frequencyHz))
        }
    }

    @Test
    fun interpolatesOnLogarithmicFrequencyAxis() {
        val centers = intArrayOf(1_000, 2_000)
        val levels = intArrayOf(0, 1_000)
        // 对数中点约 1414Hz，电平应落在中间。
        assertEquals(500, sampleBandLevel(centers, levels, 1_414))
    }

    @Test
    fun clampsOutsideKnownRange() {
        val centers = intArrayOf(100, 1_000)
        val levels = intArrayOf(-300, 200)
        assertEquals(-300, sampleBandLevel(centers, levels, 20))
        assertEquals(200, sampleBandLevel(centers, levels, 20_000))
    }

    @Test
    fun handlesDescendingCenterOrder() {
        // 模拟器上 getCenterFreq 是降序返回的，插值前必须自己排升序。
        val descendingCenters = intArrayOf(14_000, 3_600, 910, 230, 60)
        val descendingLevels = intArrayOf(-100, 200, 0, 0, 300)
        assertEquals(300, sampleBandLevel(descendingCenters, descendingLevels, 31))
        // 910Hz 是设备频段本身，应该原样取回它自己的电平。
        assertEquals(0, sampleBandLevel(descendingCenters, descendingLevels, 910))
        assertEquals(200, sampleBandLevel(descendingCenters, descendingLevels, 3_600))
        assertEquals(-100, sampleBandLevel(descendingCenters, descendingLevels, 20_000))
    }

    @Test
    fun mapsFiveBandDeviceCurveOntoTenBandCenters() {
        val deviceCenters = intArrayOf(60, 230, 910, 3_600, 14_000)
        val deviceLevels = intArrayOf(300, 300, 0, 0, 0)
        assertEquals(300, sampleBandLevel(deviceCenters, deviceLevels, 31))
        assertEquals(300, sampleBandLevel(deviceCenters, deviceLevels, 62))
        assertEquals(0, sampleBandLevel(deviceCenters, deviceLevels, 1_000))
        assertEquals(0, sampleBandLevel(deviceCenters, deviceLevels, 16_000))
    }

    @Test
    fun ignoresMismatchedArrays() {
        assertEquals(0, sampleBandLevel(intArrayOf(100), intArrayOf(1, 2), 100))
        assertEquals(0, sampleBandLevel(IntArray(0), IntArray(0), 100))
    }
}
