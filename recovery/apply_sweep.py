# -*- coding: utf-8 -*-
# 在 damaged_head 基底上应用逐词扫亮功能
import io

p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1. imports
old = """import androidx.compose.runtime.staticCompositionLocalOf"""
new = """import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy"""
assert old in s
s = s.replace(old, new, 1)

old = """import androidx.compose.ui.text.font.FontWeight"""
new = """import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight"""
assert old in s
s = s.replace(old, new, 1)

# 2. 文本构建：词内从左到右扫亮
old = """    val text = remember(line, quantizedPositionMs) {
        buildAnnotatedString {
            line.segments.forEachIndexed { index, segment ->
                val nextTime = line.segments.getOrNull(index + 1)?.timeMs
                    ?: (segment.timeMs + KARAOKE_LAST_SEGMENT_DURATION_MS)
                val progress = if (quantizedPositionMs <= segment.timeMs) {
                    0f
                } else {
                    (
                        (quantizedPositionMs - segment.timeMs.toFloat()) /
                            (nextTime - segment.timeMs).toFloat().coerceAtLeast(1f)
                        ).coerceIn(0f, 1f)
                }
                withStyle(SpanStyle(color = lerp(unsungColor, sungColor, progress))) {
                    append(segment.text)
                }
            }
        }
    }"""
new = """    val activeSegmentEndOffset = segmentStartOffset +
        (activeSegment?.text?.length ?: 0)
    // 正在唱的词的字形横向包络，由 onTextLayout 回填。布局结果对象每次换新，
    // 这里只存"(词序号, 边界)"并按结构相等去重，避免重建文本→重新布局→再重建的死循环。
    var activeSweep by remember(line) {
        mutableStateOf<WordSweepBounds?>(null, structuralEqualityPolicy())
    }
    val text = remember(line, quantizedPositionMs, activeSweep) {
        buildAnnotatedString {
            line.segments.forEachIndexed { index, segment ->
                val nextTime = line.segments.getOrNull(index + 1)?.timeMs
                    ?: (segment.timeMs + KARAOKE_LAST_SEGMENT_DURATION_MS)
                val progress = if (quantizedPositionMs <= segment.timeMs) {
                    0f
                } else {
                    (
                        (quantizedPositionMs - segment.timeMs.toFloat()) /
                            (nextTime - segment.timeMs).toFloat().coerceAtLeast(1f)
                        ).coerceIn(0f, 1f)
                }
                when {
                    progress <= 0f ->
                        withStyle(SpanStyle(color = unsungColor)) { append(segment.text) }

                    progress >= 1f ->
                        withStyle(SpanStyle(color = sungColor)) { append(segment.text) }

                    else -> {
                        // 正在唱的词：亮暗分界从词的左边缘扫到右边缘；
                        // 首帧布局未就绪或词跨了两行时，退化为词内逐字变亮。
                        val bounds = activeSweep
                            ?.takeIf { it.segmentIndex == index }
                            ?.bounds
                        if (bounds != null) {
                            withStyle(
                                SpanStyle(
                                    brush = wordSweepBrush(
                                        bounds = bounds,
                                        progress = progress,
                                        unsungColor = unsungColor,
                                        sungColor = sungColor,
                                    ),
                                ),
                            ) {
                                append(segment.text)
                            }
                        } else {
                            appendCharSweep(segment.text, progress, unsungColor, sungColor)
                        }
                    }
                }
            }
        }
    }"""
assert old in s
s = s.replace(old, new, 1)

# 3. onTextLayout 回填扫亮包络
old = """                onTextLayout = { textLayoutResult = it },"""
new = """                onTextLayout = { result ->
                    textLayoutResult = result
                    activeSweep = result?.let { layout ->
                        WordSweepBounds(
                            segmentIndex = activeSegmentIndex,
                            bounds = segmentSweepBounds(
                                layout = layout,
                                startOffset = segmentStartOffset,
                                endOffsetExclusive = activeSegmentEndOffset,
                            ),
                        )
                    }
                },"""
