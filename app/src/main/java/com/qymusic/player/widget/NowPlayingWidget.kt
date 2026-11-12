package com.qymusic.player.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.qymusic.player.MainActivity
import com.qymusic.player.R
import com.qymusic.player.playback.PlaybackService
import com.qymusic.player.ui.averageCoverColor
import java.util.Locale

/** 推给桌面组件的一帧播放状态快照。 */
data class WidgetPlaybackState(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val hasTrack: Boolean
        get() = !trackId.isNullOrBlank()
}

/** 封面位图 + 由封面算出的组件背景色（与播放页同源），随封面一起缓存。 */
class WidgetArtwork(
    val cover: Bitmap,
    val backgroundColor: Int,
)

/** 组件上的全部点击目的地。 */
class WidgetButtonIntents(
    val openApp: PendingIntent,
    val play: PendingIntent,
    val pause: PendingIntent,
    val previous: PendingIntent,
    val next: PendingIntent,
)

object NowPlayingWidget {

    /** 播放页模糊背景的压暗系数（CoverBlur.BLURRED_COVER_DARKEN），组件背景同源。 */
    private const val WIDGET_BG_DARKEN = 0.66f


    /** 毫秒 → m:ss；超过 1 小时也用分钟累加（75:32），与主流播放器组件一致。 */
    fun formatTimeMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1000L
        return String.format(
            Locale.US,
            "%d:%02d",
            totalSeconds / 60L,
            totalSeconds % 60L,
        )
    }

    /**
     * 构建 4 个媒体按键的 PendingIntent。
     *
     * ACTION_MEDIA_BUTTON + EXTRA_KEY_EVENT 直连 PlaybackService（服务 exported=true，
     * 启动器可代发）：由 MediaSessionService.onStartCommand 交给 session 默认回调，
     * KEYCODE_MEDIA_PLAY/PAUSE/NEXT/PREVIOUS 映射成 play()/pause()/
     * seekToNextMediaItem()/seekToPreviousMediaItem()；空队列时走冷启动恢复。
     *
     * 用 getForegroundService（API 26+）：App 进程不在时点组件属于后台启动服务，
     * plain getService 会被 "Background start not allowed" 静默拒绝；前台服务启动
     * 配合 mediaPlayback 类型（manifest 已声明）是系统媒体通知同款豁免路径。
     * requestCode 用 keyCode 区分，保证 PLAY 与 PAUSE 是两个独立 PendingIntent。
     */
    fun buildButtonIntents(context: Context): WidgetButtonIntents {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun mediaButton(keyCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setClassName(context, PlaybackService::class.java.name)
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, keyCode),
                )
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, keyCode, intent, flags)
            } else {
                PendingIntent.getService(context, keyCode, intent, flags)
            }
        }
        return WidgetButtonIntents(
            openApp = openApp,
            play = mediaButton(KeyEvent.KEYCODE_MEDIA_PLAY),
            pause = mediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE),
            previous = mediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            next = mediaButton(KeyEvent.KEYCODE_MEDIA_NEXT),
        )
    }

    /**
     * 渲染一帧 RemoteViews。
     *
     * artwork 为 null 时不写封面 view——布局初始 src 是应用图标，于是空态/冷启动
     * 自然回落到图标占位；RemoteViews 每次都会重新 inflate，不写即保留初始值。
     */
    fun render(
        context: Context,
        state: WidgetPlaybackState,
        artwork: WidgetArtwork?,
        buttons: WidgetButtonIntents,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
        // 整块组件点按回 App；按钮/封面的子 view 点击会覆盖在 root 上，互不冲突。
        views.setOnClickPendingIntent(R.id.widget_root, buttons.openApp)
        // 封面圆角靠 view outline 裁切（背景 shape 提供 outline），
        // 任意宽高比下 centerCrop 都能保持圆角，bitmap 不必预裁。
        views.setBoolean(R.id.widget_cover, "setClipToOutline", true)
        // 背景色与播放页同源（封面均色压暗），铺在圆角 shape 上做 tint，圆角保留。
        // setBackgroundTintList 是 API 31+ 的 RemoteViews 能力，低版本维持默认深色底。
        if (artwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                android.content.res.ColorStateList.valueOf(artwork.backgroundColor),
            )
        }

        if (!state.hasTrack) {
            views.setViewVisibility(R.id.widget_controls_area, View.GONE)
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            views.setTextViewText(
                R.id.widget_artist,
                context.getString(R.string.widget_idle_artist),
            )
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
            views.setTextViewText(R.id.widget_time_elapsed, formatTimeMs(0L))
            views.setTextViewText(R.id.widget_time_total, formatTimeMs(0L))
            return views
        }

        views.setViewVisibility(R.id.widget_controls_area, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, state.title.orEmpty())
        views.setTextViewText(R.id.widget_artist, state.artist.orEmpty())
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_cover, artwork.cover)
        }
        views.setImageViewResource(
            R.id.widget_btn_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play,
            if (state.isPlaying) buttons.pause else buttons.play,
        )
        views.setOnClickPendingIntent(R.id.widget_btn_prev, buttons.previous)
        views.setOnClickPendingIntent(R.id.widget_btn_next, buttons.next)

        val duration = state.durationMs.coerceAtLeast(0L)
        val progress = if (duration > 0L) {
            (state.positionMs.coerceAtLeast(0L) * 1000L / duration)
                .toInt()
                .coerceIn(0, 1000)
        } else {
            0
        }
        views.setProgressBar(R.id.widget_progress, 1000, progress, false)
        views.setTextViewText(R.id.widget_time_elapsed, formatTimeMs(state.positionMs))
        views.setTextViewText(R.id.widget_time_total, formatTimeMs(duration))
        return views
    }

    /**
     * 解码 artworkData 并把最长边压到 maxSide 以内（封面列约 40% 宽、撑满
     * 组件高，取 512 兜住 4x 密度），产出封面位图 + 组件背景色。
     *
     * 圆角不在 bitmap 上裁：封面 view 的背景 shape 提供 outline，
     * setClipToOutline 后 centerCrop 任意比例都保持圆角。
     * 背景色 = 封面均色（复用播放页的 [averageCoverColor]）按播放页背景的
     * 0.66 系数压暗——播放页背景是模糊封面图 × BLURRED_COVER_DARKEN，
     * 均色压暗是它的纯色等价。
     */
    fun decodeArtwork(data: ByteArray, maxSide: Int = 512): WidgetArtwork? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val average = decoded.averageCoverColor()
        val background = android.graphics.Color.rgb(
            (average.red * 255f * WIDGET_BG_DARKEN).toInt().coerceIn(0, 255),
            (average.green * 255f * WIDGET_BG_DARKEN).toInt().coerceIn(0, 255),
            (average.blue * 255f * WIDGET_BG_DARKEN).toInt().coerceIn(0, 255),
        )
        return WidgetArtwork(decoded, background)
    }

    /**
     * 从本地音频（file/content uri）读内嵌封面的原始字节，与应用内
     * MusicViewModel.readEmbeddedPicture 同一套取法；在线流/无内嵌图返回 null。
     */
    fun readEmbeddedArtworkBytes(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
