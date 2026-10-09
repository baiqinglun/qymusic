package com.qymusic.player.playback

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.KaraokeExportFormat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Converts the mixed PCM WAV into the selected delivery format.
 *
 * FLAC is written directly so it does not depend on vendor codec availability.
 * MP3 uses MediaCodec because Android does not expose a public MP3 encoder API.
 */
class KaraokeAudioEncoder(context: Context) {
    private val appContext = context.applicationContext

    fun encode(
        wavFile: File,
        output: File,
        project: KaraokeEditProject,
    ) {
        when (project.exportFormat) {
            KaraokeExportFormat.FLAC -> encodeFlac(wavFile, output, project)
            KaraokeExportFormat.MP3 -> encodeMp3(wavFile, output, project)
        }
    }

    private fun encodeFlac(
        wavFile: File,
        output: File,
        project: KaraokeEditProject,
    ) {
        val wav = readWav(wavFile)
        val cover = readCover(project.coverUri)
        BufferedOutputStream(FileOutputStream(output)).use { flacOutput ->
            writeFlacMetadata(
                output = flacOutput,
                sampleRate = wav.sampleRate,
                channels = wav.channels,
                totalSamples = wav.samples.size / wav.channels,
                comments = project.vorbisComments(),
                cover = cover,
            )
            writeFlacFrames(
                output = flacOutput,
                samples = wav.samples,
                channels = wav.channels,
                blockSize = FLAC_BLOCK_SIZE,
            )
        }
    }

