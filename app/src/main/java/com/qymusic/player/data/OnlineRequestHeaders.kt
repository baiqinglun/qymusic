package com.qymusic.player.data

/**
 * 在线播放与下载共用的请求头。
 *
 * 之前播放链路用写死的 "QYMusic" UA、且完全不带 Referer，部分平台直链会返回 403
 * 或直接回一个网页；下载链路却是浏览器 UA + 按平台带 Referer。两套口径不一致时，
 * 同一首歌可能“能下载不能播”。这里收敛成一份，避免后续再漂移。
 */
object OnlineRequestHeaders {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    const val ACCEPT = "*/*"

    /** 各平台直链要求的 Referer；没有要求的返回 null。 */
    fun refererFor(sourceKey: String): String? = when (sourceKey) {
        "wy" -> "https://music.163.com/"
        "tx" -> "https://y.qq.com/"
        "kw" -> "https://www.kuwo.cn/"
        "kg" -> "https://www.kugou.com/"
        "mg" -> "https://music.migu.cn/"
        else -> null
    }

    /** 播放请求按曲目来源注入的额外头（目前只有 Referer）。 */
    fun forSource(sourceKey: String): Map<String, String> =
        refererFor(sourceKey)?.let { mapOf("Referer" to it) }.orEmpty()

    /** HTTP 数据源的默认头。 */
    fun defaultHeaders(): Map<String, String> = mapOf("Accept" to ACCEPT)
}
