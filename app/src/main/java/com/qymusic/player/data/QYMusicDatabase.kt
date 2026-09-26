package com.qymusic.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

data class PlaybackSummary(
    val totalPlays: Long = 0L,
    val totalListenedMs: Long = 0L,
    val trackCount: Int = 0,
)

data class TrackPlaybackStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val playCount: Long,
    val listenedMs: Long,
    val lastPlayedAt: Long,
)

/** 一条播放历史，用于统计页的热力图聚合。 */
data class PlayHistoryEntry(
    val startedAt: Long,
    val listenedMs: Long,
)

data class UserPlaylist(
    val id: Long,
    val name: String,
    val trackIds: List<String>,
    /** 用户自定义封面（图片 URI 字符串），为空时用第一首歌的封面或默认封面。 */
    val coverUri: String? = null,
)

class QYMusicDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRACK_STATS (
                track_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                play_count INTEGER NOT NULL DEFAULT 0,
                listened_ms INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAY_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                listened_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                cover_uri TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_TRACKS (
                playlist_id INTEGER NOT NULL,
                track_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (playlist_id, track_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_play_history_started_at ON $TABLE_PLAY_HISTORY(started_at)",
        )
        createTrackCacheTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 只做增量迁移，避免升级时丢掉用户的歌单和播放统计。
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_PLAYLISTS ADD COLUMN $COLUMN_COVER_URI TEXT")
        }
        if (oldVersion < 3) {
            createTrackCacheTable(db)
        }
    }

    private fun createTrackCacheTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRACK_CACHE (
                track_id TEXT PRIMARY KEY,
                uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                folder_name TEXT NOT NULL DEFAULT '',
                relative_path TEXT NOT NULL DEFAULT '',
                lyric_uri TEXT,
                quality TEXT NOT NULL DEFAULT '',
                bitrate_kbps INTEGER NOT NULL DEFAULT 0,
                bit_depth INTEGER NOT NULL DEFAULT 0,
                sample_rate_hz INTEGER NOT NULL DEFAULT 0,
                channel_count INTEGER NOT NULL DEFAULT 0,
                file_size INTEGER NOT NULL DEFAULT 0,
                modified_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    fun recordPlayStart(track: Track, startedAt: Long) {
        inTransaction { db ->
            ensureTrackStatsRow(db, track)
            db.execSQL(
                """
                UPDATE $TABLE_TRACK_STATS
                SET play_count = play_count + 1,
                    title = ?,
                    artist = ?,
                    last_played_at = ?
                WHERE track_id = ?
                """.trimIndent(),
                arrayOf<Any?>(track.title, track.artist, startedAt, track.id),
            )
            db.insert(
                TABLE_PLAY_HISTORY,
                null,
                ContentValues().apply {
                    put(COLUMN_TRACK_ID, track.id)
                    put(COLUMN_STARTED_AT, startedAt)
                    put(COLUMN_LISTENED_MS, 0L)
                },
            )
        }
    }

    fun addListenedTime(track: Track, listenedMs: Long, historyStartedAt: Long?) {
        if (listenedMs <= 0L) return
        inTransaction { db ->
            ensureTrackStatsRow(db, track)
            db.execSQL(
                """
                UPDATE $TABLE_TRACK_STATS
                SET listened_ms = listened_ms + ?,
                    title = ?,
                    artist = ?
                WHERE track_id = ?
                """.trimIndent(),
                arrayOf<Any?>(listenedMs, track.title, track.artist, track.id),
            )
            if (historyStartedAt != null) {
                db.execSQL(
                    """
                    UPDATE $TABLE_PLAY_HISTORY
                    SET listened_ms = listened_ms + ?
                    WHERE track_id = ? AND started_at = ?
                    """.trimIndent(),
                    arrayOf<Any?>(listenedMs, track.id, historyStartedAt),
                )
            }
        }
    }

    fun getPlaybackSummary(): PlaybackSummary {
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*), COALESCE(SUM(play_count), 0), COALESCE(SUM(listened_ms), 0)
            FROM $TABLE_TRACK_STATS
            WHERE play_count > 0 OR listened_ms > 0
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return PlaybackSummary()
            return PlaybackSummary(
                trackCount = cursor.getInt(0),
                totalPlays = cursor.getLong(1),
                totalListenedMs = cursor.getLong(2),
            )
        }
    }

    fun getTrackStats(): List<TrackPlaybackStats> {
        val result = mutableListOf<TrackPlaybackStats>()
        readableDatabase.rawQuery(
            """
            SELECT track_id, title, artist, play_count, listened_ms, last_played_at
            FROM $TABLE_TRACK_STATS
            WHERE play_count > 0 OR listened_ms > 0
            ORDER BY last_played_at DESC, play_count DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TrackPlaybackStats(
                    trackId = cursor.getString(0),
                    title = cursor.getString(1),
                    artist = cursor.getString(2),
                    playCount = cursor.getLong(3),
                    listenedMs = cursor.getLong(4),
                    lastPlayedAt = cursor.getLong(5),
                )
            }
        }
        return result
    }

    /** 时间区间 [sinceMs, untilMs) 内的播放次数与总时长。 */
    fun getRangeSummary(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): PlaybackSummary {
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*), COALESCE(SUM(listened_ms), 0)
            FROM $TABLE_PLAY_HISTORY
            WHERE started_at >= ? AND started_at < ?
            """.trimIndent(),
            arrayOf(sinceMs.toString(), untilMs.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return PlaybackSummary()
            return PlaybackSummary(
                totalPlays = cursor.getLong(0),
                totalListenedMs = cursor.getLong(1),
            )
        }
    }

    /** 时间区间 [sinceMs, untilMs) 内的播放历史，用于按小时/天/月聚合热力图。 */
    fun getRangeHistory(sinceMs: Long, untilMs: Long = Long.MAX_VALUE): List<PlayHistoryEntry> {
        val result = mutableListOf<PlayHistoryEntry>()
        readableDatabase.rawQuery(
            """
            SELECT started_at, listened_ms
            FROM $TABLE_PLAY_HISTORY
            WHERE started_at >= ? AND started_at < ?
            ORDER BY started_at ASC
            """.trimIndent(),
            arrayOf(sinceMs.toString(), untilMs.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PlayHistoryEntry(
                    startedAt = cursor.getLong(0),
                    listenedMs = cursor.getLong(1),
                )
            }
        }
        return result
    }

    /** 时间区间 [sinceMs, untilMs) 内播放次数最多的歌曲。 */
    fun getRangeTopTracks(
        sinceMs: Long,
        untilMs: Long = Long.MAX_VALUE,
        limit: Int = TOP_TRACK_LIMIT,
    ): List<TrackPlaybackStats> {
        val result = mutableListOf<TrackPlaybackStats>()
        readableDatabase.rawQuery(
            """
            SELECT h.track_id,
                   COALESCE(s.title, '') AS title,
                   COALESCE(s.artist, '') AS artist,
                   COUNT(*) AS plays,
                   COALESCE(SUM(h.listened_ms), 0) AS listened,
                   MAX(h.started_at) AS last_played
            FROM $TABLE_PLAY_HISTORY h
            LEFT JOIN $TABLE_TRACK_STATS s ON s.track_id = h.track_id
            WHERE h.started_at >= ? AND h.started_at < ?
            GROUP BY h.track_id
            ORDER BY plays DESC, listened DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(sinceMs.toString(), untilMs.toString(), limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TrackPlaybackStats(
                    trackId = cursor.getString(0),
                    title = cursor.getString(1),
                    artist = cursor.getString(2),
                    playCount = cursor.getLong(3),
                    listenedMs = cursor.getLong(4),
                    lastPlayedAt = cursor.getLong(5),
                )
            }
        }
        return result
    }

    fun createPlaylist(name: String, initialTrackId: String? = null): Long {
        val now = System.currentTimeMillis()
        return inTransaction { db ->
            val playlistId = db.insert(
                TABLE_PLAYLISTS,
                null,
                ContentValues().apply {
                    put(COLUMN_NAME, name.trim())
                    put(COLUMN_CREATED_AT, now)
                },
            )
            if (initialTrackId != null) {
                insertPlaylistTrack(db, playlistId, initialTrackId, 0)
            }
            playlistId
        }
    }

    fun getPlaylists(): List<UserPlaylist> {
        val playlists = mutableListOf<Triple<Long, String, String?>>()
        readableDatabase.rawQuery(
            "SELECT id, name, $COLUMN_COVER_URI FROM $TABLE_PLAYLISTS ORDER BY created_at ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                playlists += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
            }
        }

        return playlists.map { (playlistId, name, coverUri) ->
            val trackIds = mutableListOf<String>()
            readableDatabase.rawQuery(
                """
                SELECT track_id
                FROM $TABLE_PLAYLIST_TRACKS
                WHERE playlist_id = ?
                ORDER BY position ASC
                """.trimIndent(),
                arrayOf(playlistId.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    trackIds += cursor.getString(0)
                }
            }
            UserPlaylist(
                id = playlistId,
                name = name,
                trackIds = trackIds,
                coverUri = coverUri,
            )
        }
    }

    fun setPlaylistCover(playlistId: Long, coverUri: String?) {
        writableDatabase.update(
            TABLE_PLAYLISTS,
            ContentValues().apply { put(COLUMN_COVER_URI, coverUri) },
            "$COLUMN_ID = ?",
            arrayOf(playlistId.toString()),
        )
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        writableDatabase.update(
            TABLE_PLAYLISTS,
            ContentValues().apply { put(COLUMN_NAME, name.trim()) },
            "$COLUMN_ID = ?",
            arrayOf(playlistId.toString()),
        )
    }

    /** 读取上次扫描结果，用来跳过没变化的文件。 */
    fun loadTrackCache(): Map<String, CachedTrack> {
        val result = mutableMapOf<String, CachedTrack>()
        readableDatabase.rawQuery(
            """
            SELECT track_id, uri, title, artist, album, duration_ms, folder_name,
                   relative_path, lyric_uri, quality, bitrate_kbps, bit_depth,
                   sample_rate_hz, channel_count, file_size, modified_at
            FROM $TABLE_TRACK_CACHE
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val track = Track(
                    id = id,
                    uri = Uri.parse(cursor.getString(1)),
                    title = cursor.getString(2),
                    artist = cursor.getString(3),
                    album = cursor.getString(4),
                    durationMs = cursor.getLong(5),
                    folderName = cursor.getString(6),
                    relativePath = cursor.getString(7),
                    lyricUri = cursor.getString(8)?.let(Uri::parse),
                    quality = cursor.getString(9),
                    bitrateKbps = cursor.getInt(10),
                    bitDepth = cursor.getInt(11),
                    sampleRateHz = cursor.getInt(12),
                    channelCount = cursor.getInt(13),
                )
                result[id] = CachedTrack(
                    track = track,
                    fileSize = cursor.getLong(14),
                    modifiedAt = cursor.getLong(15),
                )
            }
        }
        return result
    }

    /** 用最新一次扫描结果整体替换缓存。 */
    fun replaceTrackCache(entries: Collection<CachedTrack>) {
        inTransaction { db ->
            db.delete(TABLE_TRACK_CACHE, null, null)
            entries.forEach { entry ->
                val track = entry.track
                db.insertWithOnConflict(
                    TABLE_TRACK_CACHE,
                    null,
                    ContentValues().apply {
                        put(COLUMN_TRACK_ID, track.id)
                        put(COLUMN_URI, track.uri.toString())
                        put(COLUMN_TITLE, track.title)
                        put(COLUMN_ARTIST, track.artist)
                        put(COLUMN_ALBUM, track.album)
                        put(COLUMN_DURATION_MS, track.durationMs)
                        put(COLUMN_FOLDER_NAME, track.folderName)
                        put(COLUMN_RELATIVE_PATH, track.relativePath)
                        put(COLUMN_LYRIC_URI, track.lyricUri?.toString())
                        put(COLUMN_QUALITY, track.quality)
                        put(COLUMN_BITRATE_KBPS, track.bitrateKbps)
                        put(COLUMN_BIT_DEPTH, track.bitDepth)
                        put(COLUMN_SAMPLE_RATE_HZ, track.sampleRateHz)
                        put(COLUMN_CHANNEL_COUNT, track.channelCount)
                        put(COLUMN_FILE_SIZE, entry.fileSize)
                        put(COLUMN_MODIFIED_AT, entry.modifiedAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        inTransaction { db ->
            val nextPosition = db.rawQuery(
                "SELECT COALESCE(MAX(position), -1) + 1 FROM $TABLE_PLAYLIST_TRACKS WHERE playlist_id = ?",
                arrayOf(playlistId.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            insertPlaylistTrack(db, playlistId, trackId, nextPosition)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        inTransaction { db ->
            db.delete(TABLE_PLAYLIST_TRACKS, "$COLUMN_PLAYLIST_ID = ?", arrayOf(playlistId.toString()))
            db.delete(TABLE_PLAYLISTS, "$COLUMN_ID = ?", arrayOf(playlistId.toString()))
        }
    }

    private fun ensureTrackStatsRow(db: SQLiteDatabase, track: Track) {
        db.insertWithOnConflict(
            TABLE_TRACK_STATS,
            null,
            ContentValues().apply {
                put(COLUMN_TRACK_ID, track.id)
                put(COLUMN_TITLE, track.title)
                put(COLUMN_ARTIST, track.artist)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun insertPlaylistTrack(
        db: SQLiteDatabase,
        playlistId: Long,
        trackId: String,
        position: Int,
    ) {
        db.insertWithOnConflict(
            TABLE_PLAYLIST_TRACKS,
            null,
            ContentValues().apply {
                put(COLUMN_PLAYLIST_ID, playlistId)
                put(COLUMN_TRACK_ID, trackId)
                put(COLUMN_POSITION, position)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun <T> inTransaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "qy_music.db"
        const val DATABASE_VERSION = 3
        /** 统计页「播放次数前 N」的条数。 */
        const val TOP_TRACK_LIMIT = 20

        const val TABLE_TRACK_STATS = "track_stats"
        const val TABLE_PLAY_HISTORY = "play_history"
        const val TABLE_PLAYLISTS = "playlists"
        const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
        const val TABLE_TRACK_CACHE = "track_cache"

        const val COLUMN_ID = "id"
        const val COLUMN_COVER_URI = "cover_uri"
        const val COLUMN_URI = "uri"
        const val COLUMN_ALBUM = "album"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_FOLDER_NAME = "folder_name"
        const val COLUMN_RELATIVE_PATH = "relative_path"
        const val COLUMN_LYRIC_URI = "lyric_uri"
        const val COLUMN_QUALITY = "quality"
        const val COLUMN_BITRATE_KBPS = "bitrate_kbps"
        const val COLUMN_BIT_DEPTH = "bit_depth"
        const val COLUMN_SAMPLE_RATE_HZ = "sample_rate_hz"
        const val COLUMN_CHANNEL_COUNT = "channel_count"
        const val COLUMN_FILE_SIZE = "file_size"
        const val COLUMN_MODIFIED_AT = "modified_at"
        const val COLUMN_TRACK_ID = "track_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_ARTIST = "artist"
        const val COLUMN_PLAY_COUNT = "play_count"
        const val COLUMN_LISTENED_MS = "listened_ms"
        const val COLUMN_LAST_PLAYED_AT = "last_played_at"
        const val COLUMN_STARTED_AT = "started_at"
        const val COLUMN_NAME = "name"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_PLAYLIST_ID = "playlist_id"
        const val COLUMN_POSITION = "position"
    }
}
