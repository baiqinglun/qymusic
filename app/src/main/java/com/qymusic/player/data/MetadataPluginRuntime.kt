package com.qymusic.player.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
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

/**
 * 单个元数据插件的 JS 运行时。
 *
 * 与洛雪音源一样基于 WebView；插件以同步风格调用 `Platform.*` 宿主 API
 * （与 Lyrico 插件的写法一致），HTTP、加密、解压等调用通过
 * @JavascriptInterface 的同步返回实现，无需插件侧写 Promise。
 */
class MetadataPluginRuntime(
    context: Context,
    private val pluginId: String,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callSequence = AtomicLong()
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<Any?>>()
    private val bridge = NativeBridge(pluginId)

    private var webView: WebView? = null
    private var loadDeferred: CompletableDeferred<Unit>? = null
    private var pendingLoadScript: String? = null
    private var released = false

    suspend fun load(plugin: MetadataPlugin) = withContext(Dispatchers.Main.immediate) {
        check(!released) { "插件运行时已释放" }
        destroyWebView()
        pendingCalls.clear()
        val deferred = CompletableDeferred<Unit>()
        loadDeferred = deferred
        pendingLoadScript = plugin.script
        createWebView()
        webView?.loadDataWithBaseURL(
            BASE_URL,
            "<!doctype html><html><body></body></html>",
            "text/html",
            "utf-8",
            null,
        )
        withTimeout(LOAD_TIMEOUT_MS) { deferred.await() }
    }

    private val versionName: String by lazy {
        runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .orEmpty()
        }.getOrNull()?.takeIf(String::isNotBlank) ?: "0.0.0"
    }

    private val versionCode: Long by lazy {
        runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .longVersionCode
        }.getOrDefault(0L)
    }

    /** 调用插件的全局函数（searchSongs / getLyrics / searchCovers）。 */
    suspend fun invoke(functionName: String, request: JSONObject): Any? {
        val callId = "call_${callSequence.incrementAndGet()}"
        val deferred = CompletableDeferred<Any?>()
        pendingCalls[callId] = deferred
        val script = buildString {
            append("window.__qyInvoke(")
            append(JSONObject.quote(callId))
            append(",")
            append(JSONObject.quote(functionName))
            append(",")
            append(JSONObject.quote(request.toString()))
            append(");")
        }
        return try {
            withContext(Dispatchers.Main.immediate) {
                webView ?: throw IllegalStateException("插件尚未加载")
                webView?.evaluateJavascript(script, null)
            }
            withTimeout(INVOKE_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingCalls.remove(callId)
        }
    }

    fun release() {
        if (released) return
        released = true
        failPendingCalls("插件运行时已释放")
        scope.launch {
            destroyWebView()
            scope.cancel()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val view = WebView(appContext)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.addJavascriptInterface(bridge, BRIDGE_NAME)
        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    LOG_TAG,
                    "[$pluginId] ${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                )
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val script = pendingLoadScript ?: return
                if (view != webView) return
                pendingLoadScript = null
                view.evaluateJavascript(
                    "window.APP_VERSION_NAME=${JSONObject.quote(versionName)};" +
                        "window.APP_VERSION_CODE=$versionCode;",
                    null,
                )
                view.evaluateJavascript(BOOTSTRAP_SCRIPT) {
                    view.evaluateJavascript(script) { result ->
                        reportScriptError(result)
                    }
                    loadDeferred?.complete(Unit)
                    loadDeferred = null
                }
            }
        }
        webView = view
    }

    private fun reportScriptError(result: String?) {
        if (result.isNullOrBlank() || result == "null") return
        val message = runCatching {
            JSONObject(result).optString("message")
        }.getOrNull()?.takeIf(String::isNotBlank) ?: return
        Log.w(LOG_TAG, "[$pluginId] 脚本错误: $message")
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
            removeJavascriptInterface(BRIDGE_NAME)
            destroy()
        }
        webView = null
    }

    private fun resolveCallResult(callId: String, payload: String) {
        val deferred = pendingCalls[callId] ?: return
        scope.launch {
            runCatching {
                val json = JSONObject(payload)
                if (json.optBoolean("ok")) {
                    json.opt("data").takeUnless { it == JSONObject.NULL }
                } else {
                    throw IllegalStateException(
                        json.optString("error").ifBlank { "插件调用失败" },
                    )
                }
            }.onSuccess(deferred::complete)
                .onFailure(deferred::completeExceptionally)
        }
    }

    private inner class NativeBridge(private val id: String) {

        /** 插件函数调用结果（同步函数立即回，异步 Promise 稍后回）。 */
        @JavascriptInterface
        fun onCallResult(callId: String, payload: String) {
            resolveCallResult(callId, payload)
        }

        /** Platform.http：同步 HTTP 请求，返回完整响应 JSON。 */
        @JavascriptInterface
        fun httpExecute(requestJson: String): String {
            return runCatching {
                val request = JSONObject(requestJson)
                val url = request.optString("url").takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("url 不能为空")
                PluginHttpClient.execute(url, request)
            }.fold(
                onSuccess = { response ->
                    JSONObject()
                        .put("code", response.code)
                        .put("message", response.message)
                        .put("headers", response.headersToJson())
                        .put("body", response.bodyText)
                        .put("bodyBase64", response.bodyBase64)
                        .toString()
                },
                onFailure = { error ->
                    JSONObject()
                        .put("code", -1)
                        .put("message", error.message ?: error.javaClass.simpleName)
                        .put("headers", JSONObject())
                        .put("body", "")
                        .put("bodyBase64", "")
                        .toString()
                },
            )
        }

        /** Platform.crypto.md5 */
        @JavascriptInterface
        fun md5(text: String): String = runCatching {
            val digest = java.security.MessageDigest.getInstance("MD5")
                .digest(text.toByteArray(StandardCharsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

        /** Platform.crypto AES-ECB-PKCS5 */
        @JavascriptInterface
        fun aesEcbEncrypt(text: String, key: String, hexOutput: Boolean): String = runCatching {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES"))
            val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
            if (hexOutput) {
                encrypted.joinToString("") { "%02X".format(it) }
            } else {
                Base64.getEncoder().encodeToString(encrypted)
            }
        }.getOrDefault("")

        @JavascriptInterface
        fun aesEcbDecryptBase64ToText(base64: String, key: String): String = runCatching {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES"))
            val plain = cipher.doFinal(Base64.getDecoder().decode(base64))
            String(plain, StandardCharsets.UTF_8)
        }.getOrDefault("")

        /** Platform.compression：zlib inflate → UTF-8 文本。 */
        @JavascriptInterface
        fun inflateToText(base64: String): String = runCatching {
            val data = Base64.getDecoder().decode(base64)
            val inflater = Inflater()
            inflater.setInput(data)
            val output = ByteArray(INFLATE_CHUNK)
            val result = ByteArrayOutputStream()
            while (!inflater.finished()) {
                val count = inflater.inflate(output)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    continue
                }
                result.write(output, 0, count)
            }
            inflater.end()
            String(result.toByteArray(), StandardCharsets.UTF_8)
        }.getOrDefault("")

        /** Platform.cache：按插件隔离的字符串缓存。 */
        @JavascriptInterface
        fun cacheGet(key: String): String {
            val preferences = cachePreferences()
            val raw = preferences.getString(cacheKey(key), null) ?: return ""
            return runCatching {
                val json = JSONObject(raw)
                val expireAt = json.optLong("e", 0L)
                if (expireAt in 1..System.currentTimeMillis()) {
                    preferences.edit().remove(cacheKey(key)).apply()
                    ""
                } else {
                    json.optString("v")
                }
            }.getOrDefault("")
        }

        @JavascriptInterface
        fun cacheSet(key: String, value: String, ttlMs: Long) {
            val payload = JSONObject()
                .put("v", value)
                .put(
                    "e",
                    if (ttlMs > 0) System.currentTimeMillis() + ttlMs else 0L,
                )
            cachePreferences().edit().putString(cacheKey(key), payload.toString()).apply()
        }

        @JavascriptInterface
        fun cacheRemove(key: String) {
            cachePreferences().edit().remove(cacheKey(key)).apply()
        }

        @JavascriptInterface
        fun cacheClear() {
            val preferences = cachePreferences()
            val prefix = "$id|"
            preferences.edit().apply {
                preferences.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
            }.apply()
        }

        @JavascriptInterface
        fun log(level: String, tag: String, message: String) {
            val safeTag = "QYPlugin/${tag.take(48)}"
            when (level) {
                "warn" -> Log.w(safeTag, "[$id] $message")
                "error" -> Log.e(safeTag, "[$id] $message")
                else -> Log.d(safeTag, "[$id] $message")
            }
        }

        private fun cacheKey(key: String) = "$id|$key"

        private fun cachePreferences(): SharedPreferences =
            appContext.getSharedPreferences(CACHE_PREFERENCES_NAME, Context.MODE_PRIVATE)

        private fun PluginHttpResponse.headersToJson(): JSONObject {
            val headers = JSONObject()
            this@headersToJson.headers.forEach { (key, values) ->
                headers.put(key, JSONArray(values))
            }
            return headers
        }
    }

    companion object {
        private const val LOG_TAG = "QYMetadataRuntime"
        private const val BASE_URL = "https://qymusic.plugin/"
        private const val BRIDGE_NAME = "QyPluginNative"
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val INVOKE_TIMEOUT_MS = 45_000L
        private const val INFLATE_CHUNK = 64 * 1024
        private const val CACHE_PREFERENCES_NAME = "metadata_plugin_cache"

        /**
         * 宿主引导脚本：定义 globalThis.Platform（http/crypto/base64/bytes/
         * compression/xml/cache/log/app/runtime），并挂载 __qyInvoke 调用入口。
         * 全部 API 以同步风格提供，与 Lyrico 插件的写法保持一致。
         */
        internal val BOOTSTRAP_SCRIPT = """
            (function() {
              'use strict';
              var Native = window.QyPluginNative;

              function parseHeadersMap(raw) {
                var map = {};
                if (raw && typeof raw === 'object') {
                  for (var key in raw) {
                    if (Object.prototype.hasOwnProperty.call(raw, key)) {
                      var value = raw[key];
                      map[key] = Object.prototype.toString.call(value) === '[object Array]'
                        ? value : [String(value)];
                    }
                  }
                }
                return map;
              }

              function httpExecute(method, url, options, body) {
                options = options || {};
                var request = {
                  url: String(url),
                  method: method,
                  headers: options.headers || {},
                  contentType: options.contentType || '',
                  connectTimeoutMs: options.connectTimeoutMs || 10000,
                  readTimeoutMs: options.readTimeoutMs || 15000,
                  binaryResponse: options.__binary === true
                };
                if (options.bodyBase64) request.bodyBase64 = String(options.bodyBase64);
                else if (options.bodyBytes && options.bodyBytes.length)
                  request.bodyBase64 = btoa(Uint8ArrayToString(new Uint8Array(options.bodyBytes)));
                else if (body != null) request.body = String(body);
                var raw = Native.httpExecute(JSON.stringify(request));
                var response = JSON.parse(raw);
                if (response.code === -1) throw new Error('HTTP 请求失败: ' + response.message);
                return {
                  code: response.code,
                  message: response.message || '',
                  headers: parseHeadersMap(response.headers),
                  body: response.body || '',
                  bodyBase64: response.bodyBase64 || ''
                };
              }

              function ensureOk(response) {
                if (response.code < 200 || response.code >= 400) {
                  throw new Error('HTTP ' + response.code + ' ' + response.message);
                }
                return response;
              }

              function bytesToBase64(bytes) {
                return btoa(Uint8ArrayToString(bytes));
              }

              function Uint8ArrayToString(bytes) {
                var chunk = 0x8000;
                var parts = [];
                for (var i = 0; i < bytes.length; i += chunk) {
                  parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + chunk)));
                }
                return parts.join('');
              }

              function base64ToBytes(b64) {
                var binary = atob(String(b64).replace(/\\s+/g, ''));
                var bytes = new Uint8Array(binary.length);
                for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
                return bytes;
              }

              var http = {
                get: function(url, options) { return httpExecute('GET', url, options, null); },
                post: function(url, body, options) { return httpExecute('POST', url, options, body); },
                getText: function(url, options) { return ensureOk(httpExecute('GET', url, options, null)).body; },
                postText: function(url, body, options) { return ensureOk(httpExecute('POST', url, options, body)).body; },
                getBytes: function(url, options) {
                  var response = ensureOk(httpExecute('GET', url, mergeBinary(options), null));
                  return response.bodyBase64;
                },
                postBytes: function(url, body, options) {
                  return ensureOk(httpExecute('POST', url, mergeBinary(options), body)).bodyBase64;
                },
                postBytesResponse: function(url, body, options) {
                  return httpExecute('POST', url, mergeBinary(options), body);
                }
              };
              function mergeBinary(options) {
                var merged = {};
                for (var key in (options || {})) merged[key] = options[key];
                merged.__binary = true;
                return merged;
              }

              var base64 = {
                encodeText: function(text) {
                  return btoa(unescape(encodeURIComponent(String(text))));
                },
                decodeText: function(b64) {
                  return decodeURIComponent(escape(atob(String(b64))));
                },
                encodeBytes: function(bytes) { return bytesToBase64(new Uint8Array(bytes)); },
                decodeBytes: function(b64) { return Array.from(base64ToBytes(b64)); },
                dropBytes: function(b64, count) {
                  var bytes = base64ToBytes(b64);
                  return bytesToBase64(count >= bytes.length ? new Uint8Array(0) : bytes.subarray(count));
                },
                encodeUrlText: function(text) {
                  return bytesToBase64(new TextEncoder().encode(String(text)))
                    .replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+${'$'}/, '');
                },
                decodeUrlText: function(b64) {
                  return base64.decodeText(base64.fromUrl(b64));
                },
                encodeUrlBytes: function(bytes) {
                  return bytesToBase64(new Uint8Array(bytes))
                    .replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+${'$'}/, '');
                },
                decodeUrlBytes: function(b64) {
                  return Array.from(base64ToBytes(base64.fromUrl(b64)));
                },
                toUrl: function(b64) { return String(b64).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+${'$'}/, ''); },
                fromUrl: function(b64) {
                  var value = String(b64).replace(/-/g, '+').replace(/_/g, '/');
                  while (value.length % 4 !== 0) value += '=';
                  return value;
                }
              };

              var bytes = {
                xor: function(data, key) {
                  var input = data instanceof Uint8Array ? data : new Uint8Array(data || []);
                  var keyBytes = key instanceof Uint8Array ? key : new Uint8Array(key || []);
                  if (!keyBytes.length) return Array.from(input);
                  var output = new Uint8Array(input.length);
                  for (var i = 0; i < input.length; i++) output[i] = input[i] ^ keyBytes[i % keyBytes.length];
                  return Array.from(output);
                },
                xorBase64: function(b64, key) {
                  return bytesToBase64(new Uint8Array(bytes.xor(base64.decodeBytes(b64), key)));
                }
              };

              var compression = {
                inflateBytesToText: function(data) {
                  return compression.inflateBase64ToText(bytesToBase64(new Uint8Array(data || [])));
                },
                inflateBase64ToText: function(b64) { return Native.inflateToText(String(b64)); }
              };

              var crypto = {
                md5: function(text) { return Native.md5(String(text)); },
                aesEcbPkcs5EncryptBase64: function(text, key) { return Native.aesEcbEncrypt(String(text), String(key), false); },
                aesEcbPkcs5EncryptHex: function(text, key) { return Native.aesEcbEncrypt(String(text), String(key), true); },
                aesEcbPkcs5DecryptBase64ToText: function(b64, key) { return Native.aesEcbDecryptBase64ToText(String(b64), String(key)); }
              };

              var cache = {
                get: function(key) { return Native.cacheGet(String(key)); },
                set: function(key, value, ttlMs) { Native.cacheSet(String(key), value == null ? '' : String(value), ttlMs || 0); },
                remove: function(key) { Native.cacheRemove(String(key)); },
                clear: function() { Native.cacheClear(); }
              };

              var log = {
                debug: function(tagOrMessage, maybeMessage) {
                  emitLog('debug', tagOrMessage, maybeMessage);
                },
                warn: function(tagOrMessage, maybeMessage) { emitLog('warn', tagOrMessage, maybeMessage); },
                error: function(tagOrMessage, maybeMessage) { emitLog('error', tagOrMessage, maybeMessage); }
              };
              function emitLog(level, tagOrMessage, maybeMessage) {
                var tag = maybeMessage == null ? 'PlatformPlugin' : String(tagOrMessage);
                var message = maybeMessage == null ? String(tagOrMessage) : String(maybeMessage);
                try { Native.log(level, tag, message); } catch (ignored) {}
              }

              // ---- XML（基于 WebView 内置 DOMParser / XMLSerializer）----
              var xmlSerializer = new XMLSerializer();
              function parseXml(text) {
                var doc = new DOMParser().parseFromString(String(text), 'text/xml');
                if (doc.getElementsByTagName('parsererror').length) {
                  doc = new DOMParser().parseFromString(String(text), 'text/html');
                }
                return doc;
              }
              function attrsToMap(element) {
                var map = {};
                if (element && element.attributes) {
                  for (var i = 0; i < element.attributes.length; i++) {
                    var attr = element.attributes[i];
                    map[attr.name] = attr.value;
                  }
                }
                return map;
              }
              function matches(element, query) {
                if (query.tag && element.tagName !== query.tag) return false;
                var wanted = query.attrs || {};
                var attrs = attrsToMap(element);
                for (var key in wanted) {
                  if (Object.prototype.hasOwnProperty.call(wanted, key) && attrs[key] !== wanted[key]) {
                    return false;
                  }
                }
                return true;
              }
              function collect(doc, query) {
                var out = [];
                function walk(element) {
                  if (matches(element, query)) out.push(toXmlElement(element));
                  for (var i = 0; i < element.children.length; i++) walk(element.children[i]);
                }
                if (doc.documentElement) walk(doc.documentElement);
                return out;
              }
              function toXmlElement(element) {
                var children = [];
                for (var i = 0; i < element.children.length; i++) children.push(toXmlElement(element.children[i]));
                return {
                  tag: element.tagName,
                  attrs: attrsToMap(element),
                  text: element.textContent || '',
                  innerXml: Array.prototype.map.call(element.childNodes, function(node) {
                    return xmlSerializer.serializeToString(node);
                  }).join(''),
                  children: children
                };
              }
              var xml = {
                getRootAttributes: function(text) {
                  var doc = parseXml(text);
                  return attrsToMap(doc.documentElement);
                },
                findElements: function(text, query) { return collect(parseXml(text), query || {}); },
                removeElements: function(text, query) {
                  var doc = parseXml(text);
                  var doomed = [];
                  function walk(element) {
                    Array.prototype.slice.call(element.children).forEach(function(child) {
                      if (matches(child, query || {})) doomed.push(child); else walk(child);
                    });
                  }
                  if (doc.documentElement) walk(doc.documentElement);
                  doomed.forEach(function(element) {
                    if (element.parentNode) element.parentNode.removeChild(element);
                  });
                  return doc.documentElement ? xmlSerializer.serializeToString(doc) : String(text);
                },
                replaceChildrenByAttr: function(text, options) {
                  options = options || {};
                  if (!options.targetTag || !options.keyAttr) return String(text);
                  var doc = parseXml(text);
                  var replacements = options.replacements || {};
                  var elements = doc.getElementsByTagName(options.targetTag);
                  Array.prototype.slice.call(elements).forEach(function(element) {
                    var key = element.getAttribute(options.keyAttr);
                    var replacement = key == null ? null : replacements[key];
                    if (!replacement) return;
                    while (element.firstChild) element.removeChild(element.firstChild);
                    if (replacement.mode === 'xml') {
                      var fragment = new DOMParser().parseFromString(
                        '<root>' + String(replacement.value || '') + '</root>', 'text/xml');
                      if (fragment.getElementsByTagName('parsererror').length) {
                        element.textContent = String(replacement.value || '');
                      } else {
                        var root = fragment.documentElement;
                        for (var i = 0; i < root.childNodes.length; i++) {
                          element.appendChild(doc.importNode(root.childNodes[i], true));
                        }
                      }
                    } else {
                      element.textContent = String(replacement.value == null ? '' : replacement.value);
                    }
                  });
                  if (options.rootAttributes && doc.documentElement) {
                    for (var attrKey in options.rootAttributes) {
                      doc.documentElement.setAttribute(attrKey, String(options.rootAttributes[attrKey]));
                    }
                  }
                  return doc.documentElement ? xmlSerializer.serializeToString(doc) : String(text);
                }
              };

              var app = {
                getInfo: function() {
                  return {
                    name: 'QYMusic',
                    packageName: 'com.qymusic.player',
                    versionName: APP_VERSION_NAME,
                    versionCode: APP_VERSION_CODE,
                    buildType: 'release',
                    debug: false
                  };
                },
                getUserAgent: function() { return 'QYMusic/' + APP_VERSION_NAME; }
              };

              var runtime = {
                getInfo: function() {
                  return {
                    pluginApiVersion: 1,
                    hostApiVersion: ${MetadataPluginManifest.HOST_API_VERSION},
                    engine: 'webview',
                    engineVersion: null,
                    supportedHostApis: [
                      'http.getText', 'http.postText', 'http.postBytes', 'http.get',
                      'http.post', 'http.getBytes', 'http.postBytesResponse',
                      'crypto.md5', 'crypto.aesEcbPkcs5EncryptBase64',
                      'crypto.aesEcbPkcs5EncryptHex', 'crypto.aesEcbPkcs5DecryptBase64ToText',
                      'base64.encodeText', 'base64.decodeText', 'base64.encodeBytes',
                      'base64.decodeBytes', 'base64.dropBytes', 'base64.encodeUrlText',
                      'base64.decodeUrlText', 'base64.encodeUrlBytes', 'base64.decodeUrlBytes',
                      'base64.toUrl', 'base64.fromUrl', 'bytes.xor', 'bytes.xorBase64',
                      'compression.inflateBytesToText', 'compression.inflateBase64ToText',
                      'xml.getRootAttributes', 'xml.findElements', 'xml.removeElements',
                      'xml.replaceChildrenByAttr', 'cache.get', 'cache.set', 'cache.remove',
                      'cache.clear', 'log.debug', 'log.warn', 'log.error',
                      'app.info', 'app.userAgent', 'runtime.info'
                    ]
                  };
                }
              };

              globalThis.Platform = {
                app: app,
                runtime: runtime,
                http: http,
                crypto: crypto,
                base64: base64,
                bytes: bytes,
                compression: compression,
                xml: xml,
                cache: cache,
                log: log
              };
              globalThis.app = app;
              globalThis.runtime = runtime;

              window.__qyInvoke = function(callId, functionName, requestJson) {
                function respond(ok, payload) {
                  var envelope = ok
                    ? { ok: true, data: payload == null ? null : payload }
                    : { ok: false, error: String(payload == null ? '插件调用失败' : payload) };
                  try { Native.onCallResult(callId, JSON.stringify(envelope)); } catch (ignored) {}
                }
                try {
                  var fn = globalThis[functionName];
                  if (typeof fn !== 'function') throw new Error('插件未实现 ' + functionName);
                  var result = fn(JSON.parse(requestJson));
                  if (result && typeof result.then === 'function') {
                    result.then(
                      function(data) { respond(true, data); },
                      function(error) { respond(false, error && error.message ? error.message : error); }
                    );
                    return;
                  }
                  respond(true, result);
                } catch (error) {
                  respond(false, error && error.message ? error.message : error);
                }
              };
            })();
        """.trimIndent()
    }
}
