package com.qymusic.player.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 字段级补全策略验证：
 * 按优先级单趟遍历、已获字段保留并跳过、缺失字段由后续源补全、
 * 全齐即停、同名字段先成功者优先。
 */
class MetadataFetchServiceTest {

    private class FakeInvoker(
        private val plugins: List<MetadataPlugin>,
        private val results: Map<String, Any?>,
    ) : MetadataPluginInvoker {
        val callCounts = mutableMapOf<String, Int>()

        override fun enabledPlugins(): List<MetadataPlugin> = plugins

        override suspend fun invoke(
            plugin: MetadataPlugin,
            functionName: String,
            request: JSONObject,
        ): Any? {
            val key = "${plugin.id}:$functionName"
            callCounts[key] = (callCounts[key] ?: 0) + 1
            return results[key]
        }
    }

    private fun plugin(
        id: String,
        lyrics: Boolean,
        covers: Boolean,
    ): MetadataPlugin = MetadataPlugin(
        manifest = MetadataPluginManifest(
            id = id,
            name = id,
            versionCode = 1,
            versionName = "1.0.0",
            apiVersion = 1,
            minHostApiVersion = 1,
            author = "",
            description = "",
            entry = "source.js",
            includeDirs = emptyList(),
            capabilities = buildSet {
                if (lyrics) add(MetadataPluginManifest.CAP_LYRICS)
                if (covers) add(MetadataPluginManifest.CAP_COVERS)
            },
        ),
        script = "",
        builtin = true,
        importedAtMs = 0L,
    )

    private fun lyricPayload(text: String = "[00:01.00]测试歌词", wordLevel: Boolean = true): JSONObject =
        if (wordLevel) {
            JSONObject()
                .put("type", "rawEnhancedLrc")
                .put("tags", JSONObject().put("ti", "测试"))
                .put("rawEnhancedLrc", text)
        } else {
            JSONObject()
                .put("type", "rawPlainLrc")
                .put("tags", JSONObject().put("ti", "测试"))
                .put("rawPlainLrc", text)
        }

    private fun coverPayload(url: String): JSONArray = JSONArray().put(
        JSONObject().put("url", url).put("width", 480),
    )

    private val fakeImageFetcher: suspend (String) -> ByteArray? = { url ->
        if (url.startsWith("img:")) ByteArray(256) else null
    }

    private val song = LxMusicSong(
        id = "1",
        sourceKey = "kg",
        sourceName = "酷狗",
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        durationMs = 269_000,
        coverUrl = null,
        rawJson = "{}",
    )

    private fun service(invoker: MetadataPluginInvoker) =
        MetadataFetchService(invoker, imageFetcher = fakeImageFetcher)

