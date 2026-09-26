package com.qymusic.player.playback

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 界面固定展示的 10 段中心频率。
 *
 * Android 的 [android.media.audiofx.Equalizer] 常见只有 5 个频段，所以界面上统一按 10 段
 * 呈现，下发时再把这些控制点插值成设备实际频段的电平。
 */
val EQ_BAND_CENTERS_HZ = intArrayOf(
    31,
    62,
    125,
    250,
    500,
    1_000,
    2_000,
    4_000,
    8_000,
    16_000,
)

/**
 * 在 [centersHz] / [levelsMb] 描述的曲线上取 [targetHz] 处的电平。
 *
 * 频率轴按对数插值，和听感一致；超出两端时直接取端点值。
 */
fun sampleBandLevel(centersHz: IntArray, levelsMb: IntArray, targetHz: Int): Int {
    if (centersHz.isEmpty() || centersHz.size != levelsMb.size) return 0
    // 设备（例如模拟器）可能按频率降序返回频段，这里统一排成升序再插值。
    val order = centersHz.indices.sortedBy { centersHz[it] }
    val sortedCenters = IntArray(order.size) { centersHz[order[it]] }
    val sortedLevels = IntArray(order.size) { levelsMb[order[it]] }
    val target = targetHz.coerceAtLeast(1)
    if (target <= sortedCenters.first()) return sortedLevels.first()
    if (target >= sortedCenters.last()) return sortedLevels.last()

    for (index in 0 until sortedCenters.size - 1) {
        val lowHz = sortedCenters[index]
        val highHz = sortedCenters[index + 1]
        if (target in lowHz..highHz) {
            val lowLevel = sortedLevels[index]
            val highLevel = sortedLevels[index + 1]
            val span = ln(highHz.toFloat()) - ln(lowHz.toFloat())
            if (span <= 0f) return lowLevel
            val ratio = (ln(target.toFloat()) - ln(lowHz.toFloat())) / span
            return (lowLevel + (highLevel - lowLevel) * ratio).roundToInt()
        }
    }
    return sortedLevels.last()
}
