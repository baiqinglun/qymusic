package com.qymusic.player.playback

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.KaraokeExportFormat
import com.qymusic.player.data.KaraokePublishSelection
import com.qymusic.player.data.KaraokeVoiceEffect
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** 将干声和原曲伴奏按预览参数离线混合为一个 WAV 作品。 */
class KaraokeMixExporter(context: Context) {
    private val appContext = context.applicationContext
    private val audioEncoder = KaraokeAudioEncoder(context)

    fun publish(
        project: KaraokeEditProject,
        outputTreeUri: String?,
    ): String {
        val tempDir = File(appContext.cacheDir, "karaoke_export").apply { mkdirs() }
        val sourcePcm = File(tempDir, "source-${System.currentTimeMillis()}.pcm")
        val mixedWav = File(tempDir, "mixed-${System.currentTimeMillis()}.wav")
        val encoded = File(
            tempDir,
            "generated-${System.currentTimeMillis()}.${project.exportFormat.extension()}",
        )
        return try {
            val sourceSampleRate = decodeSourcePcm(
                sourceUri = Uri.parse(project.sourceUri),
                output = sourcePcm,
            )
            mixWav(
                project = project,
                sourcePcm = sourcePcm,
                sourceSampleRate = sourceSampleRate,
                output = mixedWav,
            )
            audioEncoder.encode(
                wavFile = mixedWav,
                output = encoded,
                project = project,
            )
            publishFile(encoded, project.exportFormat, outputTreeUri)
        } finally {
            sourcePcm.delete()
            mixedWav.delete()
            encoded.delete()
        }
    }

