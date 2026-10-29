package com.qymusic.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.CollectionViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 艺术家 / 专辑 / 歌单 / 文件夹共用的集合布局：同一条目模型、同一套列表与网格排版。
 * 页面之间只差封面画法与数据来源，列表 / 网格的切换由 [CollectionContent] 统一处理。
 */

/** 单个集合条目。cover 负责把封面画进给定的 Modifier（含共享元素、形状等修饰）。 */
internal class CollectionItem(
    /** 列表锚定 key：同一页面内唯一且稳定（排序后不变）。 */
    val key: String,
    val title: String,
    val trackCount: Int,
    /** 列表行标题下的补充说明（如文件夹路径）；网格卡片不显示。 */
    val subtitle: String? = null,
    val pinned: Boolean = false,
    /** 行尾是否显示箭头；不可点击的页面（文件夹）传 false。 */
    val showChevron: Boolean = true,
    val cover: @Composable (Modifier) -> Unit,
)

@Composable
internal fun CollectionContent(
    items: List<CollectionItem>,
    viewMode: CollectionViewMode,
    modifier: Modifier = Modifier,
    showAlphabetRail: Boolean = false,
    /** true 时条目内各块按 60ms 错开依次浮现（淡入 + 上移），配合骨架屏淡出使用。 */
    reveal: Boolean = false,
    /** 滚动状态由调用方持有：进入详情页时列表会被移出组合，
     *  状态放在内部的话返回时滚动位置会丢、共享封面也飞不回原格位。 */
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    emptyContent: (@Composable () -> Unit)? = null,
    onItemClick: ((CollectionItem) -> Unit)?,
) {
    if (items.isEmpty()) {
        emptyContent?.invoke()
        return
    }
    when (viewMode) {
        CollectionViewMode.LIST -> CollectionListContent(
            items = items,
            listState = listState,
            onItemClick = onItemClick,
            showAlphabetRail = showAlphabetRail,
            reveal = reveal,
            modifier = modifier,
        )

        CollectionViewMode.GRID -> CollectionGridContent(
            items = items,
            gridState = gridState,
            onItemClick = onItemClick,
            showAlphabetRail = showAlphabetRail,
            reveal = reveal,
            modifier = modifier,
        )
    }
}

