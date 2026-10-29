package com.qymusic.player.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject

data class PluginHttpResponse(
    val code: Int,
    val message: String,
    val headers: Map<String, List<String>>,
    val bodyText: String,
    val bodyBase64: String,
    val bodyWasBinary: Boolean,
)

/** 插件 Platform.http 的原生实现：同步执行，返回完整响应（文本或 Base64 二进制）。 */
object PluginHttpClient {

    fun execute(url: String, request: JSONObject): PluginHttpResponse {
        val method = request.optString("method", "GET").uppercase().ifBlank { "GET" }
        val headers = request.optJSONObject("headers") ?: JSONObject()
        val contentType = request.optString("contentType").takeIf(String::isNotBlank)
        val bodyBase64 = request.optString("bodyBase64").takeIf(String::isNotBlank)
        val bodyText = request.optString("body").takeIf(String::isNotBlank)
        val binaryResponse = request.optBoolean("binaryResponse", false)
        val connectTimeoutMs = request.optInt("connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS)
            .coerceIn(2_000, 60_000)
        val readTimeoutMs = request.optInt("readTimeoutMs", DEFAULT_READ_TIMEOUT_MS)
            .coerceIn(2_000, 120_000)

        var connection: HttpURLConnection? = null
        try {
            val current = URL(url).openConnection() as HttpURLConnection
            connection = current
            current.connectTimeout = connectTimeoutMs
            current.readTimeout = readTimeoutMs
            current.requestMethod = method
            current.instanceFollowRedirects = true
            for (key in headers.keys()) {
                val value = headers.optString(key)
                if (value.isNotBlank()) current.setRequestProperty(key, value)
            }
            if (current.getRequestProperty("User-Agent").isNullOrBlank()) {
                current.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            }
            if (current.getRequestProperty("Accept-Encoding").isNullOrBlank()) {
                current.setRequestProperty("Accept-Encoding", "gzip")
            }
            val hasBody = method in BODY_METHODS && (bodyBase64 != null || bodyText != null)
            if (hasBody) {
                current.doOutput = true
                val payload: ByteArray = if (bodyBase64 != null) {
                    Base64.getDecoder().decode(bodyBase64)
                } else {
                    bodyText!!.toByteArray(StandardCharsets.UTF_8)
                }
                if (!contentType.isNullOrBlank() &&
                    current.getRequestProperty("Content-Type").isNullOrBlank()
                ) {
                    current.setRequestProperty("Content-Type", contentType)
                }
                current.outputStream.use { it.write(payload) }
            }

            val code = current.responseCode
            val message = current.responseMessage.orEmpty()
            val responseHeaders = current.headerFields
                .orEmpty()
                .mapNotNull { (key, values) ->
                    key?.takeIf(String::isNotBlank)?.let { it to values.toList() }
                }
                .toMap()
            val stream: InputStream? = if (code in 200..399) {
                current.inputStream
            } else {
                current.errorStream
            }
            val bytes = stream?.use(::readAll) ?: ByteArray(0)
            val text = decodeText(bytes, responseHeaders)
            return PluginHttpResponse(
                code = code,
                message = message,
                headers = responseHeaders,
                bodyText = if (binaryResponse) "" else text,
                bodyBase64 = if (binaryResponse) {
                    Base64.getEncoder().encodeToString(bytes)
                } else {
                    ""
                },
                bodyWasBinary = binaryResponse,
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val raw = input.buffered().use { stream -> stream.readBytes() }
        // HttpURLConnection 通常已自动解压，这里兜底处理未解压的 gzip/deflate 内容。
        val magic = if (raw.size >= 2) {
            ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
        } else {
            -1
        }
        return when {
            magic == 0x1F8B -> runCatching { GZIPInputStream(raw.inputStream()).readBytes() }
                .getOrDefault(raw)
            magic == 0x789C || magic == 0x7801 -> runCatching {
                InflaterInputStream(raw.inputStream()).readBytes()
            }.getOrDefault(raw)
            else -> raw
        }
    }

    private fun decodeText(bytes: ByteArray, headers: Map<String, List<String>>): String {
        val charset = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
        return runCatching {
            charset?.let { name -> String(bytes, charset(name)) }
        }.getOrNull() ?: String(bytes, StandardCharsets.UTF_8)
    }

    /** 字节数组 → JSON 数组（供 Platform.base64.decodeBytes 等 JS 侧使用）。 */
    fun toJsonBytes(bytes: ByteArray): JSONArray {
        val array = JSONArray()
        for (byte in bytes) array.put(byte.toInt() and 0xFF)
        return array
    }

    private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
    private const val DEFAULT_READ_TIMEOUT_MS = 15_000
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
}