assert old in s
s = s.replace(old, new, 1)

# 4. 辅助函数
old = """}

@Composable
private fun PlainLyrics("""
new = """}

/** 正在唱的词的扫亮信息：词在行内的序号 + 字形横向包络（跨行词为 null）。 */
private data class WordSweepBounds(
    val segmentIndex: Int,
    val bounds: Rect?,
)

/** 活动词的字形横向包络；词跨了两行（或退化成零宽）时返回 null。 */
private fun segmentSweepBounds(
    layout: TextLayoutResult,
    startOffset: Int,
    endOffsetExclusive: Int,
): Rect? {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0 || startOffset >= endOffsetExclusive) return null
    val firstOffset = startOffset.coerceIn(0, textLength - 1)
    val lastOffset = (endOffsetExclusive - 1).coerceIn(0, textLength - 1)
    if (layout.getLineForOffset(firstOffset) != layout.getLineForOffset(lastOffset)) return null
    val first = layout.getBoundingBox(firstOffset)
    val last = layout.getBoundingBox(lastOffset)
    val bounds = Rect(
        left = minOf(first.left, last.left),
        top = minOf(first.top, last.top),
        right = maxOf(first.right, last.right),
        bottom = maxOf(first.bottom, last.bottom),
    )
    return bounds.takeIf { it.width >= 1f }
}

/** 词内扫亮的渐变笔刷：亮色在左、暗色在右，分界带一点软边。 */
private fun wordSweepBrush(
    bounds: Rect,
    progress: Float,
    unsungColor: Color,
    sungColor: Color,
): Brush {
    val halfFeather = WORD_SWEEP_FEATHER_RATIO / 2f
    val sungStop = (progress - halfFeather).coerceIn(0f, 1f)
    val unsungStop = (progress + halfFeather).coerceIn(0f, 1f)
    return Brush.horizontalGradient(
        *buildList {
            add(0f to sungColor)
            if (sungStop > 0f) add(sungStop to sungColor)
            if (unsungStop < 1f) add(unsungStop to unsungColor)
            add(1f to unsungColor)
        }.toTypedArray(),
        startX = bounds.left,
        endX = bounds.right,
    )
}

/** 没有布局信息时的退化路径：把亮度按字符从词首摊到词尾。 */
private fun AnnotatedString.Builder.appendCharSweep(
    text: String,
    progress: Float,
    unsungColor: Color,
    sungColor: Color,
) {
    val weights = text.map { it.sweepWeight() }
    val totalWeight = weights.sum()
    if (totalWeight <= 0f) {
        withStyle(SpanStyle(color = lerp(unsungColor, sungColor, progress))) { append(text) }
        return
    }
    var consumed = 0f
    weights.forEachIndexed { index, weight ->
        val startFrac = consumed / totalWeight
        consumed += weight
        val endFrac = consumed / totalWeight
        val charProgress = ((progress - startFrac) / (endFrac - startFrac)).coerceIn(0f, 1f)
        withStyle(SpanStyle(color = lerp(unsungColor, sungColor, charProgress))) {
            append(text[index].toString())
        }
    }
}

/** 半角字符按较窄宽度参与分摊，让中英混排的扫亮速度更均匀。 */
private fun Char.sweepWeight(): Float = if (code < 0x2E80) 0.55f else 1f

@Composable
private fun PlainLyrics("""
assert old in s
s = s.replace(old, new, 1)

# 5. 软边常量
old = """/** 逐字高亮的量化步长，越小越丝滑，越大越省电。 */
private const val KARAOKE_POSITION_STEP_MS = 50"""
new = """/** 逐字高亮的量化步长，越小越丝滑，越大越省电。 */
private const val KARAOKE_POSITION_STEP_MS = 50
/** 词内扫亮软边占词宽的比例，让亮暗分界不是一条硬切线。 */
private const val WORD_SWEEP_FEATHER_RATIO = 0.18f"""
assert old in s
s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok')
