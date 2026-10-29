package com.qymusic.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 酷狗网关签名 / mid 算法对照测试。
 * 参考值由同一算法的 Python 实现算出（该实现已在 2026-10 用真实网关请求验证过：
 * 未登录请求返回 error_code=20010，证明签名被服务端接受）。
 */
class PlatformPlaylistKugouSignTest {

    @Test
    fun `android signature matches reference`() {
        val params = mapOf(
            "appid" to "1005",
            "clientver" to "20489",
            "clienttime" to "1700000000",
            "dfid" to "-",
            "mid" to "123",
            "uuid" to "-",
            "userid" to "42",
            "token" to "tok",
            "plat" to "1",
        )
        val body = """{"userid":42,"token":"tok","total_ver":979,"type":2,"page":1,"pagesize":100}"""
        val signature = kugouSign(KUGOU_SALT_ANDROID_TEST, params, body)
        assertEquals("d16a943cc07dd2c344dd752f4572fe86", signature)
    }

    @Test
    fun `web signature matches reference`() {
        val params = mapOf(
            "appid" to "1001",
            "type" to "1",
            "plat" to "4",
            "qrcode_txt" to "x",
            "srcappid" to "2919",
            "dfid" to "-",
            "mid" to "123",
            "uuid" to "-",
            "clientver" to "20489",
            "clienttime" to "1700000000",
        )
        val signature = kugouSign(KUGOU_SALT_WEB_TEST, params, "")
        assertEquals("efce1473ada66c139049ddb8a3907cb6", signature)
    }

    @Test
    fun `mid converts md5 hex to decimal`() {
        assertEquals(
            "308741372901437977228425563242293240765",
            kugouMidOf("550e8400-e29b-41d4-a716-446655440000"),
        )
    }

    private companion object {
        const val KUGOU_SALT_ANDROID_TEST = "OIlwieks28dk2k092lksi2UIkp"
        const val KUGOU_SALT_WEB_TEST = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    }
    @Test
    fun `kugou sms login aes and rsa match reference`() {
        // 参考值由 KuGouMusicApi 同款 node 实现（crypto-js + node-forge）计算，
        // 且整条加密链路已通过酷狗服务端实测（login_by_verifycode 返回 20021 验证码错误，
        // 说明服务端成功解开了 pk/params）。
        val tempKey = "abcdefghijklmnop1234"
        val plain = """{"mobile":"13800001111","code":"123456"}"""
        val paramsHex = PlatformPlaylistTestBridge.aesEncryptHex(plain, tempKey)
        assertEquals(
            "7cfa201809ffabe66895f42734e3f52f52dbfe5f3bb989ac48ab6a1ea0a60f271ae0bc634677f4cc669571c3407da21b",
            paramsHex,
        )

        val dateTime = 1739999999000L
        val pkPlain = """{"clienttime_ms":1739999999000,"key":"abcdefghijklmnop1234"}"""
        assertEquals(
            "1961A4E478125B816D221B8666E4A318CEC2F91AC6900395CB2899376DD1BD828621BE9CB34E0FE995BFE15D4C7BA35D0DA83219D162596E3086D64AADD55E98F64CFB5A5CF8B1C2A0471AFEE743BE45826E7D8D192552334A3DCF379E4232FF4F3AD6580A6B026C760A5477371BAF76BB2AB75EBA58C5E973DAF65D9CAAAB22",
            PlatformPlaylistTestBridge.rawRsaUppercaseHex(pkPlain),
        )
        assertEquals(
            "20c888b5d73cf61103e5997decce27d4",
            PlatformPlaylistTestBridge.loginKeySign(dateTime),
        )

        // AES 解密回环：能解回原文
        assertEquals(
            plain,
            PlatformPlaylistTestBridge.aesDecryptHex(paramsHex, tempKey),
        )
    }
    @Test
    fun `netease weapi encryption matches reference`() {
        // 参考值由同款 python 实现（pycryptodome）计算；
        // 整条 weapi 链路已通过网易云服务端实测：
        // captcha/sent 返回"手机号码不符合规范"、login/cellphone 返回"验证码错误"，
        // 说明服务端成功解开了 params/encSecKey。
        val secret = "0123456789abcdef"
        val text = """{"cellphone":"13800001111","ctcode":86,"csrf_token":""}"""
        val (params, encSecKey) = PlatformPlaylistWeapiBridge.weapiEncrypt(text, secret)
        assertEquals(
            "Ah9Uk4XN1mhzJPM/45ZB2e9mEY2ObsE7h3/7CQMH5/rhZVJkW8UdflVahtVh+u0NnObG1qgIGMsyvNg359pATZtX0tVYOy6K/iOGJ/Hqu3+4PPwNMRDwgO3f8UZvNgeN",
            params,
        )
        assertEquals(
            "35701388baf89fed412e11269b9c76625d095ecaf17f03fa018abe19ea2d38b949debf242ee39a71ca1f6cda71b1b86a45aa909ee27f7e78e267d34e732f0de948206c3340a788d0003372183e2f753c1f78b66ac23d134ac1fc9b993156520ea826b8aa89a962d4491b4b8d7e08738e1da9b07aa39bf4a7ef0b1c210728cd52",
            encSecKey,
        )
    }

    @Test
    fun `netease qr status maps server codes`() {
        // 参考值来自 2026-10 本机实测（unikey 接口返回 200、轮询接口返回 801 等待扫码）
        assertEquals(0, neteaseQrStatusOf(800))
        assertEquals(1, neteaseQrStatusOf(801))
        assertEquals(2, neteaseQrStatusOf(802))
        assertEquals(3, neteaseQrStatusOf(803))
        assertNull(neteaseQrStatusOf(200))
        assertNull(neteaseQrStatusOf(703))
    }

    @Test
    fun `netease qr content is codekey login url`() {
        assertEquals(
            "https://music.163.com/login?codekey=6220b56c-d5a5-4ee0",
            neteaseQrContentOf("6220b56c-d5a5-4ee0"),
        )
    }
}
