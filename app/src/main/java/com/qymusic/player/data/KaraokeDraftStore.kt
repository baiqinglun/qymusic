package com.qymusic.player.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

enum class KaraokeVoiceEffect {
    STUDIO,
    HALL,
    DISTANT,
    ELECTRONIC,
}

enum class KaraokePublishSelection {
    FULL,
    SELECTED,
}

enum class KaraokeExportFormat {
    MP3,
    FLAC,
}

data class KaraokeEditProject(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val sourceUri: String,
    val voicePath: String,
    val durationMs: Long,
    val vocalVolume: Float = 1f,
    val musicVolume: Float = 0.72f,
    val vocalOffsetEnabled: Boolean = false,
    val vocalOffsetMs: Long = 0L,
    val effect: KaraokeVoiceEffect = KaraokeVoiceEffect.STUDIO,
    val publishSelection: KaraokePublishSelection = KaraokePublishSelection.FULL,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val exportFormat: KaraokeExportFormat = KaraokeExportFormat.FLAC,
    val exportTitle: String = trackTitle,
    val exportArtist: String = artist,
    val exportOriginalSinger: String = artist,
    val exportLyrics: String = "",
    val coverUri: String? = null,
    val draftId: String = "",
    val draftName: String = "",
    val recordedAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = recordedAtMs,
)

data class KaraokePublishState(
    val isPublishing: Boolean = false,
    val message: String? = null,
    val publishedPath: String? = null,
    val isError: Boolean = false,
)

class KaraokeDraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val draftDir = File(appContext.filesDir, DRAFT_DIRECTORY_NAME).apply {
        mkdirs()
    }
    private val legacyDraftFile = File(draftDir, LEGACY_DRAFT_FILE_NAME)
    private val legacyVoiceFile = File(draftDir, LEGACY_VOICE_FILE_NAME)

    fun loadAll(): List<KaraokeEditProject> {
        migrateLegacyDraft()
        return draftDir.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { directory -> readProject(directory, directory.name) }
            .sortedByDescending(KaraokeEditProject::recordedAtMs)
            .toList()
    }

    fun load(draftId: String): KaraokeEditProject? {
        migrateLegacyDraft()
        if (draftId.isBlank()) return null
        val directory = File(draftDir, draftId)
        if (!directory.isDirectory || !isDirectChild(directory)) return null
        return readProject(directory, draftId)
    }

    fun save(project: KaraokeEditProject): KaraokeEditProject {
        val normalized = project.normalized()
        val draftId = normalized.draftId
            .takeIf(::isValidDraftId)
            ?: UUID.randomUUID().toString()
        val directory = File(draftDir, draftId).apply {
            mkdirs()
        }
        val targetVoiceFile = File(directory, VOICE_FILE_NAME)
        val targetProjectFile = File(directory, PROJECT_FILE_NAME)

        val sourceVoice = File(normalized.voicePath)
        if (!sourceVoice.isFile) return normalized
        if (sourceVoice.canonicalPath != targetVoiceFile.canonicalPath) {
            sourceVoice.copyTo(targetVoiceFile, overwrite = true)
        }

        val saved = normalized.copy(
            draftId = draftId,
            draftName = normalized.draftName.ifBlank {
                buildDraftName(normalized.trackTitle, normalized.recordedAtMs)
            },
            voicePath = targetVoiceFile.absolutePath,
            updatedAtMs = normalized.updatedAtMs.coerceAtLeast(normalized.recordedAtMs),
        )
        targetProjectFile.writeText(saved.toJson().toString(2), Charsets.UTF_8)
        return saved
    }

    fun delete(draftIds: Set<String>) {
        draftIds
            .asSequence()
            .filter(::isValidDraftId)
            .map { File(draftDir, it) }
            .filter { it.isDirectory && isDirectChild(it) }
            .forEach(File::deleteRecursively)
    }

    private fun migrateLegacyDraft() {
        if (!legacyDraftFile.isFile) return
        val legacy = readProject(
            directory = draftDir,
            expectedDraftId = "",
            voiceOverride = legacyVoiceFile.takeIf(File::isFile),
        ) ?: return
        val migrated = save(
            legacy.copy(
                draftId = "",
                draftName = "",
                recordedAtMs = legacy.updatedAtMs,
            ),
        )
        if (File(migrated.voicePath).isFile) {
            legacyDraftFile.delete()
            legacyVoiceFile.delete()
        }
    }

    private fun readProject(
        directory: File,
        expectedDraftId: String,
        voiceOverride: File? = null,
    ): KaraokeEditProject? {
        val projectFile = File(directory, PROJECT_FILE_NAME)
        val legacyFile = File(directory, LEGACY_DRAFT_FILE_NAME)
        val sourceFile = when {
            projectFile.isFile -> projectFile
            legacyFile.isFile -> legacyFile
            else -> return null
        }
        return runCatching {
            val json = JSONObject(sourceFile.readText(Charsets.UTF_8))
            val voicePath = voiceOverride?.absolutePath
                ?: File(directory, VOICE_FILE_NAME).takeIf(File::isFile)?.absolutePath
                ?: json.optString(KEY_VOICE_PATH).takeIf { File(it).isFile }
                ?: return null
            val durationMs = json.optLong(KEY_DURATION_MS).coerceAtLeast(1L)
            val trimStartMs = json.optLong(KEY_TRIM_START_MS).coerceIn(0L, durationMs)
            val updatedAtMs = json.optLong(
                KEY_UPDATED_AT_MS,
                sourceFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
            val recordedAtMs = json.optLong(KEY_RECORDED_AT_MS, updatedAtMs)
            val draftId = json.optString(KEY_DRAFT_ID, expectedDraftId)
                .takeIf(::isValidDraftId)
                ?: expectedDraftId
            val trackTitle = json.optString(KEY_TRACK_TITLE)
            KaraokeEditProject(
                trackId = json.optString(KEY_TRACK_ID),
                trackTitle = trackTitle,
                artist = json.optString(KEY_ARTIST),
                sourceUri = json.optString(KEY_SOURCE_URI),
                voicePath = voicePath,
                durationMs = durationMs,
                vocalVolume = json.optDouble(KEY_VOCAL_VOLUME, 1.0).toFloat()
                    .coerceIn(0f, 2f),
                musicVolume = json.optDouble(KEY_MUSIC_VOLUME, 0.72).toFloat()
                    .coerceIn(0f, 2f),
                vocalOffsetEnabled = json.optBoolean(KEY_VOCAL_OFFSET_ENABLED, false),
                vocalOffsetMs = json.optLong(KEY_VOCAL_OFFSET_MS)
                    .coerceIn(MIN_VOCAL_OFFSET_MS, MAX_VOCAL_OFFSET_MS),
                effect = json.optString(KEY_EFFECT)
                    .let { value ->
                        KaraokeVoiceEffect.entries.firstOrNull { it.name == value }
                    }
                    ?: KaraokeVoiceEffect.STUDIO,
                publishSelection = json.optString(KEY_PUBLISH_SELECTION)
                    .let { value ->
                        KaraokePublishSelection.entries.firstOrNull { it.name == value }
                    }
                    ?: KaraokePublishSelection.FULL,
                trimStartMs = trimStartMs,
                trimEndMs = json.optLong(KEY_TRIM_END_MS, durationMs)
                    .coerceIn(trimStartMs, durationMs),
                exportFormat = json.optString(KEY_EXPORT_FORMAT)
                    .let { value ->
                        KaraokeExportFormat.entries.firstOrNull { it.name == value }
                    }
                    ?: KaraokeExportFormat.FLAC,
                exportTitle = json.optString(KEY_EXPORT_TITLE, trackTitle),
                exportArtist = json.optString(
                    KEY_EXPORT_ARTIST,
                    json.optString(KEY_ARTIST),
                ),
                exportOriginalSinger = json.optString(
                    KEY_EXPORT_ORIGINAL_SINGER,
                    json.optString(KEY_ARTIST),
                ),
                exportLyrics = json.optString(KEY_EXPORT_LYRICS),
                coverUri = json.optString(KEY_COVER_URI).takeIf { it.isNotBlank() },
                draftId = draftId,
                draftName = json.optString(KEY_DRAFT_NAME).ifBlank {
                    buildDraftName(trackTitle, recordedAtMs)
                },
                recordedAtMs = recordedAtMs,
                updatedAtMs = updatedAtMs,
            ).normalized()
        }.getOrNull()
    }

    private fun KaraokeEditProject.normalized(): KaraokeEditProject {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val safeRecordedAt = recordedAtMs.coerceAtLeast(0L)
        val safeStart = trimStartMs.coerceIn(0L, safeDuration)
        val safeEnd = trimEndMs.coerceIn(safeStart, safeDuration)
        return copy(
            durationMs = safeDuration,
            vocalVolume = vocalVolume.coerceIn(0f, 2f),
            musicVolume = musicVolume.coerceIn(0f, 2f),
            vocalOffsetMs = vocalOffsetMs.coerceIn(
                MIN_VOCAL_OFFSET_MS,
                MAX_VOCAL_OFFSET_MS,
            ),
            trimStartMs = safeStart,
            trimEndMs = safeEnd,
            draftName = draftName.ifBlank {
                buildDraftName(trackTitle, safeRecordedAt)
            },
            recordedAtMs = safeRecordedAt,
            updatedAtMs = updatedAtMs.coerceAtLeast(safeRecordedAt),
        )
    }

    private fun KaraokeEditProject.toJson(): JSONObject = JSONObject().apply {
        put(KEY_DRAFT_ID, draftId)
        put(KEY_DRAFT_NAME, draftName)
        put(KEY_RECORDED_AT_MS, recordedAtMs)
        put(KEY_TRACK_ID, trackId)
        put(KEY_TRACK_TITLE, trackTitle)
        put(KEY_ARTIST, artist)
        put(KEY_SOURCE_URI, sourceUri)
        put(KEY_VOICE_PATH, voicePath)
        put(KEY_DURATION_MS, durationMs)
        put(KEY_VOCAL_VOLUME, vocalVolume)
        put(KEY_MUSIC_VOLUME, musicVolume)
        put(KEY_VOCAL_OFFSET_ENABLED, vocalOffsetEnabled)
        put(KEY_VOCAL_OFFSET_MS, vocalOffsetMs)
        put(KEY_EFFECT, effect.name)
        put(KEY_PUBLISH_SELECTION, publishSelection.name)
        put(KEY_TRIM_START_MS, trimStartMs)
        put(KEY_TRIM_END_MS, trimEndMs)
        put(KEY_EXPORT_FORMAT, exportFormat.name)
        put(KEY_EXPORT_TITLE, exportTitle)
        put(KEY_EXPORT_ARTIST, exportArtist)
        put(KEY_EXPORT_ORIGINAL_SINGER, exportOriginalSinger)
        put(KEY_EXPORT_LYRICS, exportLyrics)
        coverUri?.let { put(KEY_COVER_URI, it) }
        put(KEY_UPDATED_AT_MS, updatedAtMs)
    }

    private fun isDirectChild(file: File): Boolean {
        val parent = runCatching { draftDir.canonicalFile }.getOrNull() ?: return false
        val child = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return child.parentFile == parent
    }

    companion object {
        const val MIN_VOCAL_OFFSET_MS = -2_000L
        const val MAX_VOCAL_OFFSET_MS = 2_000L

        fun buildDraftName(trackTitle: String, recordedAtMs: Long): String {
            val title = trackTitle.trim().ifBlank { "未命名歌曲" }
            return "$title ${formatRecordedAt(recordedAtMs)}"
        }

        fun formatRecordedAt(recordedAtMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(recordedAtMs))

        private fun isValidDraftId(draftId: String): Boolean =
            draftId.isNotBlank() &&
                draftId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

        private const val DRAFT_DIRECTORY_NAME = "karaoke_drafts"
        private const val PROJECT_FILE_NAME = "project.json"
        private const val VOICE_FILE_NAME = "voice.wav"
        private const val LEGACY_DRAFT_FILE_NAME = "current.json"
        private const val LEGACY_VOICE_FILE_NAME = "current_voice.wav"
        private const val KEY_DRAFT_ID = "draftId"
        private const val KEY_DRAFT_NAME = "draftName"
        private const val KEY_RECORDED_AT_MS = "recordedAtMs"
        private const val KEY_TRACK_ID = "trackId"
        private const val KEY_TRACK_TITLE = "trackTitle"
        private const val KEY_ARTIST = "artist"
        private const val KEY_SOURCE_URI = "sourceUri"
        private const val KEY_VOICE_PATH = "voicePath"
        private const val KEY_DURATION_MS = "durationMs"
        private const val KEY_VOCAL_VOLUME = "vocalVolume"
        private const val KEY_MUSIC_VOLUME = "musicVolume"
        private const val KEY_VOCAL_OFFSET_ENABLED = "vocalOffsetEnabled"
        private const val KEY_VOCAL_OFFSET_MS = "vocalOffsetMs"
        private const val KEY_EFFECT = "effect"
        private const val KEY_PUBLISH_SELECTION = "publishSelection"
        private const val KEY_TRIM_START_MS = "trimStartMs"
        private const val KEY_TRIM_END_MS = "trimEndMs"
        private const val KEY_EXPORT_FORMAT = "exportFormat"
        private const val KEY_EXPORT_TITLE = "exportTitle"
        private const val KEY_EXPORT_ARTIST = "exportArtist"
        private const val KEY_EXPORT_ORIGINAL_SINGER = "exportOriginalSinger"
        private const val KEY_EXPORT_LYRICS = "exportLyrics"
        private const val KEY_COVER_URI = "coverUri"
        private const val KEY_UPDATED_AT_MS = "updatedAtMs"
    }
}
