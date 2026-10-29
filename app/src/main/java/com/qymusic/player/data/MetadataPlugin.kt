package com.qymusic.player.data

import org.json.JSONObject

/**
 * 元数据插件清单，格式与 Lyrico 插件包的 manifest.json 一致：
 * 插件以 JS 脚本形式提供 searchSongs / getLyrics / searchCovers 三个可选能力。
 */
data class MetadataPluginManifest(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val apiVersion: Int,
    val minHostApiVersion: Int,
    val author: String,
    val description: String,
    val entry: String,
    val includeDirs: List<String>,
    val capabilities: Set<String>,
) {
    companion object {
        const val CAP_SEARCH = "searchSongs"
        const val CAP_LYRICS = "getLyrics"
        const val CAP_COVERS = "searchCovers"

        const val SUPPORTED_API_VERSION = 5
        const val HOST_API_VERSION = 1

        fun parse(root: JSONObject): MetadataPluginManifest {
            val id = root.optString("id").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("插件 manifest 缺少 id")
            val name = root.optString("name").takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("插件 manifest 缺少 name")
            val apiVersion = root.optInt("apiVersion", 1).coerceAtLeast(1)
            if (apiVersion > SUPPORTED_API_VERSION) {
                throw IllegalArgumentException("插件 API 版本 $apiVersion 过高，最高支持 $SUPPORTED_API_VERSION")
            }
            val capabilities = root.optJSONArray("capabilities")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            add(array.optString(index))
                        }
                    }
                }
                .orEmpty()
                .filter { it == CAP_SEARCH || it == CAP_LYRICS || it == CAP_COVERS }
                .toSet()
            val includeDirs = root.optJSONArray("includeDirs")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val dir = array.optString(index).trim()
                            if (dir.isNotBlank() && !dir.contains("..") && !dir.startsWith("/")) {
                                add(dir.trimEnd('/'))
                            }
                        }
                    }
                }
                .orEmpty()
            return MetadataPluginManifest(
                id = id,
                name = name,
                versionCode = root.optInt("versionCode", 1).coerceAtLeast(1),
                versionName = root.optString("versionName", "1.0.0").ifBlank { "1.0.0" },
                apiVersion = apiVersion,
                minHostApiVersion = root.optInt("minHostApiVersion", 1).coerceAtLeast(1),
                author = root.optString("author"),
                description = root.optString("description"),
                entry = root.optString("entry", "source.js").ifBlank { "source.js" },
                includeDirs = includeDirs,
                capabilities = capabilities.ifEmpty { setOf(CAP_LYRICS) },
            )
        }
    }
}

data class MetadataPlugin(
    val manifest: MetadataPluginManifest,
    val script: String,
    val builtin: Boolean,
    val importedAtMs: Long,
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name
    val supportsLyrics: Boolean get() = MetadataPluginManifest.CAP_LYRICS in manifest.capabilities
    val supportsCovers: Boolean get() = MetadataPluginManifest.CAP_COVERS in manifest.capabilities
    val supportsSearch: Boolean get() = MetadataPluginManifest.CAP_SEARCH in manifest.capabilities

    fun capabilityLabel(): String = buildList {
        if (supportsLyrics) add("歌词")
        if (supportsCovers) add("封面")
        if (supportsSearch) add("歌曲")
    }.joinToString(" / ")
}

/** 插件包（zip）解析结果：manifest + 合并后的入口脚本。 */
data class MetadataPluginPackage(
    val manifest: MetadataPluginManifest,
    val script: String,
)

object MetadataPluginParser {

    /** 解析 zip 插件包字节，要求包内存在 manifest.json 与入口脚本。 */
    fun parseZip(bytes: ByteArray): MetadataPluginPackage {
        val files = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    files[entry.name.trimStart('/')] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (files.isEmpty()) throw IllegalArgumentException("插件包为空")
        val manifestBytes = files["manifest.json"]
            ?: files.entries.firstOrNull { it.key.endsWith("/manifest.json") }?.value
            ?: throw IllegalArgumentException("插件包缺少 manifest.json")
        val manifest = MetadataPluginParser.fromBytes(manifestBytes)
        val includeScripts = manifest.includeDirs.flatMap { dir ->
            files.entries
                .filter { (name, _) -> name.startsWith("$dir/") }
                .sortedBy { it.key }
                .map { it.value.toString(Charsets.UTF_8) }
        }
        val entryBytes = files[manifest.entry]
            ?: files.entries.firstOrNull { it.key.endsWith("/${manifest.entry}") }?.value
            ?: throw IllegalArgumentException("插件包缺少入口脚本 ${manifest.entry}")
        val script = (includeScripts + entryBytes.toString(Charsets.UTF_8)).joinToString("\n\n")
        if (script.isBlank()) throw IllegalArgumentException("插件入口脚本为空")
        return MetadataPluginPackage(manifest = manifest, script = script)
    }

    /** 单文件 JS 插件：按文件名合成默认清单，默认声明全部能力。 */
    fun parseSingleScript(fileName: String, script: String): MetadataPluginPackage {
        val normalized = script.trim()
        if (normalized.isBlank()) throw IllegalArgumentException("插件脚本为空")
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .removeSuffix(".js").ifBlank { "plugin" }
        val manifest = MetadataPluginManifest(
            id = "local.$safeName",
            name = safeName,
            versionCode = 1,
            versionName = "1.0.0",
            apiVersion = 1,
            minHostApiVersion = 1,
            author = "",
            description = "本地导入的单文件插件",
            entry = "$safeName.js",
            includeDirs = emptyList(),
            capabilities = setOf(
                MetadataPluginManifest.CAP_LYRICS,
                MetadataPluginManifest.CAP_COVERS,
                MetadataPluginManifest.CAP_SEARCH,
            ),
        )
        return MetadataPluginPackage(manifest = manifest, script = normalized)
    }

    fun fromBytes(bytes: ByteArray): MetadataPluginManifest =
        fromText(String(bytes, Charsets.UTF_8))

    fun fromText(text: String): MetadataPluginManifest =
        MetadataPluginManifest.parse(JSONObject(text))
}
