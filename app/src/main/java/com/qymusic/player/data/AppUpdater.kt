package com.qymusic.player.data

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：检查阿里云 OSS 上的 latest.json（回退 GitHub Releases），
 * 下载 APK 并拉起安装器。
 *
 * OSS 约定（Bucket：bql-qymusic1，地域 香港 oss-cn-hongkong，公共读，对象放 updates/ 目录）：
 *   latest.json                          ← 版本清单（每次发版最后更新它，根目录与 updates/ 各放一份）
 *   updates/QYMusic-<版本>-<时间戳>.apk   ← 安装包（文件名带版本，可长缓存）
 * 兼容旧的上传位置 `/updates/latest.json`（作为第二个源）。
 */
object AppUpdater {

    private const val LOG_TAG = "QYUpdate"

    /** 主更新源：OSS 根目录的版本清单。 */
    const val UPDATES_URL =
        "https://bql-qymusic1.oss-cn-hongkong.aliyuncs.com/latest.json"

    /** 早期约定的 /updates/ 目录；两种上传位置都能命中。 */
    private const val UPDATES_URL_LEGACY =
        "https://bql-qymusic1.oss-cn-hongkong.aliyuncs.com/updates/latest.json"

    /** 仓库最新 Release（历史渠道，作为兜底）。 */
    private const val GITHUB_UPDATES_URL =
        "https://api.github.com/repos/baiqinglun/qymusic/releases/latest"

    /**
     * 更新源按顺序尝试。第一个拿得到有效清单的源就是最终答案
     * （有新版就提示，没有就结束），后面的只在前面的源不可用时兜底。
     */
    private val UPDATE_SOURCES = listOf(
        UPDATES_URL,
        UPDATES_URL_LEGACY,
        GITHUB_UPDATES_URL,
    )

    /**
     * 网盘兜底渠道：OSS 不可用、或下载死活不动时，让用户自己去网盘拿安装包。
     *
     * 蓝奏云分享页要先输提取码、点“获取文件”才出真实直链（还带一次性 sign 和 Referer 校验），
     * DownloadManager 直连必然失败，所以这里只负责“复制提取码 + 用浏览器打开”，
     * 下载动作交给浏览器，装包交给系统安装器。
     */
    const val MIRROR_URL = "https://motianlun.lanzouw.com/iWNYW4fhq9tg"

    /** 网盘提取码，复制到剪贴板，用户切到浏览器粘贴即可。 */
    const val MIRROR_PASSWORD = "6scv"

    /**
     * 常见网盘/分享盘的域名特征。命中这些域名说明 apkUrl 是“网页 + 提取码”而不是文件流，
     * 只能复制提取码后跳浏览器；丢给 DownloadManager 只会下到一段 HTML 或 403。
     *
     * latest.json 里可以用任意网盘链接 + `password` 字段（也兼容 `shareCode`），
     * 应用会自动走浏览器流程，不需要为每个新网盘改代码。
     */
    private val NETDISK_HOST_HINTS = listOf(
        "lanzou", "lanzoux", "lanzoui", "lanzouv", "lanzouw", // 蓝奏云及其镜像站
        "123pan", "pan.baidu.com", "aliyundrive", "alipan",
        "cloud.189.cn", "caiyun.139.com", "quark.cn", "115.com",
    )

