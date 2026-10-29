package com.qymusic.player.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class LxHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
)

object LxHttpClient {
    fun execute(url: String, options: JSONObject = JSONObject()): LxHttpResponse {
        val method = options.optString("method", "GET")
            .uppercase()
            .takeIf { it in SUPPORTED_METHODS }
            ?: "GET"
        val timeoutMs = options.optInt("timeout", DEFAULT_TIMEOUT_MS)
            .coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        val headers = options.optJSONObject("headers") ?: JSONObject()
        val requestBody = options.opt("body")
            ?.takeUnless { it == JSONObject.NULL }
            ?.let { value ->
                when (value) {
                    is String -> value
                    is JSONObject -> value.toString()
                    is JSONArray -> value.toString()
                    else -> value.toString()
                }
            }

        val connection = openWithRedirects(url, method, headers, requestBody, timeoutMs)
        return try {
            val statusCode = connection.responseCode
            val responseHeaders = connection.headerFields
                .orEmpty()
                .mapNotNull { (key, values) ->
                    key?.takeIf(String::isNotBlank)?.let {
                        it to values.joinToString(", ")
                    }
                }
                .toMap()
            val input = if (statusCode >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val body = input?.use { stream ->
                BufferedReader(
                    InputStreamReader(stream, StandardCharsets.UTF_8),
                ).readText()
            }.orEmpty()
            LxHttpResponse(
                statusCode = statusCode,
                headers = responseHeaders,
                body = body,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openWithRedirects(
        url: String,
        method: String,
        headers: JSONObject,
        requestBody: String?,
        timeoutMs: Int,
    ): HttpURLConnection {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = method
            headers.keys().forEach { key ->
                headers.optString(key).takeIf(String::isNotBlank)?.let { value ->
                    connection.setRequestProperty(key, value)
                }
            }
            if (connection.getRequestProperty("User-Agent").isNullOrBlank()) {
                connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            }
            if (requestBody != null && method in METHODS_WITH_BODY) {
                connection.doOutput = true
                if (connection.getRequestProperty("Content-Type").isNullOrBlank()) {
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8",
                    )
                }
                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val statusCode = connection.responseCode
            if (statusCode in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("HTTP 重定向缺少 Location")
                }
                redirects += 1
                if (redirects > MAX_REDIRECTS) {
                    throw IllegalStateException("HTTP 重定向次数过多")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            return connection
        }
    }

    private const val DEFAULT_TIMEOUT_MS = 15_000
    private const val MIN_TIMEOUT_MS = 5_000
    private const val MAX_TIMEOUT_MS = 60_000
    private const val MAX_REDIRECTS = 5
    private const val DEFAULT_USER_AGENT = "LX-Music-Mobile"
    private val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "DELETE", "HEAD")
    private val METHODS_WITH_BODY = setOf("POST", "PUT", "DELETE")
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
