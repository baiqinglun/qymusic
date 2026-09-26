package com.qymusic.player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputProbeTest {
    @Test
    fun bitPerfectNeedsAndroid14UsbDacAndHighResDevice() {
        assertTrue(
            canUseBitPerfectOutput(sdkInt = 34, externalDacConnected = true, supportsHighRes = true),
        )
        assertFalse(
            canUseBitPerfectOutput(sdkInt = 33, externalDacConnected = true, supportsHighRes = true),
        )
        assertFalse(
            canUseBitPerfectOutput(sdkInt = 34, externalDacConnected = false, supportsHighRes = true),
        )
        assertFalse(
            canUseBitPerfectOutput(sdkInt = 34, externalDacConnected = true, supportsHighRes = false),
        )
    }

    @Test
    fun describesOutputCapability() {
        val bitPerfect = AudioOutputInfo(
            nativeSampleRateHz = 96_000,
            supports24Bit = true,
            supports96k = true,
            bitPerfectAvailable = true,
        )
        assertTrue(bitPerfect.highResDescription.contains("Hi-Res"))

        val plain = AudioOutputInfo(nativeSampleRateHz = 48_000)
        assertTrue(plain.nativeDescription.startsWith("48000Hz"))
        assertTrue(plain.highResDescription.contains("不支持"))
    }
}