    /** apkUrl 是否指向网盘分享页（而不是可直接下载的 APK 直链）。 */
    fun isNetdiskUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return NETDISK_HOST_HINTS.any { host.contains(it) }
    }

    /** 用浏览器打开任意分享链接。 */
    fun openShareLink(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 复制提取码，用户切到浏览器直接粘贴。 */
    fun copyToClipboard(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    /** 用浏览器打开内置的备用网盘分享页。 */
    fun openMirror(context: Context) = openShareLink(context, MIRROR_URL)

    /** 复制内置备用网盘的提取码。 */
    fun copyMirrorPassword(context: Context) =
        copyToClipboard(context, "QYMusic", MIRROR_PASSWORD)

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Long,
        val notes: String,
        val apkUrl: String,
        /** 网盘提取码；apkUrl 是直链时为空。 */
        val sharePassword: String = "",
    ) {
        /** apkUrl 指向网盘分享页，只能跳浏览器下载。 */
        val isNetdisk: Boolean get() = isNetdiskUrl(apkUrl)
    }

    data class Progress(val status: Int, val percent: Int)

    /** 检查结果：有新版 / 已是最新 / 所有源都不可用（设置页手动检查要区分这三种）。 */
    sealed interface CheckResult {
        data class Update(val info: UpdateInfo) : CheckResult
        object UpToDate : CheckResult
        object Failed : CheckResult
    }

    /**
     * 检查更新。解析两种结构：
     *  - GitHub Releases API（tag_name / body / assets[].browser_download_url）
     *  - 简易 JSON（versionName / versionCode / notes / apkUrl）
     * 返回比当前版本新的信息；无更新或全部源不可用返回 null。
     */
    suspend fun check(context: Context): UpdateInfo? =
        (checkSources(context, UPDATE_SOURCES) as? CheckResult.Update)?.info

    /** 只检查指定地址（调试 / 特殊渠道包用）。 */
    suspend fun check(context: Context, updatesUrl: String): UpdateInfo? =
        (checkSources(context, listOf(updatesUrl)) as? CheckResult.Update)?.info

    /** 手动检查（设置页）：需要区分“已是最新”和“检查失败”。 */
    suspend fun checkResult(context: Context): CheckResult =
        checkSources(context, UPDATE_SOURCES)

    private suspend fun checkSources(
        context: Context,
        sources: List<String>,
    ): CheckResult = withContext(Dispatchers.IO) {
        val installedCode = versionCodeOf(installedVersionName(context))
        for (url in sources) {
            val info = runCatching {
                val root = fetchJson(url)
                if (root.has("tag_name")) parseGitHubRelease(root) else parseSimpleJson(root)
            }.onFailure { error ->
                Log.w(LOG_TAG, "更新源不可用 $url: ${error.message}")
            }.getOrNull() ?: continue
            // 统一用 versionName 语义比较（tag 与本地 versionName 同一规则折算）
            val newer = versionCodeOf(info.versionName) > installedCode
            Log.i(
                LOG_TAG,
                "更新源命中 $url → ${info.versionName}（${if (newer) "有新版" else "已是最新"}）",
            )
            // 这个源已经给出明确答案：有新版就提示，没有就结束，不再问后面的兜底源。
            return@withContext if (newer) CheckResult.Update(info) else CheckResult.UpToDate
        }
        Log.w(LOG_TAG, "所有更新源都不可用")
        CheckResult.Failed
    }

    internal fun parseGitHubRelease(root: JSONObject): UpdateInfo? {
        val tag = root.optString("tag_name")
        if (tag.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val url = asset.optString("browser_download_url")
            val name = asset.optString("name")
            if (url.isNotBlank() && name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = url
                break
            }
        }
        if (apkUrl.isBlank()) return null
        return UpdateInfo(
            versionName = tag.removePrefix("v"),
            versionCode = versionCodeOf(tag),
            notes = root.optString("body").trim(),
            apkUrl = apkUrl,
        )
    }

    internal fun parseSimpleJson(root: JSONObject): UpdateInfo? {
        val apkUrl = root.optString("apkUrl")
        if (apkUrl.isBlank()) return null
        // 网盘提取码两种常见字段名都认，方便手写 latest.json
        val sharePassword = root.optString("password").ifBlank { root.optString("shareCode") }
        return UpdateInfo(
            versionName = root.optString("versionName"),
            versionCode = root.optLong("versionCode"),
            notes = root.optString("notes"),
            apkUrl = apkUrl,
            sharePassword = sharePassword,
        )
    }

    /** "v0.1.14" -> 114：前三段数字各按百进制折算，兼容每段两位数。 */
    fun versionCodeOf(tag: String): Long {
        val numbers = Regex("[0-9]+").findAll(tag).take(3)
            .map { it.value.toLong() }.toList()
        var code = 0L
        for (i in 0 until 3) {
            code = code * 100 + (numbers.getOrElse(i) { 0 })
        }
        return code
    }

    private fun fetchJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP " + connection.responseCode)
            }
            val body = connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun installedVersionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

    /** 系统下载器下载 APK，完成后通过 ACTION_DOWNLOAD_COMPLETE 广播通知。 */
    fun enqueueDownload(context: Context, info: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("QYMusic " + info.versionName)
            .setDescription("正在下载更新…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "qymusic-" + info.versionName + ".apk",
            )
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    fun downloadedApkUri(context: Context, downloadId: Long): Uri? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.getUriForDownloadedFile(downloadId)
        }.getOrNull()?.takeIf { it != Uri.EMPTY }
    }

    /** 返回 (下载状态, 百分比)。 */
    fun downloadProgress(context: Context, downloadId: Long): Progress {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )
                return Progress(
                    status,
                    if (total > 0) (downloaded * 100 / total).toInt() else 0,
                )
            }
        }
        return Progress(-1, 0)
    }

    fun installApk(context: Context, apkUri: Uri) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
        ).setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        context.startActivity(intent)
    }

    /** 未授予「安装未知应用」权限时跳到本应用的开关页。 */
    fun openUnknownSourceSettings(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun canInstall(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
}
