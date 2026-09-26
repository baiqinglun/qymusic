package com.qymusic.player.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build

/**
 * 当前输出链路的能力快照。
 *
 * Android 的声音一般要经过 AudioFlinger 混音，能否做 Hi-Res（24bit/96kHz）直通取决于
 * 输出设备、系统版本与 HAL，这里把关键信息读出来给用户一个明确交代。
 */
data class AudioOutputInfo(
    val nativeSampleRateHz: Int = 48_000,
    val nativeFramesPerBuffer: Int = 0,
    val maxChannelCount: Int = 2,
    val supportsFloat: Boolean = false,
    val supports24Bit: Boolean = false,
    val supports96k: Boolean = false,
    val externalDacConnected: Boolean = false,
    val bitPerfectAvailable: Boolean = false,
    val deviceNames: List<String> = emptyList(),
) {
    /** 例：48000Hz / 16bit / 2ch。 */
    val nativeDescription: String
        get() = buildString {
            append(nativeSampleRateHz)
            append("Hz")
            append(" / ")
            append(if (supports24Bit) "24bit" else "16bit")
            append(" / ")
            append(maxChannelCount)
            append("ch")
        }

    val highResDescription: String
        get() = when {
            bitPerfectAvailable -> "支持 Hi-Res 直通（可绕过系统重采样）"
            supports24Bit && supports96k -> "设备支持 24bit/96kHz，但系统会经过 AudioFlinger 混音"
            supports96k -> "设备支持 96kHz 采样率，位深受限"
            else -> "当前路由不支持 Hi-Res 输出（最高 ${nativeSampleRateHz}Hz）"
        }
}

/**
 * bit-perfect 直通的前提：Android 14 起才开放 `AudioMixerAttributes`，
 * 且只对外接 USB/专业音频设备有意义，同时设备本身要支持高采样率。
 */
fun canUseBitPerfectOutput(
    sdkInt: Int,
    externalDacConnected: Boolean,
    supportsHighRes: Boolean,
): Boolean = sdkInt >= 34 && externalDacConnected && supportsHighRes

fun probeAudioOutput(context: Context): AudioOutputInfo {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return AudioOutputInfo()
    val nativeRate = audioManager
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        ?.toIntOrNull()
        ?: 48_000
    val framesPerBuffer = audioManager
        .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        ?.toIntOrNull()
        ?: 0

    val outputs = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }.getOrDefault(emptyList())

    var supportsFloat = false
    var supports24Bit = false
    var supports96k = false
    var maxChannels = 2
    var externalDac = false
    val names = mutableListOf<String>()

    outputs.forEach { device ->
        if (!device.isSink) return@forEach
        val encodings = runCatching { device.encodings?.toList().orEmpty() }
            .getOrDefault(emptyList())
        val rates = runCatching { device.sampleRates?.toList().orEmpty() }
            .getOrDefault(emptyList())

        if (encodings.contains(AudioFormat.ENCODING_PCM_FLOAT)) supportsFloat = true
        if (encodings.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED)) supports24Bit = true
        if (rates.any { it >= 96_000 }) supports96k = true
        val channelCount = device.channelCounts?.maxOrNull()
        if (channelCount != null) {
            maxChannels = maxOf(maxChannels, channelCount)
        }
        if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        ) {
            externalDac = true
        }
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "输出设备"
        names += "$name(${rates.maxOrNull() ?: nativeRate}Hz)"
    }

    return AudioOutputInfo(
        nativeSampleRateHz = nativeRate,
        nativeFramesPerBuffer = framesPerBuffer,
        maxChannelCount = maxChannels,
        supportsFloat = supportsFloat,
        supports24Bit = supports24Bit,
        supports96k = supports96k,
        externalDacConnected = externalDac,
        bitPerfectAvailable = canUseBitPerfectOutput(
            sdkInt = Build.VERSION.SDK_INT,
            externalDacConnected = externalDac,
            supportsHighRes = supports24Bit && supports96k,
        ),
        deviceNames = names,
    )
}
