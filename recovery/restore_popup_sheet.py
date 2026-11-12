# -*- coding: utf-8 -*-
# 抽屉弹窗回退为整体滑入式弹窗（之前的弹窗形式）
import io, re

p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
s = io.open(p, encoding='utf-8').read()

# ---------- 1. 常量块：抽屉磁吸常量 -> 滑入式常量 ----------
old = """/** 底部弹窗升起 / 磁吸 / 下滑关闭共用：ease-out 曲线，约 250ms。 */
private val SheetEaseOut = tween<Float>(durationMillis = 250, easing = EaseOutCubic)
/** 展开时背后页面变圆角的最大值与压暗强度。 */
private const val BACKDROP_CORNER_DP = 28f
private const val BACKDROP_SCRIM_ALPHA = 0.34f
/** 磁吸档位：抽屉高度占屏比（上拉到最高档时底边仍与屏幕底边平齐）。 */
private val SHEET_SNAP_FRACTIONS = listOf(0.30f, 0.50f, 0.70f)
/** 抽屉高度下限：低于此档继续下拉的累计量超阈值即关闭。 */
private const val SHEET_MIN_FRACTION = 0.12f
/** 抽屉高度上限。 */
private const val SHEET_MAX_FRACTION = 0.72f
/** 低于最低档继续下拉的累计量超过该值（px）即关闭。 */
private const val SHEET_CLOSE_OVERDRAG_PX = 140f"""
new = """/** 底部弹窗升起 / 下滑关闭共用：ease-out 曲线，约 250ms。 */
private val SheetEaseOut = tween<Float>(durationMillis = 250, easing = EaseOutCubic)
/** 弹窗内容自适应高度，但最高不超过屏高的这个比例，超高部分内部滚动。 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.85f
/** 下拉甩动关闭的速度阈值（px/s）。 */
private const val SHEET_FLING_CLOSE_DP = 350f
/** progress 安全上限（正常拖动只降不升，防嵌套滚动边界把进度顶过 1）。 */
private const val SHEET_MAX_PROGRESS = 1.15f"""
assert old in s, 'constants block not found'
s = s.replace(old, new, 1)

# ---------- 2. PlaybackBottomSheet：抽屉 -> 滑入式 ----------
start_marker = '@Composable\nprivate fun PlaybackBottomSheet('
end_marker = '@Composable\nprivate fun SheetDragHandle() {'
start = s.index(start_marker)
end = s.index(end_marker)

new_fn = '''@Composable
private fun PlaybackBottomSheet(
    onDismiss: () -> Unit,
    blurredCover: Bitmap,
    playbackScheme: ColorScheme,
    title: String? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // 0 = 完全在屏下，1 = 就位。拖动要在手势回调里非挂起写值，
    // 所以进度本体是 FloatState，动画用 Animatable 桥接、逐帧写回。
    val progress = remember { mutableFloatStateOf(0f) }
    val progressAnim = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    // 升起：从屏下 ease-out 滑到就位（约 250ms）。
    LaunchedEffect(Unit) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(1f, SheetEaseOut) { progress.floatValue = value }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 滑动基准距离：用整屏高，保证初始完全在屏外。
        val slidePx = constraints.maxHeight.toFloat()
        val flingClosePx = with(density) { SHEET_FLING_CLOSE_DP.dp.toPx() }
        val maxHeightPx = constraints.maxHeight * SHEET_MAX_HEIGHT_FRACTION

        fun close() {
            if (dismissed) return
            dismissed = true
            scope.launch {
                progressAnim.snapTo(progress.floatValue)
                progressAnim.animateTo(0f, SheetEaseOut) { progress.floatValue = value }
                onDismiss()
            }
        }
        fun settle(flingPxPerS: Float) {
            if (dismissed) return
            val offsetPx = (1f - progress.floatValue) * slidePx
            if (offsetPx > contentHeightPx / 3f || flingPxPerS > flingClosePx) {
                close()
            } else {
                scope.launch {
                    progressAnim.snapTo(progress.floatValue)
                    progressAnim.animateTo(1f, SheetEaseOut) { progress.floatValue = value }
                }
            }
        }
        fun dragBy(dyPx: Float) {
            if (dismissed) return
            progress.floatValue =
                (progress.floatValue - dyPx / slidePx).coerceIn(0f, SHEET_MAX_PROGRESS)
        }

        // 可滚动内容：滚到顶后继续向下拖，把位移转给弹窗整体（与手势拖动同一套关闭判定）。
        val nestedConnection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !dismissed && progress.value <= 1.0001f) {
                    val before = progress.value
                    dragBy(available.y)
                    return Offset(0f, (before - progress.value) * slidePx)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 0f && !dismissed) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }

        val dragModifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    // 抓住弹窗时若升起动画还在跑，先停掉，避免动画每帧覆写 progress 不跟手。
                    scope.launch { progressAnim.stop() }
                },
                onDragEnd = {
                    settle(velocityTracker.calculateVelocity().y)
                },
                onDragCancel = { settle(0f) },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    // 下拉 1:1 跟手；上拉不做整体位移（交给内部列表自己滚）。
                    if (dragAmount > 0f) dragBy(dragAmount)
                },
            )
        }

        // 背板压暗：点击空白处关闭，透明度跟进度走（含升起 / 拖动 / 下滑全程）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.42f))
                .pointerInput(Unit) { detectTapGestures { close() } },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = with(density) { maxHeightPx.toDp() })
                .navigationBarsPadding()
                .graphicsLayer { translationY = (1f - progress.value) * slidePx }
                .onSizeChanged { contentHeightPx = it.height }
                .nestedScroll(nestedConnection)
                .then(dragModifier),
        ) {
            MaterialTheme(colorScheme = playbackScheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
                ) {
                    Box(modifier = Modifier.matchParentSize()) {
                        CoverBackdrop(bitmap = blurredCover)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (scrollable) {
                                    Modifier.verticalScroll(scrollState)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp),
                    ) {
                        SheetDragHandle()
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        CompositionLocalProvider(
                            LocalSheetClose provides {
                                close()
                            },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

'''
s = s[:start] + new_fn + s[end:]

# ---------- 3. 调用点：删掉 initialFraction 行 ----------
lines = s.split('\n')
out = [l for l in lines if not re.match(r'^\s*initialFraction = .+,$', l)]
assert len(lines) - len(out) == 3, (len(lines), len(out))
s = '\n'.join(out)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok')
