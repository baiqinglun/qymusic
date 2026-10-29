package com.qymusic.player.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 应用内更新：检查 GitHub Releases（或任意同结构 JSON），下载 APK 并拉起安装器。 */
object AppUpdater {

    /** 更新源：仓库的最新 Release API。 */
    const val UPDATES_URL =
        "https://api.github.com/repos/baiqinglun/qymusic/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Long,
        val notes: String,
        val apkUrl: String,
    )

    data class Progress(val status: Int, val percent: Int)

    /**
     * 检查更新。解析两种结构：
     *  - GitHub Releases API（tag_name / body / assets[].browser_download_url）
     *  - 简易 JSON（versionCode / versionName / notes / apkUrl）
     * 返回比当前版本新的信息；无更新或网络失败返回 null。
     */
    suspend fun check(context: Context, updatesUrl: String = UPDATES_URL): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = fetchJson(updatesUrl)
                val info = if (root.has("tag_name")) {
                    parseGitHubRelease(root)
                } else {
                    parseSimpleJson(root)
                } ?: return@runCatching null
                // 统一用 versionName 语义比较（tag 与本地 versionName 同一规则折算）
                val installedName = installedVersionName(context)
                if (versionCodeOf(info.versionName) > versionCodeOf(installedName)) {
                    info
                } else {
                    null
                }
            }.getOrNull()
        }

    private fun parseGitHubRelease(root: JSONObject): UpdateInfo? {
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

    private fun parseSimpleJson(root: JSONObject): UpdateInfo? {
        val apkUrl = root.optString("apkUrl")
        if (apkUrl.isBlank()) return null
        return UpdateInfo(
            versionName = root.optString("versionName"),
            versionCode = root.optLong("versionCode"),
            notes = root.optString("notes"),
            apkUrl = apkUrl,
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
