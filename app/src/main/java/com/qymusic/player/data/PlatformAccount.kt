package com.qymusic.player.data

import android.content.Context
import java.io.File
import java.util.UUID
import org.json.JSONObject

/**
 * 平台歌单页的已登录账号。
 *
 * cookies 原样保存登录页拿到的全部 cookie（网易云 MUSIC_U、QQ uin/qm_keyst、
 * 酷我 uid/kw_token）；酷狗走扫码登录，没有 cookie，凭据放在 cookies 里
 * （userid/token），与网关请求的签名参数保持同一份来源。
 */
data class PlatformAccount(
    val platform: BuiltinMusicSource,
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val cookies: Map<String, String>,
    val loggedAtMs: Long,
) {
    fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
}

/** 平台账号持久化：filesDir 下单个 JSON 文件。 */
class PlatformAccountStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    fun loadAccounts(): Map<BuiltinMusicSource, PlatformAccount> = synchronized(lock) {
        if (!file.isFile) return@synchronized emptyMap()
        runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val accounts = root.optJSONObject(KEY_ACCOUNTS) ?: JSONObject()
            buildMap {
                for (key in accounts.keys()) {
                    val source = BuiltinMusicSource.entries.firstOrNull { it.key == key } ?: continue
                    val item = accounts.optJSONObject(key) ?: continue
                    val userId = item.optString(KEY_USER_ID).takeIf(String::isNotBlank) ?: continue
                    val cookies = item.optJSONObject(KEY_COOKIES) ?: JSONObject()
                    val cookieMap = buildMap {
                        for (name in cookies.keys()) {
                            put(name, cookies.optString(name))
                        }
                    }
                    put(
                        source,
                        PlatformAccount(
                            platform = source,
                            userId = userId,
                            nickname = item.optString(KEY_NICKNAME).ifBlank { source.displayName },
                            avatarUrl = item.optString(KEY_AVATAR).takeIf(String::isNotBlank),
                            cookies = cookieMap,
                            loggedAtMs = item.optLong(KEY_LOGGED_AT_MS),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveAccount(account: PlatformAccount) = synchronized(lock) {
        val root = readRoot()
        val accounts = root.optJSONObject(KEY_ACCOUNTS) ?: JSONObject()
        accounts.put(
            account.platform.key,
            JSONObject()
                .put(KEY_USER_ID, account.userId)
                .put(KEY_NICKNAME, account.nickname)
                .put(KEY_AVATAR, account.avatarUrl ?: JSONObject.NULL)
                .put(
                    KEY_COOKIES,
                    JSONObject().apply {
                        account.cookies.forEach { (name, value) -> put(name, value) }
                    },
                )
                .put(KEY_LOGGED_AT_MS, account.loggedAtMs),
        )
        writeRoot(root.put(KEY_ACCOUNTS, accounts))
    }

    fun removeAccount(platform: BuiltinMusicSource) = synchronized(lock) {
        val root = readRoot()
        val accounts = root.optJSONObject(KEY_ACCOUNTS) ?: JSONObject()
        accounts.remove(platform.key)
        writeRoot(root.put(KEY_ACCOUNTS, accounts))
    }

    /**
     * 酷狗网关签名用的设备标识：安装期内保持稳定即可，
     * 服务端只校验签名本身，不校验 mid 与设备的绑定关系。
     */
    fun loadDeviceGuid(): String = synchronized(lock) {
        val root = readRoot()
        val existing = root.optString(KEY_DEVICE_GUID).takeIf(String::isNotBlank)
        if (existing != null) return@synchronized existing
        val created = UUID.randomUUID().toString()
        writeRoot(root.put(KEY_DEVICE_GUID, created))
        created
    }

    private fun readRoot(): JSONObject =
        runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .getOrDefault(JSONObject())

    private fun writeRoot(root: JSONObject) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)
        temp.copyTo(file, overwrite = true)
        temp.delete()
    }

    private companion object {
        const val FILE_NAME = "platform_accounts.json"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_DEVICE_GUID = "deviceGuid"
        const val KEY_USER_ID = "userId"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AVATAR = "avatarUrl"
        const val KEY_COOKIES = "cookies"
        const val KEY_LOGGED_AT_MS = "loggedAtMs"
    }
}