    @Test
    fun `歌词与封面可来自不同源，字段级回退补全`() = runBlocking {
        // A：只有歌词；B：只有封面
        val a = plugin("a", lyrics = true, covers = true)
        val b = plugin("b", lyrics = true, covers = true)
        val invoker = FakeInvoker(
            plugins = listOf(a, b),
            results = mapOf(
                "a:getLyrics" to lyricPayload(),
                "a:searchCovers" to JSONArray(), // A 有能力但没给封面
                "b:getLyrics" to lyricPayload("[00:02.00]不应被采用"),
                "b:searchCovers" to coverPayload("img:b-cover"),
            ),
        )
        val bundle = service(invoker).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = true),
        )
        assertEquals("a", bundle.lyrics?.sourceName)
        assertTrue(bundle.lyrics?.lrcText?.contains("[00:01.00]测试歌词") == true)
        assertEquals("b", bundle.cover?.sourceName)
    }

    @Test
    fun `同名字段先成功者优先，后续源不再被请求`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = true)
        val b = plugin("b", lyrics = true, covers = true)
        val invoker = FakeInvoker(
            plugins = listOf(a, b),
            results = mapOf(
                "a:getLyrics" to lyricPayload(),
                "a:searchCovers" to coverPayload("img:a-cover"),
                "b:getLyrics" to lyricPayload(),
                "b:searchCovers" to coverPayload("img:b-cover"),
            ),
        )
        val bundle = service(invoker).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = true),
        )
        assertEquals("a", bundle.lyrics?.sourceName)
        assertEquals("a", bundle.cover?.sourceName)
        // A 已补齐全部目标字段后，B 不应被调用（终止条件）
        assertEquals(0, invoker.callCounts["b:getLyrics"] ?: 0)
        assertEquals(0, invoker.callCounts["b:searchCovers"] ?: 0)
    }

    @Test
    fun `只请求单个字段时不调用无关能力`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = true)
        val invoker = FakeInvoker(
            plugins = listOf(a),
            results = mapOf(
                "a:getLyrics" to lyricPayload(),
                "a:searchCovers" to coverPayload("img:a-cover"),
            ),
        )
        val bundle = service(invoker).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = false),
        )
        assertEquals("a", bundle.lyrics?.sourceName)
        assertNull(bundle.cover)
        assertEquals(0, invoker.callCounts["a:searchCovers"] ?: 0)
    }

    @Test
    fun `歌词偏好逐字时跳过普通歌词源选用后续逐字源`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = false)
        val b = plugin("b", lyrics = true, covers = false)
        val invoker = FakeInvoker(
            plugins = listOf(a, b),
            results = mapOf(
                "a:getLyrics" to lyricPayload("[00:01.00]普通歌词", wordLevel = false),
                "b:getLyrics" to lyricPayload("[00:01.00]逐字歌词", wordLevel = true),
            ),
        )
        val service = service(invoker)
        service.preferWordLevel = true
        val bundle = service.fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = false),
        )
        assertEquals("b", bundle.lyrics?.sourceName)
        assertEquals(true, bundle.lyrics?.wordLevel)
    }

    @Test
    fun `歌词偏好逐字无逐字源时回退普通歌词`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = false)
        val invoker = FakeInvoker(
            plugins = listOf(a),
            results = mapOf(
                "a:getLyrics" to lyricPayload("[00:01.00]普通歌词", wordLevel = false),
            ),
        )
        val service = service(invoker)
        service.preferWordLevel = true
        val bundle = service.fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = false),
        )
        assertEquals("a", bundle.lyrics?.sourceName)
        assertEquals(false, bundle.lyrics?.wordLevel)
    }

    @Test
    fun `封面直链优先且成功后不再向插件请求封面`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = true)
        val invoker = FakeInvoker(
            plugins = listOf(a),
            results = mapOf(
                "a:getLyrics" to lyricPayload(),
                "a:searchCovers" to coverPayload("img:a-cover"),
            ),
        )
        val bundle = service(invoker).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(
                wantLyrics = true,
                wantCover = true,
                preferCoverUrl = "img:direct",
            ),
        )
        assertEquals("酷狗", bundle.cover?.sourceName) // 直链归属歌曲来源
        assertEquals("a", bundle.lyrics?.sourceName)
        assertEquals(0, invoker.callCounts["a:searchCovers"] ?: 0)
    }

    @Test
    fun `某源封面下载失败时由后续源补全`() = runBlocking {
        // B 给出的封面 URL 下载失败，回退到 C
        val b = plugin("b", lyrics = false, covers = true)
        val c = plugin("c", lyrics = false, covers = true)
        val failingFetcher: suspend (String) -> ByteArray? = { url ->
            if (url == "img:c-cover") ByteArray(256) else null
        }
        val invoker = FakeInvoker(
            plugins = listOf(b, c),
            results = mapOf(
                "b:searchCovers" to coverPayload("img:b-broken"),
                "c:searchCovers" to coverPayload("img:c-cover"),
            ),
        )
        val bundle = MetadataFetchService(invoker, imageFetcher = failingFetcher).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = false, wantCover = true),
        )
        assertEquals("c", bundle.cover?.sourceName)
    }

    @Test
    fun `所有源都无结果时字段为空`() = runBlocking {
        val a = plugin("a", lyrics = true, covers = true)
        val invoker = FakeInvoker(
            plugins = listOf(a),
            results = mapOf(
                "a:getLyrics" to JSONArray(),
                "a:searchCovers" to JSONArray(),
            ),
        )
        val bundle = service(invoker).fetchMetadata(
            song = song,
            request = MetadataFetchRequest(wantLyrics = true, wantCover = true),
        )
        assertNull(bundle.lyrics)
        assertNull(bundle.cover)
    }
}
