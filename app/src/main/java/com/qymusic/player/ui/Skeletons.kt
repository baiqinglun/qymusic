package com.qymusic.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qymusic.player.data.CollectionViewMode

/** 骨架屏斜向光带扫过一轮的时长：歌曲与集合页骨架共用同一节奏。 */
internal const val SKELETON_SHIMMER_PERIOD_MS = 1500

/** 数据到位后骨架屏的淡出时长，与条目的分块浮现同时开始。 */
internal const val SKELETON_FADE_OUT_MS = 260

/** 骨架条长度按行变化，避免整屏看起来是同一根。 */
private val SKELETON_TITLE_FRACTIONS = listOf(0.62f, 0.46f, 0.72f, 0.54f, 0.66f, 0.42f)
private val SKELETON_SUBTITLE_FRACTIONS = listOf(0.44f, 0.58f, 0.36f, 0.52f, 0.4f, 0.56f)
private val SKELETON_GRID_TITLE_FRACTIONS = listOf(0.78f, 0.58f, 0.7f, 0.5f, 0.64f, 0.56f)
private val SKELETON_GRID_COUNT_FRACTIONS = listOf(0.4f, 0.28f, 0.34f, 0.24f, 0.3f, 0.38f)

/** 共享的光带动画：0→1 线性循环，一道斜向光带每 1.5 秒从左往右扫过一轮。 */
@Composable
internal fun rememberSkeletonShimmer(): Animatable<Float, AnimationVector1D> {
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shimmer.snapTo(0f)
            shimmer.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SKELETON_SHIMMER_PERIOD_MS,
                    easing = LinearEasing,
                ),
            )
        }
    }
    return shimmer
}

/**
 * 骨架屏的光带遮罩：整片骨架区域只画一道斜向光带，所有骨架块共享同一条。
 * 需要独立图层（Offscreen），光带用 SrcAtop 只落在骨架形状上，不会透到背景。
 * 在绘制阶段读 [shimmerValue]，光带逐帧重绘但不触发重组。
 */
internal fun Modifier.skeletonBandOverlay(shimmerValue: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = size.width * 0.4f
            val centerX = -band + (size.width + band * 2f) * shimmerValue
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    start = Offset(centerX - band * 0.5f, 0f),
                    end = Offset(centerX + band * 0.5f, size.height),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }

/** 骨架屏淡出状态：loading 期间常显，数据到位后整体淡出一次再移除。 */
internal class SkeletonOverlayState(
    val showing: Boolean,
    private val alphaAnimatable: Animatable<Float, AnimationVector1D>,
) {
    /** 在 graphicsLayer 里读取：淡出逐帧走绘制阶段，不触发重组。 */
    val alpha: Float get() = alphaAnimatable.value
}

@Composable
internal fun rememberSkeletonOverlay(loading: Boolean): SkeletonOverlayState {
    val alpha = remember { Animatable(if (loading) 1f else 0f) }
    var showing by remember { mutableStateOf(loading) }
    LaunchedEffect(loading) {
        if (loading) {
            showing = true
            alpha.snapTo(1f)
        } else if (showing) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SKELETON_FADE_OUT_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            showing = false
        }
    }
    return SkeletonOverlayState(showing, alpha)
}

@Composable
internal fun SkeletonBlock(
    color: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color),
    )
}

/**
 * 集合页（艺术家 / 专辑 / 歌单 / 文件夹）骨架屏：列表 / 网格两种形态，
 * 封面、标题、数量的块与真实条目同形同尺寸；整片骨架共用一道斜向光带。
 *
 * @param circleCovers 列表形态下封面是否为圆形（艺术家页）。
 * @param showSubtitle 列表行是否带第二行说明（文件夹页）。
 * @param showChevron 列表行尾是否保留箭头占位。
 * @param endPadding 右侧字母导航占位，与真实列表的留白对齐。
 */
@Composable
internal fun CollectionSkeleton(
    viewMode: CollectionViewMode,
    modifier: Modifier = Modifier,
    circleCovers: Boolean = false,
    showSubtitle: Boolean = false,
    showChevron: Boolean = true,
    endPadding: Dp = 0.dp,
) {
    val shimmer = rememberSkeletonShimmer()
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (endPadding > 0.dp) {
                    Modifier.padding(end = endPadding)
                } else {
                    Modifier
                },
            )
            .skeletonBandOverlay(shimmer.value),
    ) {
        when (viewMode) {
            CollectionViewMode.LIST -> CollectionSkeletonList(
                blockColor = blockColor,
                dividerColor = dividerColor,
                circleCovers = circleCovers,
                showSubtitle = showSubtitle,
                showChevron = showChevron,
            )

            CollectionViewMode.GRID -> CollectionSkeletonGrid(blockColor = blockColor)
        }
    }
}

/** 骨架列表行：行高、内边距、分割线缩进与 CollectionListContent 的真实条目一致。 */
@Composable
private fun CollectionSkeletonList(
    blockColor: Color,
    dividerColor: Color,
    circleCovers: Boolean,
    showSubtitle: Boolean,
    showChevron: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(SKELETON_ROW_COUNT) { index ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        color = blockColor,
                        modifier = Modifier.size(48.dp),
                        corner = if (circleCovers) 24.dp else 8.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBlock(
                            color = blockColor,
                            modifier = Modifier
                                .fillMaxWidth(
                                    SKELETON_TITLE_FRACTIONS[
                                        index % SKELETON_TITLE_FRACTIONS.size,
                                    ],
                                )
                                .height(15.dp),
                        )
                        if (showSubtitle) {
                            SkeletonBlock(
                                color = blockColor,
                                modifier = Modifier
                                    .fillMaxWidth(
                                        SKELETON_SUBTITLE_FRACTIONS[
                                            index % SKELETON_SUBTITLE_FRACTIONS.size,
                                        ],
                                    )
                                    .height(11.dp),
                            )
                        }
                    }
                    SkeletonBlock(
                        color = blockColor,
                        modifier = Modifier
                            .width(28.dp)
                            .height(11.dp),
                        corner = 3.dp,
                    )
                    if (showChevron) {
                        Spacer(modifier = Modifier.width(28.dp))
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 84.dp),
                    color = dividerColor,
                )
            }
        }
    }
}

/** 骨架网格卡片：三列方角封面加两行文字，与 CollectionGridContent 的真实卡片一致。 */
@Composable
private fun CollectionSkeletonGrid(
    blockColor: Color,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMN_COUNT),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // 骨架屏只是占位，不允许滚动。
        userScrollEnabled = false,
    ) {
        items(SKELETON_GRID_ITEM_COUNT) { index ->
            Column {
                SkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    corner = 8.dp,
                )
                // 文字条高度取真实文本的行高（bodyMedium 20sp、labelSmall 16sp），
                // 让每张卡片与真实内容等高，骨架淡出时下面的行不会整体上移。
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(
                            SKELETON_GRID_TITLE_FRACTIONS[
                                index % SKELETON_GRID_TITLE_FRACTIONS.size,
                            ],
                        )
                        .height(20.dp),
                )
                SkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(
                            SKELETON_GRID_COUNT_FRACTIONS[
                                index % SKELETON_GRID_COUNT_FRACTIONS.size,
                            ],
                        )
                        .height(16.dp),
                )
            }
        }
    }
}

/** 骨架列表行数：铺满一屏即可。 */
private const val SKELETON_ROW_COUNT = 9

/** 骨架网格卡片数：三列 × 五行，铺满一屏。 */
private const val SKELETON_GRID_ITEM_COUNT = 15
