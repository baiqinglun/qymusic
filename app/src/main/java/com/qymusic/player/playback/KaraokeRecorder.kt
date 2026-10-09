package com.qymusic.player.playback

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KaraokeRecordingPhase {
    IDLE,
    RECORDING,
    PROCESSING,
    READY,
    ERROR,
}

data class KaraokePitchPoint(
    val timeMs: Long,
    val musicMidi: Float?,
    val vocalMidi: Float?,
)

data class KaraokeRecordingState(
    val phase: KaraokeRecordingPhase = KaraokeRecordingPhase.IDLE,
    val elapsedMs: Long = 0L,
    val vocalMidi: Float? = null,
    val musicMidi: Float? = null,
    val points: List<KaraokePitchPoint> = emptyList(),
    val score: Int? = null,
    val pitchScore: Int? = null,
    val stabilityScore: Int? = null,
    val musicPitchAvailable: Boolean = false,
    val outputPath: String? = null,
    val errorMessage: String? = null,
) {
    val isRecording: Boolean get() = phase == KaraokeRecordingPhase.RECORDING
}

/**
 * Records microphone PCM, tracks microphone pitch and compares it with the
 * playback waveform exposed by the current audio session.
 */
@SuppressLint("MissingPermission")
class KaraokeRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(KaraokeRecordingState())
    val state: StateFlow<KaraokeRecordingState> = _state.asStateFlow()

    private val pointLock = Any()
    private val points = ArrayDeque<KaraokePitchPoint>()

    @Volatile
    private var recording = false

    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var visualizer: Visualizer? = null
    private var output: OutputStream? = null
    private var outputFile: File? = null
    private var sessionToken = 0L
    private var startedAtElapsedMs = 0L
    private var lastPointAtMs = -1L
    private var sampleRateHz = DEFAULT_SAMPLE_RATE_HZ
    private var playbackSessionId = 0
    private var latestVocalMidi: Float? = null
    private var latestMusicMidi: Float? = null

    @Synchronized
    fun start(audioSessionId: Int) {
        if (_state.value.isRecording) return
        stopInternal(discard = true)
        val token = ++sessionToken

        val file = File(
            appContext.cacheDir,
            "karaoke-${System.currentTimeMillis()}.wav",
        )
        var sessionOutput: OutputStream? = null
        try {
            sampleRateHz = chooseSampleRate()
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                error("无法创建麦克风缓冲区")
            }
            val bufferSize = max(minBuffer, sampleRateHz / 5 * PCM_BYTES_PER_SAMPLE)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            check(record.state == AudioRecord.STATE_INITIALIZED) {
                "麦克风初始化失败"
            }

            val activeOutput = file.outputStream().buffered()
            activeOutput.write(ByteArray(WAV_HEADER_BYTES))
            sessionOutput = activeOutput
            output = activeOutput
            outputFile = file
            startedAtElapsedMs = SystemClock.elapsedRealtime()
            lastPointAtMs = -1L
            playbackSessionId = audioSessionId
            latestVocalMidi = null
            latestMusicMidi = null
            synchronized(pointLock) { points.clear() }
            audioRecord = record
            recording = true
            record.startRecording()

            _state.value = KaraokeRecordingState(
                phase = KaraokeRecordingPhase.RECORDING,
                outputPath = file.absolutePath,
            )
            startPlaybackPitchProbe(audioSessionId)

            worker = Thread(
                {
                    runRecordingLoop(
                        record = record,
                        bufferSize = bufferSize,
                        sessionToken = token,
                        sessionFile = file,
                        sessionOutput = activeOutput,
                    )
                },
                "QYMusicKaraokeRecorder",
            ).apply {
                isDaemon = true
                start()
            }
        } catch (error: Exception) {
            runCatching { sessionOutput?.close() }
            releaseInput()
            outputFile?.delete()
            outputFile = null
            output = null
            recording = false
            _state.value = KaraokeRecordingState(
                phase = KaraokeRecordingPhase.ERROR,
                errorMessage = error.message ?: error.javaClass.simpleName,
            )
            Log.e(LOG_TAG, "K歌录制启动失败", error)
        }
    }

    @Synchronized
    fun stop() {
        if (!recording) {
            if (_state.value.phase == KaraokeRecordingPhase.RECORDING) {
                _state.value = _state.value.copy(phase = KaraokeRecordingPhase.PROCESSING)
            }
            return
        }
        recording = false
        _state.value = _state.value.copy(
            phase = KaraokeRecordingPhase.PROCESSING,
            elapsedMs = elapsedMs(),
        )
        runCatching { audioRecord?.stop() }
        releaseVisualizer()
    }

    @Synchronized
    fun discard() {
        stopInternal(discard = true)
        _state.value = KaraokeRecordingState()
    }

    @Synchronized
    fun attachPlaybackAudioSession(audioSessionId: Int) {
        if (!recording || audioSessionId <= 0 || audioSessionId == playbackSessionId) return
        releaseVisualizer()
        playbackSessionId = audioSessionId
        startPlaybackPitchProbe(audioSessionId)
    }

    @Synchronized
    fun release() {
        stopInternal(discard = true)
    }

    private fun runRecordingLoop(
        record: AudioRecord,
        bufferSize: Int,
        sessionToken: Long,
        sessionFile: File,
        sessionOutput: OutputStream,
    ) {
        val buffer = ByteArray(bufferSize)
        var capturedBytes = 0L
        try {
            while (recording) {
                val read = record.read(
                    buffer,
                    0,
                    buffer.size,
                    AudioRecord.READ_BLOCKING,
                )
                if (read <= 0) {
                    if (read < 0 && recording) {
                        error("读取麦克风失败：$read")
                    }
                    continue
                }
                sessionOutput.write(buffer, 0, read)
                capturedBytes += read

                val detected = KaraokePitchDetector.detectPcm16(
                    buffer = buffer,
                    length = read,
                    sampleRateHz = sampleRateHz,
                )
                latestVocalMidi = detected?.frequencyHz?.let(::frequencyToMidi)
                appendPointIfNeeded()
            }
            finishRecording(
                sessionToken = sessionToken,
                sessionFile = sessionFile,
                sessionOutput = sessionOutput,
                capturedBytes = capturedBytes,
            )
        } catch (error: Exception) {
            runCatching { sessionOutput.close() }
            if (sessionToken == this.sessionToken) {
                releaseInput()
                _state.value = KaraokeRecordingState(
                    phase = KaraokeRecordingPhase.ERROR,
                    elapsedMs = elapsedMs(),
                    errorMessage = error.message ?: error.javaClass.simpleName,
                )
            }
            Log.e(LOG_TAG, "K歌录制失败", error)
        }
    }

    private fun finishRecording(
        sessionToken: Long,
        sessionFile: File,
        sessionOutput: OutputStream,
        capturedBytes: Long,
    ) {
        runCatching { sessionOutput.flush() }
        runCatching { sessionOutput.close() }
        if (sessionToken != this.sessionToken) {
            sessionFile.delete()
            return
        }
        output = null
        releaseInput()
        releaseVisualizer()

        runCatching { writeWavHeader(sessionFile, capturedBytes, sampleRateHz) }
            .onFailure { error ->
                _state.value = KaraokeRecordingState(
                    phase = KaraokeRecordingPhase.ERROR,
                    elapsedMs = elapsedMs(),
                    outputPath = sessionFile.absolutePath,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                )
                return
            }

        val snapshot = synchronized(pointLock) { points.toList() }
        val score = KaraokeScoreCalculator.calculate(snapshot)
        _state.value = KaraokeRecordingState(
            phase = KaraokeRecordingPhase.READY,
            elapsedMs = elapsedMs(),
            points = snapshot,
            score = score.total,
            pitchScore = score.pitch,
            stabilityScore = score.stability,
            musicPitchAvailable = score.musicPitchAvailable,
            outputPath = sessionFile.absolutePath,
        )
        outputFile = null
    }

    private fun appendPointIfNeeded() {
        val nowMs = elapsedMs()
        if (nowMs - lastPointAtMs < PITCH_POINT_INTERVAL_MS) return
        appendPoint(force = false)
    }

    private fun appendPoint(force: Boolean) {
        val nowMs = elapsedMs()
        if (!force && nowMs - lastPointAtMs < PITCH_POINT_INTERVAL_MS) return
        lastPointAtMs = nowMs
        val point = KaraokePitchPoint(
            timeMs = nowMs,
            musicMidi = latestMusicMidi,
            vocalMidi = latestVocalMidi,
        )
        val snapshot = synchronized(pointLock) {
            points.addLast(point)
            while (points.size > MAX_PITCH_POINTS) {
                points.removeFirst()
            }
            points.toList()
        }
        _state.value = _state.value.copy(
            elapsedMs = nowMs,
            vocalMidi = latestVocalMidi,
            musicMidi = latestMusicMidi,
            points = snapshot,
        )
    }

    @SuppressLint("MissingPermission")
    private fun startPlaybackPitchProbe(audioSessionId: Int) {
        if (
            audioSessionId <= 0 ||
            audioSessionId != playbackSessionId ||
            visualizer != null
        ) {
            return
        }
        runCatching {
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureRange[1].coerceIn(
                captureRange[0],
                MAX_VISUALIZER_CAPTURE_SIZE,
            )
            val probe = Visualizer(audioSessionId)
            probe.captureSize = captureSize
            probe.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (!recording || waveform == null) return
                        val detected = KaraokePitchDetector.detectWaveform(
                            waveform = waveform,
                            sampleRateHz = samplingRate,
                        )
                        if (detected != null) {
                            latestMusicMidi = frequencyToMidi(detected.frequencyHz)
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 5,
                true,
                false,
            )
            probe.enabled = true
            visualizer = probe
        }.onFailure { error ->
            // Some devices reject a second out-of-process capture client. The
            // microphone score still works, and the UI explains the fallback.
            Log.w(LOG_TAG, "无法读取播放音高，将只按人声稳定度评分", error)
        }
    }

    private fun stopInternal(discard: Boolean) {
        sessionToken += 1
        recording = false
        playbackSessionId = 0
        runCatching { audioRecord?.stop() }
        releaseVisualizer()
        worker?.interrupt()
        worker = null
        releaseInput()
        runCatching { output?.close() }
        output = null
        if (discard) {
            outputFile?.delete()
            outputFile = null
        }
    }

    private fun releaseInput() {
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    private fun elapsedMs(): Long =
        if (startedAtElapsedMs <= 0L) {
            0L
        } else {
            (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
        }

    private fun chooseSampleRate(): Int {
        for (rate in PREFERRED_SAMPLE_RATES) {
            val result = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (result > 0) return rate
        }
        return DEFAULT_SAMPLE_RATE_HZ
    }

    private fun writeWavHeader(
        file: File,
        dataBytes: Long,
        sampleRateHz: Int,
    ) {
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((dataBytes + WAV_HEADER_BYTES - 8L).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRateHz)
                putInt(sampleRateHz * PCM_BYTES_PER_SAMPLE)
                putShort(PCM_BYTES_PER_SAMPLE.toShort())
                putShort(16.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }
            .array()
        RandomAccessFile(file, "rw").use { randomAccess ->
            randomAccess.seek(0L)
            randomAccess.write(header)
        }
    }

    private companion object {
        const val LOG_TAG = "QYMusicKaraoke"
        const val DEFAULT_SAMPLE_RATE_HZ = 44_100
        const val PCM_BYTES_PER_SAMPLE = 2
        const val WAV_HEADER_BYTES = 44
        const val PITCH_POINT_INTERVAL_MS = 80L
        const val MAX_PITCH_POINTS = 3_600
        const val MAX_VISUALIZER_CAPTURE_SIZE = 1_024
        val PREFERRED_SAMPLE_RATES = intArrayOf(44_100, 48_000)
    }
}

private data class KaraokeScoreValues(
    val total: Int,
    val pitch: Int,
    val stability: Int,
    val musicPitchAvailable: Boolean,
)

private object KaraokeScoreCalculator {
    fun calculate(points: List<KaraokePitchPoint>): KaraokeScoreValues {
        if (points.isEmpty()) {
            return KaraokeScoreValues(
                total = 0,
                pitch = 0,
                stability = 0,
                musicPitchAvailable = false,
            )
        }

        val vocalPoints = points.mapNotNull { it.vocalMidi }
        val matched = points.mapNotNull { point ->
            val vocal = point.vocalMidi ?: return@mapNotNull null
            val music = point.musicMidi ?: return@mapNotNull null
            vocal to music
        }
        val pitchAccuracy = if (matched.isEmpty()) {
            0f
        } else {
            val averageError = matched
                .map { (vocal, music) -> pitchClassDistance(vocal, music) }
                .average()
                .toFloat()
            (100f - averageError * PITCH_ERROR_PENALTY).coerceIn(0f, 100f)
        }
        val stability = vocalPitchStability(vocalPoints)
        val coverage = (vocalPoints.size.toFloat() / points.size.toFloat()).coerceIn(0f, 1f)
        val total = if (matched.isEmpty()) {
            (stability * 0.62f + coverage * 100f * 0.38f).coerceIn(0f, 100f)
        } else {
            (
                pitchAccuracy * 0.70f +
                    stability * 0.20f +
                    coverage * 100f * 0.10f
                ).coerceIn(0f, 100f)
        }

        return KaraokeScoreValues(
            total = total.toInt(),
            pitch = pitchAccuracy.toInt(),
            stability = stability.toInt(),
            musicPitchAvailable = matched.isNotEmpty(),
        )
    }

    private fun pitchClassDistance(firstMidi: Float, secondMidi: Float): Float {
        val difference = abs(firstMidi - secondMidi) % MIDI_OCTAVE
        return min(difference, MIDI_OCTAVE - difference)
    }

    private fun vocalPitchStability(values: List<Float>): Float {
        if (values.size < 2) return 55f
        val average = values.average().toFloat()
        val deviation = sqrt(
            values.map { value ->
                val delta = value - average
                delta * delta
            }.average().toFloat(),
        )
        return (100f - deviation * STABILITY_DEVIATION_PENALTY).coerceIn(0f, 100f)
    }

    private const val MIDI_OCTAVE = 12f
    private const val PITCH_ERROR_PENALTY = 15f
    private const val STABILITY_DEVIATION_PENALTY = 24f
}

private data class DetectedPitch(
    val frequencyHz: Float,
    val confidence: Float,
)

private object KaraokePitchDetector {
    fun detectPcm16(
        buffer: ByteArray,
        length: Int,
        sampleRateHz: Int,
    ): DetectedPitch? {
        val sampleCount = length / 2
        if (sampleCount < MIN_SAMPLE_COUNT) return null
        val start = max(0, sampleCount - ANALYSIS_SAMPLE_COUNT)
        val samples = FloatArray(sampleCount - start)
        var rmsSum = 0.0
        for (index in samples.indices) {
            val byteIndex = (start + index) * 2
            val lowByte = buffer[byteIndex].toInt() and 0xFF
            val highByte = buffer[byteIndex + 1].toInt() shl 8
            val sample = (lowByte or highByte).toShort().toFloat() / Short.MAX_VALUE
            samples[index] = sample
            rmsSum += sample * sample
        }
        return detect(samples, sampleRateHz, rmsSum)
    }

    fun detectWaveform(
        waveform: ByteArray,
        sampleRateHz: Int,
    ): DetectedPitch? {
        if (waveform.size < MIN_SAMPLE_COUNT) return null
        val start = max(0, waveform.size - ANALYSIS_SAMPLE_COUNT)
        val samples = FloatArray(waveform.size - start)
        var rmsSum = 0.0
        for (index in samples.indices) {
            val sample = (
                (waveform[start + index].toInt() and 0xFF) - 128
                ) / 128f
            samples[index] = sample
            rmsSum += sample * sample
        }
        return detect(samples, sampleRateHz, rmsSum)
    }

    private fun detect(
        samples: FloatArray,
        sampleRateHz: Int,
        rmsSum: Double,
    ): DetectedPitch? {
        if (samples.size < MIN_SAMPLE_COUNT || sampleRateHz <= 0) return null
        val rms = sqrt(rmsSum / samples.size.toDouble()).toFloat()
        if (rms < MIN_RMS) return null

        var mean = 0f
        samples.forEach { mean += it }
        mean /= samples.size
        val centered = FloatArray(samples.size) { samples[it] - mean }

        val minLag = (sampleRateHz / MAX_FREQUENCY_HZ).toInt().coerceAtLeast(2)
        val maxLag = (sampleRateHz / MIN_FREQUENCY_HZ)
            .toInt()
            .coerceAtMost(centered.size - 2)
        if (maxLag <= minLag) return null

        var bestLag = 0
        var bestCorrelation = 0f
        for (lag in minLag..maxLag) {
            var correlation = 0f
            var firstEnergy = 0f
            var secondEnergy = 0f
            val count = centered.size - lag
            for (index in 0 until count) {
                val first = centered[index]
                val second = centered[index + lag]
                correlation += first * second
                firstEnergy += first * first
                secondEnergy += second * second
            }
            val denominator = sqrt(firstEnergy * secondEnergy)
            if (denominator <= 0.000001f) continue
            val normalized = correlation / denominator
            if (normalized > bestCorrelation) {
                bestCorrelation = normalized
                bestLag = lag
            }
        }

        if (bestLag <= 0 || bestCorrelation < MIN_CORRELATION) return null
        return DetectedPitch(
            frequencyHz = sampleRateHz.toFloat() / bestLag.toFloat(),
            confidence = bestCorrelation,
        )
    }

    private const val ANALYSIS_SAMPLE_COUNT = 2_048
    private const val MIN_SAMPLE_COUNT = 512
    private const val MIN_RMS = 0.006f
    private const val MIN_FREQUENCY_HZ = 70f
    private const val MAX_FREQUENCY_HZ = 1_100f
    private const val MIN_CORRELATION = 0.45f
}

private fun frequencyToMidi(frequencyHz: Float): Float =
    69f + 12f * log2(frequencyHz.coerceAtLeast(1f) / 440f)