/** 列表 / 网格切换按钮：图标指向切换之后的形式。 */
@Composable
internal fun ViewModeToggleButton(
    viewMode: CollectionViewMode,
    onViewModeChange: (CollectionViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onViewModeChange(viewMode.toggled()) },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (viewMode == CollectionViewMode.LIST) {
                Icons.Rounded.GridView
            } else {
                Icons.AutoMirrored.Rounded.ViewList
            },
            contentDescription = stringResource(
                if (viewMode == CollectionViewMode.LIST) {
                    R.string.switch_to_grid
                } else {
                    R.string.switch_to_list
                },
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 列表 / 网格互切。 */
internal fun CollectionViewMode.toggled(): CollectionViewMode =
    if (this == CollectionViewMode.LIST) CollectionViewMode.GRID else CollectionViewMode.LIST

@Composable
private fun CollectionListContent(
    items: List<CollectionItem>,
    listState: LazyListState,
    onItemClick: ((CollectionItem) -> Unit)?,
    showAlphabetRail: Boolean,
    reveal: Boolean,
    modifier: Modifier = Modifier,
) {
    // 排序变化后回到顶部，否则按 key 锚定会让顺序看起来没变。
    // 从详情页返回会重建这个 composable，用当前顺序作为初值以保留滚动位置，
    // 这样封面才能飞回列表里原来的格位。
    val orderSignature = items.joinToString(separator = "|") { it.key }
    var lastOrderSignature by remember { mutableStateOf(orderSignature) }
    LaunchedEffect(orderSignature) {
        if (lastOrderSignature != orderSignature) {
            lastOrderSignature = orderSignature
            if (
                listState.firstVisibleItemIndex != 0 ||
                listState.firstVisibleItemScrollOffset != 0
            ) {
                listState.scrollToItem(0)
            }
        }
    }
    val sectionStarts = remember(orderSignature, items) {
        collectionSectionStartIndexes(items)
    }
    val scope = rememberCoroutineScope()
    val activeLetter by remember(orderSignature, listState) {
        derivedStateOf {
            items.getOrNull(listState.firstVisibleItemIndex)
                ?.let { sectionKeyOf(it.title) }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showAlphabetRail) {
                        Modifier.padding(end = ALPHABET_RAIL_WIDTH)
                    } else {
                        Modifier
                    },
                ),
        ) {
            items(items = items, key = { it.key }) { item ->
                // 数据到位后：封面 → 标题 → 数量依次淡入并上移，每块错开 60ms。
                val coverReveal = rememberBlockReveal(reveal, order = 0)
                val titleReveal = rememberBlockReveal(reveal, order = 1)
                val countReveal = rememberBlockReveal(reveal, order = 2)
                val revealOffsetPx = with(LocalDensity.current) { BLOCK_REVEAL_OFFSET.toPx() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .listScrollScale(listState, item.key)
                        .heightIn(min = 72.dp)
                        .then(
                            if (onItemClick != null) {
                                Modifier.clickable(role = Role.Button) { onItemClick(item) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(48.dp)) {
                        // 浮现动画放在传给封面的 Modifier 里（sharedElement 之后）：
                        // 共享元素必须是封面最外层的修饰符，外层再包 graphicsLayer
                        // 会打断进 / 出详情页时的封面迁移过渡。
                        item.cover(
                            Modifier
                                .fillMaxSize()
                                .blockReveal(coverReveal, revealOffsetPx),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .blockReveal(titleReveal, revealOffsetPx),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle != null) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.track_count, item.trackCount),
                        modifier = Modifier.blockReveal(countReveal, revealOffsetPx),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.pinned) {
                        PinnedBadge()
                    }
                    if (item.showChevron) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 84.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
        if (showAlphabetRail) {
            AlphabetRail(
                available = sectionStarts.keys,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    sectionStarts[letter]?.let { index ->
                        scope.launch { listState.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun CollectionGridContent(
    items: List<CollectionItem>,
    gridState: LazyGridState,
    onItemClick: ((CollectionItem) -> Unit)?,
    showAlphabetRail: Boolean,
    reveal: Boolean,
    modifier: Modifier = Modifier,
) {
    val orderSignature = items.joinToString(separator = "|") { it.key }
    var lastOrderSignature by remember { mutableStateOf(orderSignature) }
    LaunchedEffect(orderSignature) {
        if (lastOrderSignature != orderSignature) {
            lastOrderSignature = orderSignature
            if (
                gridState.firstVisibleItemIndex != 0 ||
                gridState.firstVisibleItemScrollOffset != 0
            ) {
                gridState.scrollToItem(0)
            }
        }
    }
    val sectionStarts = remember(orderSignature, items) {
        collectionSectionStartIndexes(items)
    }
    val scope = rememberCoroutineScope()
    val activeLetter by remember(orderSignature, gridState) {
        derivedStateOf {
            items.getOrNull(gridState.firstVisibleItemIndex)
                ?.let { sectionKeyOf(it.title) }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(GRID_COLUMN_COUNT),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showAlphabetRail) {
                        Modifier.padding(end = ALPHABET_RAIL_WIDTH)
                    } else {
                        Modifier
                    },
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            gridItems(items = items, key = { it.key }) { item ->
                // 数据到位后：封面 → 标题 → 数量依次淡入并上移，每块错开 60ms。
                val coverReveal = rememberBlockReveal(reveal, order = 0)
                val titleReveal = rememberBlockReveal(reveal, order = 1)
                val countReveal = rememberBlockReveal(reveal, order = 2)
                val revealOffsetPx = with(LocalDensity.current) { BLOCK_REVEAL_OFFSET.toPx() }
                Column(
                    modifier = Modifier
                        .gridScrollScale(gridState, item.key)
                        .then(
                            if (onItemClick != null) {
                                Modifier.clickable(role = Role.Button) { onItemClick(item) }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        // 浮现动画放在传给封面的 Modifier 里（sharedElement 之后），
                        // 保证共享元素是封面最外层修饰符，迁移过渡不被打断。
                        item.cover(
                            Modifier
                                .fillMaxSize()
                                .blockReveal(coverReveal, revealOffsetPx),
                        )
                        if (item.pinned) {
                            PinnedBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        modifier = Modifier.blockReveal(titleReveal, revealOffsetPx),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.track_count, item.trackCount),
                        modifier = Modifier.blockReveal(countReveal, revealOffsetPx),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showAlphabetRail) {
            AlphabetRail(
                available = sectionStarts.keys,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    sectionStarts[letter]?.let { index ->
                        scope.launch { gridState.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

/** 按标题（中文拼音）给集合条目做导航索引。 */
private fun collectionSectionStartIndexes(items: List<CollectionItem>): Map<String, Int> {
    val starts = linkedMapOf<String, Int>()
    items.forEachIndexed { index, item ->
        starts.getOrPut(sectionKeyOf(item.title)) { index }
    }
    return starts
}

@Composable
internal fun PinnedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = stringResource(R.string.pin),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun AlphabetRail(
    available: Set<String>,
    activeLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = remember { SECTION_LETTERS }
    val letterHeightPx = with(LocalDensity.current) { ALPHABET_LETTER_HEIGHT.toPx() }

    // 每个字母占固定高度，触摸位置可以直接换算成索引，拖到哪就跳到哪。
    fun letterAt(offsetY: Float): String =
        letters[(offsetY / letterHeightPx).toInt().coerceIn(0, letters.lastIndex)]

    Column(
        modifier = modifier
            .width(ALPHABET_RAIL_WIDTH)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onLetterSelected(letterAt(offset.y))
                    },
                ) { change, _ ->
                    change.consume()
                    onLetterSelected(letterAt(change.position.y))
                }
            }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    onLetterSelected(letterAt(offset.y))
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val hasTracks = letter in available
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALPHABET_LETTER_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        letter == activeLetter -> MaterialTheme.colorScheme.primary
                        hasTracks -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

internal val SECTION_LETTERS: List<String> = ('A'..'Z').map(Char::toString) + "#"
internal val ALPHABET_RAIL_WIDTH = 24.dp
private val ALPHABET_LETTER_HEIGHT = 17.dp
internal const val GRID_COLUMN_COUNT = 3

/**
 * 网格卡片从底部滑入时由小变大：刚露头时最小，完整进入视口后恢复原大小。
 * 用画布缩放而不是 graphicsLayer，不额外创建图层，低端设备上开销小得多。
 */
internal fun Modifier.gridScrollScale(
    gridState: LazyGridState,
    key: Any,
): Modifier = drawWithContent {
    // 按 key 找自己，而不是按下标：排序后下标会变，按 key 就不会因为下标变化而重组。
    val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    val scaleValue = listEntryScale(
        viewportEnd = gridState.layoutInfo.viewportEndOffset,
        itemOffset = itemInfo?.offset?.y,
        itemSize = itemInfo?.size?.height ?: 0,
    )
    if (scaleValue < 1f) {
        scale(scaleValue, scaleValue) { this@drawWithContent.drawContent() }
    } else {
        this@drawWithContent.drawContent()
    }
}

/**
 * 列表行同样从底部滑入时由小变大；缩放原点放在左侧中点，保证封面与文字左对齐。
 */
internal fun Modifier.listScrollScale(
    listState: LazyListState,
    key: Any,
): Modifier = drawWithContent {
    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    val scaleValue = listEntryScale(
        viewportEnd = listState.layoutInfo.viewportEndOffset,
        itemOffset = itemInfo?.offset,
        itemSize = itemInfo?.size ?: 0,
    )
    if (scaleValue < 1f) {
        scale(scaleValue, scaleValue, pivot = Offset(0f, size.height / 2f)) {
            this@drawWithContent.drawContent()
        }
    } else {
        this@drawWithContent.drawContent()
    }
}

/** 0 = 刚从视口底部露头，1 = 已经完整进入；对应 0.78 倍到 1 倍。 */
private fun listEntryScale(
    viewportEnd: Int,
    itemOffset: Int?,
    itemSize: Int,
): Float {
    if (itemOffset == null || itemSize <= 0) return 1f
    val progress = ((viewportEnd - itemOffset).toFloat() / itemSize).coerceIn(0f, 1f)
    return LIST_ITEM_MIN_SCALE + (1f - LIST_ITEM_MIN_SCALE) * progress
}

/** 条目刚从底部滑入时的缩放值，越接近 1 动效越轻微。 */
private const val LIST_ITEM_MIN_SCALE = 0.78f

/** 条目各块（封面 / 标题 / 数量等）浮现的时长。 */
internal const val BLOCK_REVEAL_MS = 220

/** 相邻两块的浮现间隔。 */
internal const val BLOCK_REVEAL_STAGGER_MS = 60L

/** 每块浮现时向上移动的距离。 */
internal val BLOCK_REVEAL_OFFSET = 8.dp

/** 按浮现进度淡入并上移：进度在绘制阶段读取，避免逐帧重组条目。 */
internal fun Modifier.blockReveal(
    progress: Animatable<Float, AnimationVector1D>,
    offsetPx: Float,
): Modifier = graphicsLayer {
    val value = progress.value
    alpha = value
    translationY = (1f - value) * offsetPx
}

/**
 * 每一块只在该行第一次组合时播放一次浮现；[order] 决定 60ms 的错开顺序，
 * 从上到下依次是封面、标题、数量。
 */
@Composable
internal fun rememberBlockReveal(
    enabled: Boolean,
    order: Int,
): Animatable<Float, AnimationVector1D> {
    val play = remember { enabled }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(play, order) {
        if (!play) return@LaunchedEffect
        if (order > 0) {
            delay(order * BLOCK_REVEAL_STAGGER_MS)
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = BLOCK_REVEAL_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    return progress
}
