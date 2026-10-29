package com.qymusic.player.data

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 把网易云 weapi 加密桥接给单元测试。
 * 实现必须与 PlatformPlaylistClient 内一致（改动需同步）。
 */
object PlatformPlaylistWeapiBridge {

    fun weapiEncrypt(text: String, secret: String): Pair<String, String> {
        val step1 = aesBase64(text.toByteArray(Charsets.UTF_8), "0CoJUm6Qyw8W8jud")
        val params = aesBase64(step1.toByteArray(Charsets.UTF_8), secret)
        val encSecKey = BigInteger(1, secret.reversed().toByteArray(Charsets.UTF_8))
            .modPow(NETEASE_RSA_E, NETEASE_RSA_N)
            .toString(16)
            .padStart(256, '0')
        return params to encSecKey
    }

    private fun aesBase64(data: ByteArray, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec("0102030405060708".toByteArray(Charsets.UTF_8)),
        )
        return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(data))
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private val NETEASE_RSA_E = BigInteger("010001", 16)
    private val NETEASE_RSA_N = BigInteger(
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
            "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e03" +
            "12ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10" +
            "b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db" +
            "0a22b8e7",
        16,
    )
}
