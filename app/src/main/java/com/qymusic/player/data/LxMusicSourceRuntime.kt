package com.qymusic.player.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class LxMusicSourceRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callSequence = AtomicLong()
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<Any?>>()
    private val javascriptBridge = JavascriptBridge()

    private var webView: WebView? = null
    private var loadRequest: LoadRequest? = null
    private var sourceId: String? = null
    private var capabilities: List<LxSourceCapability> = emptyList()
    private var released = false

    val loadedSourceId: String? get() = sourceId
    val loadedCapabilities: List<LxSourceCapability> get() = capabilities

    suspend fun load(source: LxMusicSource): List<LxSourceCapability> =
        withContext(Dispatchers.Main.immediate) {
            check(!released) { "音源运行时已释放" }
            failPendingCalls("音源已切换")
            destroyWebView()
            sourceId = null
            capabilities = emptyList()
            val deferred = CompletableDeferred<List<LxSourceCapability>>()
            loadRequest = LoadRequest(source = source, result = deferred)
            createWebView()
            webView?.loadDataWithBaseURL(
                BASE_URL,
                "<!doctype html><html><body></body></html>",
                "text/html",
                "utf-8",
                null,
            )
            withTimeout(SOURCE_INIT_TIMEOUT_MS) { deferred.await() }
        }

    suspend fun search(
        keyword: String,
        sourceKey: String? = null,
        page: Int = 1,
        pageSize: Int = SEARCH_PAGE_SIZE,
    ): LxSearchPage {
        val capability = sourceKey
            ?.let { requested -> capabilities.firstOrNull { it.key == requested } }
            ?: capabilities.firstOrNull {
                it.key == PREFERRED_SEARCH_SOURCE && it.supportsSearch
            }
            ?: capabilities.firstOrNull(LxSourceCapability::supportsSearch)
            ?: throw IllegalStateException("当前音源不支持搜索")
        val info = JSONObject()
            .put("keyword", keyword)
            .put("page", page)
            .put("pagesize", pageSize)
        val data = invoke(
            action = LxSourceCapability.ACTION_SEARCH,
            sourceKey = capability.key,
            info = info,
        )
        return parseSearchPage(
            data = data,
            capability = capability,
            requestedPageSize = pageSize,
        )
    }

    suspend fun resolveUrl(
        song: LxMusicSong,
        quality: String,
    ): String {
        val info = JSONObject()
            .put("musicInfo", JSONObject(song.rawJson))
            .put("type", quality)
        return when (
            val data = invoke(
                action = LxSourceCapability.ACTION_URL,
                sourceKey = song.sourceKey,
                info = info,
            )
        ) {
            is String -> data
            is JSONObject -> data.optString("url").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("音源没有返回可下载地址")

            else -> throw IllegalStateException("音源返回了无法识别的下载地址")
        }
    }

    fun release() {
        if (released) return
        released = true
        failPendingCalls("音源运行时已释放")
        scope.launch {
            destroyWebView()
            scope.cancel()
        }
    }

    private suspend fun invoke(
        action: String,
        sourceKey: String,
        info: JSONObject,
    ): Any? {
        val callId = "call_${callSequence.incrementAndGet()}"
        val result = CompletableDeferred<Any?>()
        pendingCalls[callId] = result
        val script = buildString {
            append("window.__lxInvoke(")
            append(JSONObject.quote(callId))
            append(",")
            append(JSONObject.quote(action))
            append(",")
            append(JSONObject.quote(sourceKey))
            append(",")
            append(JSONObject.quote(info.toString()))
            append(");")
        }
        return try {
            withContext(Dispatchers.Main.immediate) {
                val activeWebView = webView
                    ?: throw IllegalStateException("音源尚未加载")
                activeWebView.evaluateJavascript(script, null)
            }
            withTimeout(SOURCE_CALL_TIMEOUT_MS) { result.await() }
        } finally {
            pendingCalls.remove(callId)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val view = WebView(appContext)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.addJavascriptInterface(javascriptBridge, JAVASCRIPT_INTERFACE_NAME)
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    LOG_TAG,
                    "${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                )
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val request = loadRequest ?: return
                if (request.scriptStarted) return
                request.scriptStarted = true
                view.evaluateJavascript(BRIDGE_SCRIPT) {
                    view.evaluateJavascript(request.source.script) { result ->
                        reportScriptError(request, result)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    failLoad(error.description?.toString() ?: "音源页面加载失败")
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !request.url.toString().startsWith(BASE_URL)
        }
        webView = view
    }

    private fun reportScriptError(request: LoadRequest, result: String?) {
        if (loadRequest !== request || result.isNullOrBlank() || result == "null") return
        val message = runCatching {
            JSONObject(result).optString("message")
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return
        request.result.completeExceptionally(IllegalStateException(message))
        if (loadRequest === request) loadRequest = null
    }

    private fun handleSourceInit(
        source: LxMusicSource,
        credentials: String,
    ) {
        scope.launch {
            runCatching {
                parseCapabilities(JSONObject(credentials))
            }.onSuccess { parsed ->
                sourceId = source.id
                capabilities = parsed
                loadRequest?.result?.complete(parsed)
                loadRequest = null
            }.onFailure { error ->
                failLoad(error.message ?: "音源初始化失败")
            }
        }
    }

    private fun handleCallResult(
        callId: String,
        payload: String,
    ) {
        val result = pendingCalls[callId] ?: return
        scope.launch {
            runCatching {
                val json = JSONObject(payload)
                if (json.optBoolean("ok")) {
                    json.opt("data").takeUnless { it == JSONObject.NULL }
                } else {
                    throw IllegalStateException(
                        json.optString("error").ifBlank { "音源调用失败" },
                    )
                }
            }.onSuccess(result::complete)
                .onFailure(result::completeExceptionally)
        }
    }

    private fun handleNativeRequest(
        requestId: String,
        payload: String,
    ) {
        scope.launch(Dispatchers.IO) {
            val envelope = runCatching {
                val request = JSONObject(payload)
                val url = request.optString("url")
                require(url.isNotBlank()) { "请求地址为空" }
                val options = request.optJSONObject("options") ?: JSONObject()
                val response = LxHttpClient.execute(url, options)
                JSONObject()
                    .put("error", JSONObject.NULL)
                    .put(
                        "response",
                        JSONObject()
                            .put("statusCode", response.statusCode)
                            .put("headers", JSONObject(response.headers))
                            .put("body", response.body),
                    )
            }.getOrElse { error ->
                JSONObject()
                    .put("error", error.message ?: error.javaClass.simpleName)
                    .put("response", JSONObject.NULL)
            }
            withContext(Dispatchers.Main.immediate) {
                webView?.evaluateJavascript(
                    "window.__lxResolveRequest(" +
                        JSONObject.quote(requestId) +
                        "," +
                        JSONObject.quote(envelope.toString()) +
                        ");",
                    null,
                )
            }
        }
    }

    private fun parseCapabilities(root: JSONObject): List<LxSourceCapability> {
        val sources = root.optJSONObject("sources") ?: JSONObject()
        val keys = sources.keys()
        return buildList {
            while (keys.hasNext()) {
                val key = keys.next()
                val item = sources.optJSONObject(key) ?: continue
                val actions = item.optJSONArray("actions")
                    ?.toStringSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: setOf(LxSourceCapability.ACTION_URL)
                val qualities = item.optJSONArray("qualitys")
                    ?.toStringList()
                    ?.takeIf { it.isNotEmpty() }
                    ?: item.optJSONArray("qualities")
                        ?.toStringList()
                        ?.takeIf { it.isNotEmpty() }
                    ?: emptyList()
                add(
                    LxSourceCapability(
                        key = key,
                        name = item.optString("name").ifBlank { key },
                        type = item.optString("type").ifBlank { "music" },
                        actions = actions,
                        qualities = qualities,
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("音源没有声明可用能力")
    }

    private fun parseSearchPage(
        data: Any?,
        capability: LxSourceCapability,
        requestedPageSize: Int,
    ): LxSearchPage {
        val root = when (data) {
            is JSONObject -> data
            is JSONArray -> JSONObject().put("list", data)
            is String -> JSONObject(data)
            else -> throw IllegalStateException("音源返回了无法识别的搜索结果")
        }
        val array = extractSongArray(root)
        val songs = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(parseSong(item, capability, index))
            }
        }
        return LxSearchPage(
            songs = songs,
            isEnd = root.optBoolean("isEnd", songs.size < requestedPageSize),
            total = root.optInt("total").takeIf { root.has("total") },
        )
    }

    private fun extractSongArray(root: JSONObject): JSONArray =
        root.optJSONArray("list")
            ?: root.optJSONArray("songs")
            ?: root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()

    private fun parseSong(
        item: JSONObject,
        capability: LxSourceCapability,
        index: Int,
    ): LxMusicSong {
        val title = firstText(item, "name", "title", "songName", "songname")
            .ifBlank { "未知歌曲" }
        val artist = when (val artistValue = item.opt("singer") ?: item.opt("artist")) {
            is JSONArray -> buildList {
                for (artistIndex in 0 until artistValue.length()) {
                    val value = artistValue.opt(artistIndex)
                    add(
                        when (value) {
                            is JSONObject -> value.optString("name")
                            else -> value?.toString().orEmpty()
                        },
                    )
                }
            }.filter(String::isNotBlank).joinToString(" / ")

            is JSONObject -> artistValue.optString("name")
            else -> artistValue?.toString().orEmpty()
        }.ifBlank { "未知歌手" }
        val album = when (val albumValue = item.opt("albumName") ?: item.opt("album")) {
            is JSONObject -> albumValue.optString("name")
            else -> albumValue?.toString().orEmpty()
        }
        val durationValue = item.optDouble("duration", 0.0)
        val durationMs = when {
            durationValue <= 0.0 -> 0L
            durationValue > 10_000.0 -> durationValue.toLong()
            else -> (durationValue * 1000.0).toLong()
        }
        val id = firstText(item, "id", "songmid", "songId", "hash", "rid", "mid")
            .ifBlank { "${item.hashCode()}_$index" }
        return LxMusicSong(
            id = id,
            sourceKey = capability.key,
            sourceName = capability.name,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = firstText(item, "pic", "cover", "coverUrl")
                .takeIf(String::isNotBlank),
            rawJson = item.toString(),
        )
    }

    private fun firstText(item: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            item.optString(key).takeIf(String::isNotBlank)
        }.orEmpty()

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            opt(index)?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        }
    }.distinct()

    private fun JSONArray.toStringSet(): Set<String> = toStringList().toSet()

    private fun failLoad(message: String) {
        loadRequest?.result?.completeExceptionally(IllegalStateException(message))
        loadRequest = null
    }

    private fun failPendingCalls(message: String) {
        pendingCalls.values.forEach { deferred ->
            deferred.completeExceptionally(IllegalStateException(message))
        }
        pendingCalls.clear()
    }

    private fun destroyWebView() {
        webView?.apply {
            stopLoading()
            removeJavascriptInterface(JAVASCRIPT_INTERFACE_NAME)
            destroy()
        }
        webView = null
    }

    private inner class JavascriptBridge {
        @JavascriptInterface
        fun onSourceInit(payload: String) {
            val request = loadRequest ?: return
            handleSourceInit(request.source, payload)
        }

        @JavascriptInterface
        fun onRequest(requestId: String, payload: String) {
            handleNativeRequest(requestId, payload)
        }

        @JavascriptInterface
        fun onCallResult(callId: String, payload: String) {
            handleCallResult(callId, payload)
        }

        @JavascriptInterface
        fun onSourceEvent(event: String, payload: String) {
            Log.d(LOG_TAG, "source event $event: $payload")
        }
    }

    private data class LoadRequest(
        val source: LxMusicSource,
        val result: CompletableDeferred<List<LxSourceCapability>>,
        var scriptStarted: Boolean = false,
    )

    private companion object {
        const val BASE_URL = "https://qymusic.lx/"
        const val JAVASCRIPT_INTERFACE_NAME = "LxNative"
        const val LOG_TAG = "QYMusicLxSource"
        const val SEARCH_PAGE_SIZE = 30
        const val PREFERRED_SEARCH_SOURCE = "qsvip"
        const val SOURCE_INIT_TIMEOUT_MS = 30_000L
        const val SOURCE_CALL_TIMEOUT_MS = 60_000L

        val BRIDGE_SCRIPT = """
            (function() {
              var handlers = Object.create(null);
              var pending = new Map();
              var requestSeq = 0;
              var EVENT_NAMES = Object.freeze({
                request: 'request',
                inited: 'inited'
              });

              function emit(eventName, payload) {
                var list = handlers[eventName] || [];
                return Promise.all(list.map(function(handler) {
                  return Promise.resolve().then(function() {
                    return handler(payload);
                  });
                })).then(function(results) {
                  return results.length ? results[results.length - 1] : null;
                });
              }

              function resolveRequest(id, payload) {
                var pendingItem = pending.get(id);
                pending.delete(id);
                if (!pendingItem) return;
                var parsed;
                try {
                  parsed = JSON.parse(payload);
                } catch (error) {
                  pendingItem.reject(error);
                  return;
                }
                if (parsed.error) {
                  var requestError = new Error(String(parsed.error));
                  if (pendingItem.callback) pendingItem.callback(requestError);
                  pendingItem.reject(requestError);
                } else {
                  if (pendingItem.callback) pendingItem.callback(null, parsed.response);
                  pendingItem.resolve(parsed.response);
                }
              }

              window.lx = {
                EVENT_NAMES: EVENT_NAMES,
                env: 'mobile',
                version: '2.6.0',
                on: function(eventName, handler) {
                  if (!handlers[eventName]) handlers[eventName] = [];
                  handlers[eventName].push(handler);
                },
                send: function(eventName, data) {
                  if (eventName === EVENT_NAMES.inited) {
                    window.LxNative.onSourceInit(JSON.stringify(data));
                  } else {
                    window.LxNative.onSourceEvent(String(eventName), JSON.stringify(data));
                  }
                },
                request: function(url, options, callback) {
                  var requestId = 'req_' + (++requestSeq) + '_' + Date.now();
                  return new Promise(function(resolve, reject) {
                    pending.set(requestId, {
                      callback: callback,
                      resolve: resolve,
                      reject: reject
                    });
                    window.LxNative.onRequest(requestId, JSON.stringify({
                      url: String(url),
                      options: options || {}
                    }));
                  });
                },
                utils: {
                  randomBytes: function(length) {
                    var bytes = new Uint8Array(length);
                    crypto.getRandomValues(bytes);
                    return bytes;
                  },
                  str2b64: function(value) {
                    return btoa(unescape(encodeURIComponent(String(value))));
                  },
                  b642str: function(value) {
                    return decodeURIComponent(escape(atob(String(value))));
                  }
                }
              };

              window.__lxResolveRequest = resolveRequest;
              window.__lxInvoke = function(callId, action, source, infoJson) {
                var info;
                try {
                  info = JSON.parse(infoJson);
                } catch (error) {
                  window.LxNative.onCallResult(callId, JSON.stringify({
                    ok: false,
                    error: String(error)
                  }));
                  return;
                }
                emit(EVENT_NAMES.request, {
                  action: action,
                  source: source,
                  info: info
                }).then(function(data) {
                  window.LxNative.onCallResult(callId, JSON.stringify({
                    ok: true,
                    data: data == null ? null : data
                  }));
                }, function(error) {
                  window.LxNative.onCallResult(callId, JSON.stringify({
                    ok: false,
                    error: String(error && error.message ? error.message : error)
                  }));
                });
              };
            })();
        """.trimIndent()
    }
}
