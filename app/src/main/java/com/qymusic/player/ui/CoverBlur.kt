package com.qymusic.player.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Color

internal fun createProceduralCover(
    seed: Int,
    size: Int = 128,
): Bitmap {
    val hue = ((seed.toLong() and 0xFFFFFFFFL) % 360L).toFloat()
    val secondHue = (hue + 48f) % 360f
    val firstColor = android.graphics.Color.HSVToColor(
        floatArrayOf(hue, 0.48f, 0.66f),
    )
    val secondColor = android.graphics.Color.HSVToColor(
        floatArrayOf(secondHue, 0.52f, 0.46f),
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            firstColor,
            secondColor,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    return bitmap
}

internal fun Bitmap.createBlurredCover(
    size: Int = 64,
    radius: Int = 10,
    darken: Float = BLURRED_COVER_DARKEN,
): Bitmap {
    val scaled = Bitmap.createScaledBitmap(this, size, size, true)
    val pixels = IntArray(size * size)
    scaled.getPixels(pixels, 0, size, 0, 0, size, size)
    scaled.recycle()

    var blurred = pixels
    repeat(3) {
        blurred = boxBlur(blurred, size, size, radius)
    }

    // 直接把压暗揉进小图里：播放页就不必再叠一层全屏黑色蒙版，少一次全屏混合。
    if (darken < 1f) {
        val factor = darken.coerceIn(0f, 1f)
        for (index in blurred.indices) {
            val color = blurred[index]
            val alpha = color ushr 24 and 0xFF
            val red = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
            val green = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
            val blue = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
            blurred[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(blurred, 0, size, 0, 0, size, size)
    }
}

/** 相当于原来叠在封面上的 34% 黑色蒙版。 */
private const val BLURRED_COVER_DARKEN = 0.66f

internal fun Bitmap.averageCoverColor(): Color {
    val size = minOf(width, height, 32)
    if (size <= 0) return Color(0xFF171B21)

    val stepX = (width / size).coerceAtLeast(1)
    val stepY = (height / size).coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0

    var y = stepY / 2
    while (y < height) {
        var x = stepX / 2
        while (x < width) {
            val color = getPixel(x, y)
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return Color(0xFF171B21)

    return Color(
        red = (red.toFloat() / count / 255f).coerceIn(0f, 1f),
        green = (green.toFloat() / count / 255f).coerceIn(0f, 1f),
        blue = (blue.toFloat() / count / 255f).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun boxBlur(
    source: IntArray,
    width: Int,
    height: Int,
    radius: Int,
): IntArray {
    val horizontal = IntArray(source.size)
    val result = IntArray(source.size)

    for (y in 0 until height) {
        for (x in 0 until width) {
            var alpha = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            var samples = 0
            for (offset in -radius..radius) {
                val sampleX = (x + offset).coerceIn(0, width - 1)
                val color = source[y * width + sampleX]
                alpha += (color ushr 24) and 0xFF
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                samples++
            }
            horizontal[y * width + x] = packColor(
                alpha = alpha / samples,
                red = red / samples,
                green = green / samples,
                blue = blue / samples,
            )
        }
    }

    for (y in 0 until height) {
        for (x in 0 until width) {
            var alpha = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            var samples = 0
            for (offset in -radius..radius) {
                val sampleY = (y + offset).coerceIn(0, height - 1)
                val color = horizontal[sampleY * width + x]
                alpha += (color ushr 24) and 0xFF
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                samples++
            }
            result[y * width + x] = packColor(
                alpha = alpha / samples,
                red = red / samples,
                green = green / samples,
                blue = blue / samples,
            )
        }
    }
    return result
}

private fun packColor(
    alpha: Long,
    red: Long,
    green: Long,
    blue: Long,
): Int =
    (((alpha and 0xFF).toInt()) shl 24) or
        (((red and 0xFF).toInt()) shl 16) or
        (((green and 0xFF).toInt()) shl 8) or
        ((blue and 0xFF).toInt())
