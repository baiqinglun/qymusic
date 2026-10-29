package com.qymusic.player.data

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 把 [PlatformPlaylistClient] 的私有加密逻辑桥接给单元测试。
 * 实现必须与客户端内的一致（拷贝自 PlatformPlaylistClient，改动需同步）。
 */
object PlatformPlaylistTestBridge {

    fun aesEncryptHex(data: String, tempKey: String): String {
        val keyString = md5Hex(tempKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyString.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(keyString.substring(16).toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun aesDecryptHex(hex: String, tempKey: String): String {
        val keyString = md5Hex(tempKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyString.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(keyString.substring(16).toByteArray(Charsets.UTF_8)),
        )
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return cipher.doFinal(bytes).toString(Charsets.UTF_8)
    }

    fun rawRsaUppercaseHex(data: String): String {
        val message = data.toByteArray(Charsets.UTF_8)
        val padded = ByteArray(128)
        message.copyInto(padded)
        val result = BigInteger(padded).modPow(KUGOU_RSA_E, KUGOU_RSA_N)
        return result.toString(16).padStart(256, '0').uppercase()
    }

    fun loginKeySign(dateTimeMs: Long): String =
        md5Hex("1005" + KUGOU_SALT_ANDROID + "20489" + dateTimeMs)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val KUGOU_SALT_ANDROID = "OIlwieks28dk2k092lksi2UIkp"
    private val KUGOU_RSA_E = BigInteger("010001", 16)
    private val KUGOU_RSA_N = BigInteger(
        "c8006ed03842d2628209bd314984ca5ed6cfe06e30c95f9d4704d9c49791d7a935" +
            "ba950ecb0bc8ebf5f5994f0bac927a7eb151b3c1de343303fa539c83136eccfd" +
            "7d7e511e2dbce18eaa9f784c9b50d443e75865979e0a5e216e46c684066a8d6b" +
            "998580bbaa22d73f5790286bb14742e83244e44db6d707ffe162c5c7002d45",
        16,
    )
}