    private fun decodeSourcePcm(
        sourceUri: Uri,
        output: File,
    ): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, sourceUri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("原曲没有可解码的音轨")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("无法读取音频格式")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getPositiveIntegerOrDefault(
                MediaFormat.KEY_SAMPLE_RATE,
                DEFAULT_SAMPLE_RATE,
            )
            var channelCount = inputFormat.getIntegerOrDefault(
                MediaFormat.KEY_CHANNEL_COUNT,
                2,
            )
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            val bufferInfo = MediaCodec.BufferInfo()
            BufferedOutputStream(FileOutputStream(output)).use { pcmOutput ->
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: error("解码输入缓冲区不可用")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0,
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(
                        bufferInfo,
                        CODEC_TIMEOUT_US,
                    )) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec.outputFormat
                            sampleRate = format.getPositiveIntegerOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE,
                                sampleRate,
                            )
                            channelCount = format.getIntegerOrDefault(
                                MediaFormat.KEY_CHANNEL_COUNT,
                                channelCount,
                            )
                            pcmEncoding = format.getIntegerOrDefault(
                                MediaFormat.KEY_PCM_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT,
                            )
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (
                                outputBuffer != null &&
                                bufferInfo.size > 0 &&
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                            ) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                writeStereoPcm16(
                                    output = pcmOutput,
                                    buffer = outputBuffer,
                                    channelCount = channelCount.coerceAtLeast(1),
                                    pcmEncoding = pcmEncoding,
                                )
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (
                                bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            ) {
                                outputEnded = true
                            }
                        }
                    }
                }
            }
            return sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun writeStereoPcm16(
        output: BufferedOutputStream,
        buffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
    ) {
        val ordered = buffer.order(ByteOrder.LITTLE_ENDIAN)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = ordered.asFloatBuffer()
                val frames = floats.remaining() / channelCount
                repeat(frames) {
                    val left = floats.get()
                    val right = if (channelCount > 1) floats.get() else left
                    repeat(channelCount - 2.coerceAtLeast(0)) { floats.get() }
                    writeShortLe(output, (left * Short.MAX_VALUE).roundToInt())
                    writeShortLe(output, (right * Short.MAX_VALUE).roundToInt())
                }
            }

            else -> {
                val shorts = ordered.asShortBuffer()
                val frames = shorts.remaining() / channelCount
                repeat(frames) {
                    val left = shorts.get().toInt()
                    val right = if (channelCount > 1) shorts.get().toInt() else left
                    repeat(channelCount - 2.coerceAtLeast(0)) { shorts.get() }
                    writeShortLe(output, left)
                    writeShortLe(output, right)
                }
            }
        }
    }

    private fun mixWav(
        project: KaraokeEditProject,
        sourcePcm: File,
        sourceSampleRate: Int,
        output: File,
    ) {
        val selectionStart = if (
            project.publishSelection == KaraokePublishSelection.SELECTED
        ) {
            project.trimStartMs.coerceIn(0L, project.durationMs)
        } else {
            0L
        }
        val selectionEnd = if (
            project.publishSelection == KaraokePublishSelection.SELECTED
        ) {
            project.trimEndMs.coerceIn(selectionStart, project.durationMs)
        } else {
            project.durationMs
        }
        val outputFrames = ((selectionEnd - selectionStart)
            .coerceAtLeast(0L) * sourceSampleRate / 1_000L)
            .toInt()
        val sourceStartFrame = selectionStart * sourceSampleRate / 1_000L
        val voicePcm = loadWavPcm16(File(project.voicePath))
        val voiceSampleRate = VOICE_SAMPLE_RATE
        val delayFrames = max(
            MIN_DELAY_FRAMES,
            (sourceSampleRate * project.effect.delayMs() / 1_000f).roundToInt(),
        )
        val delayBuffer = FloatArray(delayFrames)
        var delayIndex = 0
        var lowPass = 0f
        val effectiveOffsetMs = if (project.vocalOffsetEnabled) {
            project.vocalOffsetMs
        } else {
            0L
        }

        RandomAccessFile(output, "rw").use { outputFile ->
            outputFile.setLength(0L)
            outputFile.write(ByteArray(WAV_HEADER_BYTES))
            BufferedOutputStream(FileOutputStream(output, true)).use { wavOutput ->
                val sourceStream = FileInputStream(sourcePcm).channel
                sourceStream.position(
                    sourceStartFrame * STEREO_PCM_FRAME_BYTES,
                )
                val sourceInput = BufferedInputStream(
                    java.nio.channels.Channels.newInputStream(sourceStream),
                )
                val sourceChunk = ByteArray(SOURCE_READ_BYTES)
                var remainingFrames = outputFrames
                var frameIndex = 0L
                while (remainingFrames > 0) {
                    val bytesToRead = minOf(
                        sourceChunk.size,
                        remainingFrames * STEREO_PCM_FRAME_BYTES,
                    )
                    val read = sourceInput.read(sourceChunk, 0, bytesToRead)
                    if (read <= 0) {
                        repeat(remainingFrames) {
                            writeStereoFrame(wavOutput, 0, 0)
                        }
                        break
                    }
                    val framesInChunk = read / STEREO_PCM_FRAME_BYTES
                    repeat(framesInChunk) { chunkFrame ->
                        val offset = chunkFrame * STEREO_PCM_FRAME_BYTES
                        val sourceLeft = readShortLe(sourceChunk, offset) / 32768f
                        val sourceRight = readShortLe(sourceChunk, offset + 2) / 32768f
                        val songTimeMs = selectionStart +
                            (frameIndex * 1_000L / sourceSampleRate)
                        val voiceSample = sampleVoice(
                            voicePcm = voicePcm,
                            voiceSampleRate = voiceSampleRate,
                            timeMs = songTimeMs - effectiveOffsetMs,
                        )
                        val effectedVoice = applyEffect(
                            effect = project.effect,
                            dry = voiceSample,
                            delayBuffer = delayBuffer,
                            delayIndex = delayIndex,
                            songTimeMs = songTimeMs,
                            lowPass = lowPass,
                        )
                        lowPass = effectedVoice.lowPass
                        delayIndex = if (delayFrames == 0) {
                            0
                        } else {
                            (delayIndex + 1) % delayFrames
                        }
                        val mixedLeft = sourceLeft * project.musicVolume +
                            effectedVoice.sample * project.vocalVolume
                        val mixedRight = sourceRight * project.musicVolume +
                            effectedVoice.sample * project.vocalVolume
                        writeStereoFrame(
                            output = wavOutput,
                            left = (mixedLeft.coerceIn(-1f, 1f) * Short.MAX_VALUE)
                                .roundToInt(),
                            right = (mixedRight.coerceIn(-1f, 1f) * Short.MAX_VALUE)
                                .roundToInt(),
                        )
                        frameIndex += 1
                    }
                    remainingFrames -= framesInChunk
                }
            }
            val dataBytes = outputFrames.toLong() * STEREO_PCM_FRAME_BYTES
            outputFile.seek(0L)
            outputFile.write(wavHeader(dataBytes, sourceSampleRate, channels = 2))
        }
    }

    private fun applyEffect(
        effect: KaraokeVoiceEffect,
        dry: Float,
        delayBuffer: FloatArray,
        delayIndex: Int,
        songTimeMs: Long,
        lowPass: Float,
    ): EffectedSample {
        if (delayBuffer.isEmpty()) return EffectedSample(dry, 0f)
        val delayed = delayBuffer[delayIndex]
        val newLowPass = lowPass * 0.78f + dry * 0.22f
        val source = when (effect) {
            KaraokeVoiceEffect.DISTANT -> newLowPass
            else -> dry
        }
        val wet = when (effect) {
            KaraokeVoiceEffect.STUDIO -> delayed * 0.10f
            KaraokeVoiceEffect.HALL -> delayed * 0.32f
            KaraokeVoiceEffect.DISTANT -> delayed * 0.45f
            KaraokeVoiceEffect.ELECTRONIC -> {
                val tremolo = 0.68f +
                    0.32f * sin(2f * PI.toFloat() * 7f * songTimeMs / 1_000f)
                source * tremolo + delayed * 0.24f
            }
        }
        delayBuffer[delayIndex] = dry.coerceIn(-1f, 1f)
        return EffectedSample(
            sample = (source + wet).coerceIn(-1f, 1f),
            lowPass = newLowPass,
        )
    }

    private fun sampleVoice(
        voicePcm: ShortArray,
        voiceSampleRate: Int,
        timeMs: Long,
    ): Float {
        if (voicePcm.isEmpty() || timeMs < 0L) return 0f
        val position = timeMs * voiceSampleRate / 1_000L
        val firstIndex = position.toInt()
        if (firstIndex >= voicePcm.size - 1) return 0f
        val fraction = (position - firstIndex).toFloat()
        val first = voicePcm[firstIndex] / 32768f
        val second = voicePcm[(firstIndex + 1).coerceAtMost(voicePcm.lastIndex)] / 32768f
        return first + (second - first) * fraction
    }

    private fun loadWavPcm16(file: File): ShortArray {
        val bytes = file.readBytes()
        val dataOffset = findWavDataOffset(bytes)
        if (dataOffset < 0 || bytes.size - dataOffset < 2) return ShortArray(0)
        val buffer = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset)
            .slice()
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }

    private fun findWavDataOffset(bytes: ByteArray): Int {
        var index = 12
        while (index + 8 <= bytes.size) {
            val id = String(bytes, index, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, index + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            if (id == "data") return index + 8
            index += 8 + size.coerceAtLeast(0)
        }
        return -1
    }

    private fun publishFile(
        source: File,
        format: KaraokeExportFormat,
        outputTreeUri: String?,
    ): String {
        val fileName = "QYMusic_Generated_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            ".${format.extension()}"
        if (!outputTreeUri.isNullOrBlank()) {
            val tree = DocumentFile.fromTreeUri(
                appContext,
                Uri.parse(outputTreeUri),
            ) ?: error("无法访问生成目录")
            val target = tree.createFile(format.mimeType(), fileName)
                ?: error("无法在所选目录创建文件")
            runCatching {
                appContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("无法写入生成文件")
            }.onFailure {
                target.delete()
                throw it
            }
            return target.uri.toString()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType())
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/$PUBLIC_DIRECTORY_NAME",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: error("无法创建发布文件")
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("无法写入发布文件")
            }.onFailure {
                resolver.delete(uri, null, null)
                throw it
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }

        val directory = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            PUBLIC_DIRECTORY_NAME,
        ).apply { mkdirs() }
        val target = File(directory, fileName)
        source.copyTo(target, overwrite = true)
        return target.absolutePath
    }

    private fun writeStereoFrame(
        output: BufferedOutputStream,
        left: Int,
        right: Int,
    ) {
        writeShortLe(output, left)
        writeShortLe(output, right)
    }

    private fun writeShortLe(output: BufferedOutputStream, value: Int) {
        val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        output.write(clamped and 0xFF)
        output.write((clamped shr 8) and 0xFF)
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) or
            (bytes[offset + 1].toInt() shl 8)).toShort().toInt()

    private fun wavHeader(
        dataBytes: Long,
        sampleRate: Int,
        channels: Int,
    ): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        return ByteBuffer.allocate(WAV_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((dataBytes + WAV_HEADER_BYTES - 8L).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort((channels * bitsPerSample / 8).toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }
            .array()
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, defaultValue: Int): Int =
        if (containsKey(key)) getInteger(key) else defaultValue

    private fun MediaFormat.getPositiveIntegerOrDefault(
        key: String,
        defaultValue: Int,
    ): Int = getIntegerOrDefault(key, defaultValue).takeIf { it > 0 } ?: defaultValue

    private fun KaraokeVoiceEffect.delayMs(): Int = when (this) {
        KaraokeVoiceEffect.STUDIO -> 18
        KaraokeVoiceEffect.HALL -> 72
        KaraokeVoiceEffect.DISTANT -> 110
        KaraokeVoiceEffect.ELECTRONIC -> 36
    }

    private fun KaraokeExportFormat.extension(): String = when (this) {
        KaraokeExportFormat.MP3 -> "mp3"
        KaraokeExportFormat.FLAC -> "flac"
    }

    private fun KaraokeExportFormat.mimeType(): String = when (this) {
        KaraokeExportFormat.MP3 -> "audio/mpeg"
        KaraokeExportFormat.FLAC -> "audio/flac"
    }

    private data class EffectedSample(
        val sample: Float,
        val lowPass: Float,
    )

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MIN_SAMPLE_RATE = 8_000
        const val VOICE_SAMPLE_RATE = 44_100
        const val WAV_HEADER_BYTES = 44
        const val STEREO_PCM_FRAME_BYTES = 4
        const val SOURCE_READ_BYTES = 64 * 1_024
        const val MIN_DELAY_FRAMES = 1
        const val PUBLIC_DIRECTORY_NAME = "QYMusic"
    }
}