    private fun encodeMp3(
        wavFile: File,
        output: File,
        project: KaraokeEditProject,
    ) {
        val wavOffset = findWavDataOffset(wavFile.readBytes().copyOfRange(0, minOf(64, wavFile.length().toInt())))
        val dataOffset = if (wavOffset >= 0) wavOffset else WAV_HEADER_BYTES
        val wav = readWavHeader(wavFile)
        val codec = runCatching {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG)
        }.getOrElse {
            error("当前设备不支持 MP3 编码，请选择 FLAC 格式")
        }
        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_MPEG,
                wav.sampleRate,
                wav.channels,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, MP3_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MP3_INPUT_BUFFER_SIZE)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            BufferedOutputStream(FileOutputStream(output)).use { mp3Output ->
                writeId3v24(mp3Output, project)
                encodePcmWithCodec(
                    wavFile = wavFile,
                    dataOffset = dataOffset,
                    codec = codec,
                    output = mp3Output,
                    sampleRate = wav.sampleRate,
                    channels = wav.channels,
                )
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun encodePcmWithCodec(
        wavFile: File,
        dataOffset: Int,
        codec: MediaCodec,
        output: OutputStream,
        sampleRate: Int,
        channels: Int,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        FileInputStream(wavFile).use { fileInput ->
            val inputStream = BufferedInputStream(fileInput)
            var remainingSkip = dataOffset.toLong()
            while (remainingSkip > 0L) {
                val skipped = inputStream.skip(remainingSkip)
                if (skipped <= 0L) break
                remainingSkip -= skipped
            }
            val chunk = ByteArray(MP3_INPUT_BUFFER_SIZE)
            var inputEnded = false
            var outputEnded = false
            var totalFrames = 0L
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("MP3 编码输入缓冲区不可用")
                        inputBuffer.clear()
                        val read = inputStream.read(chunk, 0, chunk.size)
                        if (read < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                totalFrames * 1_000_000L / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            inputBuffer.put(chunk, 0, read)
                            val frames = read / (channels * 2)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                read,
                                totalFrames * 1_000_000L / sampleRate,
                                0,
                            )
                            totalFrames += frames
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    CODEC_TIMEOUT_US,
                )) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outputIndex)
                        if (
                            encoded != null &&
                            bufferInfo.size > 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            output.write(
                                ByteArray(bufferInfo.size).also(encoded::get),
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
    }

    private fun writeFlacMetadata(
        output: OutputStream,
        sampleRate: Int,
        channels: Int,
        totalSamples: Int,
        comments: List<String>,
        cover: CoverArt?,
    ) {
        output.write("fLaC".toByteArray(Charsets.US_ASCII))
        val hasCover = cover != null
        output.write(metadataHeader(type = 0, length = 34, last = false))
        output.write(
            flacStreamInfo(
                sampleRate = sampleRate,
                channels = channels,
                totalSamples = totalSamples,
            ),
        )
        val vorbis = vorbisComment(comments)
        output.write(metadataHeader(type = 4, length = vorbis.size, last = !hasCover))
        output.write(vorbis)
        if (cover != null) {
            val picture = flacPicture(cover)
            output.write(metadataHeader(type = 6, length = picture.size, last = true))
            output.write(picture)
        }
    }

    private fun flacStreamInfo(
        sampleRate: Int,
        channels: Int,
        totalSamples: Int,
    ): ByteArray {
        val data = ByteArrayOutputStream(34)
        data.writeShortBe(FLAC_BLOCK_SIZE)
        data.writeShortBe(FLAC_BLOCK_SIZE)
        data.write24Be(0)
        data.write24Be(0)
        val packed = (sampleRate.toLong() and 0xFFFFFL) shl 44 or
            ((channels - 1).toLong() and 0x7L) shl 41 or
            (15L shl 36) or
            (totalSamples.toLong() and 0xFFFFFFFFFL)
        repeat(8) { index ->
            data.write(((packed shr (56 - index * 8)) and 0xFF).toInt())
        }
        repeat(16) { data.write(0) }
        return data.toByteArray()
    }

    private fun vorbisComment(comments: List<String>): ByteArray {
        val vendor = "QYMusic".toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            writeIntLe(vendor.size)
            write(vendor)
            writeIntLe(comments.size)
            comments.forEach { comment ->
                val bytes = comment.toByteArray(Charsets.UTF_8)
                writeIntLe(bytes.size)
                write(bytes)
            }
        }.toByteArray()
    }

    private fun flacPicture(cover: CoverArt): ByteArray =
        ByteArrayOutputStream().apply {
            writeIntBe(3)
            val mime = cover.mimeType.toByteArray(Charsets.US_ASCII)
            writeIntBe(mime.size)
            write(mime)
            writeIntBe(0)
            writeIntBe(cover.width)
            writeIntBe(cover.height)
            writeIntBe(24)
            writeIntBe(0)
            writeIntBe(cover.bytes.size)
            write(cover.bytes)
        }.toByteArray()

    private fun writeFlacFrames(
        output: OutputStream,
        samples: ShortArray,
        channels: Int,
        blockSize: Int,
    ) {
        val totalFrames = samples.size / channels
        var frameNumber = 0
        var startFrame = 0
        while (startFrame < totalFrames) {
            val framesInBlock = minOf(blockSize, totalFrames - startFrame)
            val block = flacFrame(
                samples = samples,
                channels = channels,
                startFrame = startFrame,
                frameCount = framesInBlock,
                frameNumber = frameNumber,
            )
            output.write(block)
            startFrame += framesInBlock
            frameNumber += 1
        }
    }

    private fun flacFrame(
        samples: ShortArray,
        channels: Int,
        startFrame: Int,
        frameCount: Int,
        frameNumber: Int,
    ): ByteArray {
        val writer = FlacBitWriter()
        writer.writeBits(0x3FFE, 14)
        writer.writeBits(0, 1)
        writer.writeBits(0, 1)
        val blockSizeCode = if (frameCount == FLAC_BLOCK_SIZE) 12 else 7
        writer.writeBits(blockSizeCode, 4)
        writer.writeBits(9, 4)
        writer.writeBits(channels - 1, 4)
        writer.writeBits(4, 3)
        writer.writeBits(0, 1)
        writer.writeBytes(utf8Bytes(frameNumber))
        if (blockSizeCode == 7) {
            writer.writeBits(frameCount - 1, 16)
        }
        writer.align()
        writer.writeByte(crc8(writer.toByteArray()))
        repeat(channels) { channel ->
            writer.writeBits(0, 1)
            writer.writeBits(1, 6)
            writer.writeBits(0, 1)
            repeat(frameCount) { frame ->
                writer.writeBits(
                    samples[(startFrame + frame) * channels + channel].toInt() and 0xFFFF,
                    16,
                )
            }
        }
        writer.align()
        val crc = crc16(writer.toByteArray())
        writer.writeByte((crc shr 8) and 0xFF)
        writer.writeByte(crc and 0xFF)
        return writer.toByteArray()
    }

    private fun writeId3v24(
        output: OutputStream,
        project: KaraokeEditProject,
    ) {
        val frames = ByteArrayOutputStream().apply {
            write(textFrame("TIT2", project.exportTitle))
            write(textFrame("TPE1", project.exportArtist))
            write(textFrame("TOPE", project.exportOriginalSinger))
            if (project.exportLyrics.isNotBlank()) {
                write(lyricsFrame(project.exportLyrics))
            }
            readCover(project.coverUri)?.let { cover ->
                write(apicFrame(cover))
            }
        }.toByteArray()
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 4
        header[4] = 0
        header[5] = 0
        writeSynchsafe(header, 6, frames.size)
        output.write(header)
        output.write(frames)
    }

    private fun textFrame(id: String, value: String): ByteArray =
        id3Frame(id, byteArrayOf(3) + value.toByteArray(Charsets.UTF_8))

    private fun lyricsFrame(value: String): ByteArray =
        id3Frame(
            "USLT",
            byteArrayOf(3) + "xxx".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0) + value.toByteArray(Charsets.UTF_8),
        )

    private fun apicFrame(cover: CoverArt): ByteArray =
        id3Frame(
            "APIC",
            byteArrayOf(3) +
                cover.mimeType.toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0, 3, 0) +
                cover.bytes,
        )

    private fun id3Frame(id: String, data: ByteArray): ByteArray {
        val frame = ByteArray(10 + data.size)
        id.toByteArray(Charsets.US_ASCII).copyInto(frame, 0)
        writeSynchsafe(frame, 4, data.size)
        data.copyInto(frame, 10)
        return frame
    }

    private fun writeSynchsafe(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = ((value shr 21) and 0x7F).toByte()
        target[offset + 1] = ((value shr 14) and 0x7F).toByte()
        target[offset + 2] = ((value shr 7) and 0x7F).toByte()
        target[offset + 3] = (value and 0x7F).toByte()
    }

    private fun readWav(file: File): WavData {
        val bytes = file.readBytes()
        val dataOffset = findWavDataOffset(bytes)
        val header = readWavHeader(file)
        if (dataOffset < 0 || dataOffset >= bytes.size) {
            return WavData(
                sampleRate = header.sampleRate,
                channels = header.channels,
                samples = ShortArray(0),
            )
        }
        val data = ByteArray(bytes.size - dataOffset)
        bytes.copyInto(data, 0, dataOffset)
        return WavData(
            sampleRate = header.sampleRate,
            channels = header.channels,
            samples = ByteBufferCompat.shortArrayLe(data),
        )
    }

    private fun readWavHeader(file: File): WavHeader {
        val bytes = file.readBytes().copyOfRange(0, minOf(WAV_HEADER_BYTES, file.length().toInt()))
        return WavHeader(
            sampleRate = bytes.intLe(24).takeIf { it > 0 } ?: 44_100,
            channels = bytes.shortLe(22),
        )
    }

    private fun findWavDataOffset(bytes: ByteArray): Int {
        var index = 12
        while (index + 8 <= bytes.size) {
            val id = String(bytes, index, 4, Charsets.US_ASCII)
            val size = bytes.intLe(index + 4)
            if (id == "data") return index + 8
            index += 8 + size.coerceAtLeast(0)
        }
        return -1
    }

    private fun readCover(uriString: String?): CoverArt? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriString)
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            CoverArt(
                bytes = bytes,
                mimeType = appContext.contentResolver.getType(uri)
                    ?: if (bytes.size >= 8 && bytes[0] == 0x89.toByte()) {
                        "image/png"
                    } else {
                        "image/jpeg"
                    },
                width = options.outWidth.coerceAtLeast(1),
                height = options.outHeight.coerceAtLeast(1),
            )
        }.getOrNull()
    }

    private fun metadataHeader(
        type: Int,
        length: Int,
        last: Boolean,
    ): ByteArray = byteArrayOf(
        ((if (last) 0x80 else 0) or type).toByte(),
        ((length shr 16) and 0xFF).toByte(),
        ((length shr 8) and 0xFF).toByte(),
        (length and 0xFF).toByte(),
    )

    private fun utf8Bytes(value: Int): ByteArray {
        return when {
            value < 0x80 -> byteArrayOf(value.toByte())
            value < 0x800 -> byteArrayOf(
                (0xC0 or (value shr 6)).toByte(),
                (0x80 or (value and 0x3F)).toByte(),
            )

            value < 0x10000 -> byteArrayOf(
                (0xE0 or (value shr 12)).toByte(),
                (0x80 or ((value shr 6) and 0x3F)).toByte(),
                (0x80 or (value and 0x3F)).toByte(),
            )

            else -> byteArrayOf(
                (0xF0 or (value shr 18)).toByte(),
                (0x80 or ((value shr 12) and 0x3F)).toByte(),
                (0x80 or ((value shr 6) and 0x3F)).toByte(),
                (0x80 or (value and 0x3F)).toByte(),
            )
        }
    }

    private fun crc8(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc
    }

    private fun crc16(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x8005) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    private fun KaraokeEditProject.vorbisComments(): List<String> =
        buildList {
            if (exportTitle.isNotBlank()) add("TITLE=$exportTitle")
            if (exportArtist.isNotBlank()) add("ARTIST=$exportArtist")
            if (exportOriginalSinger.isNotBlank()) {
                add("ORIGINALARTIST=$exportOriginalSinger")
            }
            if (exportLyrics.isNotBlank()) add("LYRICS=$exportLyrics")
        }

    private class FlacBitWriter {
        private val output = ByteArrayOutputStream()
        private var current = 0
        private var bitCount = 0

        fun writeBits(value: Int, count: Int) {
            for (bitIndex in count - 1 downTo 0) {
                current = (current shl 1) or ((value shr bitIndex) and 1)
                bitCount += 1
                if (bitCount == 8) {
                    output.write(current)
                    current = 0
                    bitCount = 0
                }
            }
        }

        fun writeByte(value: Int) {
            align()
            output.write(value and 0xFF)
        }

        fun writeBytes(bytes: ByteArray) {
            align()
            output.write(bytes)
        }

        fun align() {
            if (bitCount == 0) return
            current = current shl (8 - bitCount)
            output.write(current)
            current = 0
            bitCount = 0
        }

        fun toByteArray(): ByteArray {
            align()
            return output.toByteArray()
        }
    }

    private object ByteBufferCompat {
        fun shortArrayLe(bytes: ByteArray): ShortArray {
            val count = bytes.size / 2
            val result = ShortArray(count)
            repeat(count) { index ->
                val offset = index * 2
                result[index] = (
                    (bytes[offset].toInt() and 0xFF) or
                        (bytes[offset + 1].toInt() shl 8)
                    ).toShort()
            }
            return result
        }
    }

    private data class WavHeader(
        val sampleRate: Int,
        val channels: Int,
    )

    private data class WavData(
        val sampleRate: Int,
        val channels: Int,
        val samples: ShortArray,
    )

    private data class CoverArt(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val FLAC_BLOCK_SIZE = 4_096
        const val WAV_HEADER_BYTES = 44
        const val CODEC_TIMEOUT_US = 10_000L
        const val MP3_BIT_RATE = 192_000
        const val MP3_INPUT_BUFFER_SIZE = 32 * 1_024
    }
}

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.shortLe(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArrayOutputStream.writeShortBe(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.write24Be(value: Int) {
    write((value shr 16) and 0xFF)
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeIntBe(value: Int) {
    write((value shr 24) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}
