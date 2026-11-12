package com.qymusic.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences

/**
 * 桌面播放组件入口。
 *
 * 状态由 [PlaybackService] 播放事件驱动推送（见 [push]），provider 自己只在
 * 系统要求重绘（组件被添加/桌面重启）时按缓存渲染：
 * - 进程活着：直接用 [lastState] / [lastCover]；
 * - 进程死了：从 SharedPreferences 恢复文本态，封面回落到应用图标。
 *
 * 封面 bitmap 与按钮 PendingIntent 都缓存在 companion 里，service 的
 * 1 秒进度 tick 只重建 RemoteViews，不重复解码与注册。
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {

        override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val state = lastState ?: restoreState(context) ?: WidgetPlaybackState()
            appWidgetManager.updateAppWidget(
                ComponentName(context, NowPlayingWidgetProvider::class.java),
                NowPlayingWidget.render(context, state, lastArtwork, buttons(context)),
            )
        }

    companion object {
        private const val PREFS_NAME = "now_playing_widget"
        private const val KEY_TRACK_ID = "trackId"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_POSITION = "positionMs"
        private const val KEY_DURATION = "durationMs"

        @Volatile
        private var lastState: WidgetPlaybackState? = null

        /** 封面位图 + 组件背景色，一起缓存一起失效。 */
        @Volatile
        private var lastArtwork: WidgetArtwork? = null

        /** 封面缓存键 = trackId + artwork 字节数，变了才重新解码。 */
        @Volatile
        private var lastCoverKey: String? = null

        /** 上一次持久化状态的特征串，避免进度 tick 反复写 SharedPreferences。 */
        @Volatile
        private var lastPersistedSignature: String? = null

        @Volatile
        private var cachedButtons: WidgetButtonIntents? = null

        private fun buttons(context: Context): WidgetButtonIntents =
            cachedButtons ?: NowPlayingWidget.buildButtonIntents(context).also {
                cachedButtons = it
            }

        /** 桌面上是否还有本组件实例；没有时 service 直接跳过推送与进度 tick。 */
        fun hasInstances(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NowPlayingWidgetProvider::class.java))
                .isNotEmpty()

        /**
         * 推送一帧状态并刷新所有组件实例。artwork/coverKey 只在封面解码完成时传入，
         * 进度 tick 复用 [lastArtwork] 即可。桌面上没有实例时只更新缓存不渲染，
         * 这样移除组件再重新添加也能拿到最新状态。
         */
        fun push(
            context: Context,
            state: WidgetPlaybackState,
            artwork: WidgetArtwork? = null,
            coverKey: String? = null,
        ) {
            lastState = state
            if (artwork != null && (coverKey != null || lastArtwork == null)) {
                lastArtwork = artwork
                lastCoverKey = coverKey
            }
            if (!hasInstances(context)) return
            val signature = listOf(
                state.trackId,
                state.title,
                state.artist,
                state.isPlaying.toString(),
                state.durationMs.toString(),
            ).joinToString("|")
            if (signature != lastPersistedSignature) {
                lastPersistedSignature = signature
                persist(context, state)
            }
            AppWidgetManager.getInstance(context).updateAppWidget(
                ComponentName(context, NowPlayingWidgetProvider::class.java),
                NowPlayingWidget.render(context, state, lastArtwork, buttons(context)),
            )
        }

        /** service 销毁时调用：回到空态并清掉持久化文本。 */
        fun pushEmpty(context: Context) {
            lastState = WidgetPlaybackState()
            lastArtwork = null
            lastCoverKey = null
            lastPersistedSignature = null
            persist(context, WidgetPlaybackState())
            if (!hasInstances(context)) return
            AppWidgetManager.getInstance(context).updateAppWidget(
                ComponentName(context, NowPlayingWidgetProvider::class.java),
                NowPlayingWidget.render(
                    context,
                    WidgetPlaybackState(),
                    null,
                    buttons(context),
                ),
            )
        }

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun persist(context: Context, state: WidgetPlaybackState) {
            prefs(context).edit()
                .putString(KEY_TRACK_ID, state.trackId)
                .putString(KEY_TITLE, state.title)
                .putString(KEY_ARTIST, state.artist)
                .putLong(KEY_POSITION, state.positionMs)
                .putLong(KEY_DURATION, state.durationMs)
                .apply()
        }

        private fun restoreState(context: Context): WidgetPlaybackState? {
            val prefs = prefs(context)
            val trackId = prefs.getString(KEY_TRACK_ID, null) ?: return null
            return WidgetPlaybackState(
                trackId = trackId,
                title = prefs.getString(KEY_TITLE, null),
                artist = prefs.getString(KEY_ARTIST, null),
                isPlaying = false,
                positionMs = prefs.getLong(KEY_POSITION, 0L),
                durationMs = prefs.getLong(KEY_DURATION, 0L),
            )
        }
    }
}
