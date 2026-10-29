package com.qymusic.player.data

import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** 平台歌单（用户在酷狗/QQ/酷我/网易云账号下的歌单）。 */
data class PlatformPlaylist(
    val platform: BuiltinMusicSource,
    /** 打开歌单用的 id：网易云歌单 id / QQ dirid(dissid) / 酷狗 global_collection_id / 酷我 pid。 */
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val collected: Boolean,
)

/** 酷狗扫码登录的二维码内容。 */
data class PlatformLoginQr(
    val key: String,
    /** 服务端直接返回的 base64 PNG（含 data:image/png;base64, 前缀）。 */
    val qrImageBase64: String,
)

/** 酷狗扫码状态：0 过期，1 等待扫码，2 已扫码待确认，4 成功。 */
data class KugouQrPollState(
    val status: Int,
    val account: PlatformAccount?,
)

/** 网易云扫码登录会话：[unikey] 参与轮询，[qrContent] 为本地渲染二维码的内容。 */
data class NeteaseQrSession(
    val unikey: String,
    val qrContent: String,
)

/** 网易云扫码状态：0 过期，1 等待扫码，2 已扫码待确认，3 登录成功。 */
data class NeteaseQrPollState(
    val status: Int,
    val account: PlatformAccount?,
)

/**
 * 各平台"用户歌单"拉取客户端。接口均为 2026-10 实测可用的公开网页端/移动端协议，
 * 鉴权统一使用 [PlatformAccount.cookies] 里保存的登录凭据。
 *
 * [deviceGuid] 是安装级稳定标识，只参与酷狗网关签名的 mid 计算。
 */
class PlatformPlaylistClient(private val deviceGuid: String) {

    private val Random = SecureRandom()

    // ---- 对外入口 ----

    fun fetchPlaylists(account: PlatformAccount): List<PlatformPlaylist> = when (account.platform) {
        BuiltinMusicSource.KUGOU -> fetchKugouPlaylists(account)
        BuiltinMusicSource.QQ -> fetchQqPlaylists(account)
        BuiltinMusicSource.KUWO -> fetchKuwoPlaylists(account)
        BuiltinMusicSource.NETEASE -> fetchNeteasePlaylists(account)
    }

    /** 歌单内歌曲，转成与内置搜索结果同构的 [LxMusicSong]，后续解析/播放/下载复用音源链路。 */
    fun fetchPlaylistTracks(
        account: PlatformAccount,
        playlist: PlatformPlaylist,
    ): List<LxMusicSong> = when (account.platform) {
        BuiltinMusicSource.KUGOU -> fetchKugouTracks(playlist)
        BuiltinMusicSource.QQ -> fetchQqTracks(account, playlist)
        BuiltinMusicSource.KUWO -> fetchKuwoTracks(playlist)
        BuiltinMusicSource.NETEASE -> fetchNeteaseTracks(account, playlist)
    }

    // ---- 酷狗扫码登录 ----

    fun fetchKugouQr(deviceGuid: String): PlatformLoginQr {
        val params = mutableMapOf(
            "appid" to "1001",
            "type" to "1",
            "plat" to "4",
            "qrcode_txt" to KUGOU_QRCODE_TXT,
            "srcappid" to "2919",
        )
        addKugouDefaults(params, deviceGuid)
        params["signature"] = kugouSignature(KUGOU_SALT_WEB, params, "")
        val response = LxHttpClient.execute(
            url = "https://login-user.kugou.com/v2/qrcode?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kugouHeaders(deviceGuid)),
        )
        ensureSuccess(response, "获取酷狗登录二维码")
        val data = JSONObject(response.body).optJSONObject("data") ?: JSONObject()
        val key = data.optString("qrcode").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("酷狗未返回二维码")
        return PlatformLoginQr(
            key = key,
            qrImageBase64 = data.optString("qrcode_img"),
        )
    }

    fun pollKugouQr(key: String, deviceGuid: String): KugouQrPollState {
        val params = mutableMapOf(
            "plat" to "4",
            "appid" to "1005",
            "srcappid" to "2919",
            "qrcode" to key,
            "dev" to "",
        )
        addKugouDefaults(params, deviceGuid)
        params["signature"] = kugouSignature(KUGOU_SALT_WEB, params, "")
        val response = LxHttpClient.execute(
            url = "https://login-user.kugou.com/v2/get_userinfo_qrcode?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kugouHeaders(deviceGuid)),
        )
        ensureSuccess(response, "检查扫码状态")
        val root = JSONObject(response.body)
        val data = root.optJSONObject("data") ?: JSONObject()
        val status = data.optInt("status", -1)
        if (status != 4) return KugouQrPollState(status = status, account = null)
        val userid = data.optString("userid").takeIf(String::isNotBlank)
            ?: data.optLong("userid").takeIf { it > 0 }?.toString()
            ?: throw IllegalStateException("酷狗登录成功但缺少用户信息")
        val token = data.optString("token").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("酷狗登录成功但缺少凭据")
        return KugouQrPollState(
            status = 4,
            account = PlatformAccount(
                platform = BuiltinMusicSource.KUGOU,
                userId = userid,
                nickname = data.optString("nickname").ifBlank { BuiltinMusicSource.KUGOU.displayName },
                avatarUrl = data.optString("avatar").takeIf(String::isNotBlank),
                cookies = mapOf("userid" to userid, "token" to token),
                loggedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // ---- 酷狗：手机号 + 短信验证码登录 ----

    /** 发送短信验证码。失败时抛出带服务端信息的异常。 */
    fun sendKugouSmsCode(mobile: String) {
        val bodyText = JSONObject()
            .put("businessid", 5)
            .put("mobile", mobile)
            .put("plat", 3)
            .toString()
        val params = mutableMapOf<String, String>()
        addKugouDefaults(params, deviceGuid)
        params["signature"] = kugouSignature(KUGOU_SALT_ANDROID, params, bodyText)
        val response = LxHttpClient.execute(
            url = "http://login.user.kugou.com/v7/send_mobile_code?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "POST")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kugouLoginHeaders())
                .put("body", bodyText),
        )
        ensureSuccess(response, "发送验证码")
        val root = JSONObject(response.body)
        if (root.optInt("status", 0) != 1) {
            throw IllegalStateException(kugouLoginError(root, "验证码发送失败"))
        }
    }

    /**
     * 手机号 + 短信验证码登录。返回可直接保存的账号（含 userid/token）。
     * 请求体经 AES 加密、会话密钥再经裸 RSA（零填充）加密，2026-10 实测服务端可解。
     */
    fun loginKugouBySms(mobile: String, code: String): PlatformAccount {
        val tempKey = randomLowercase(16)
        val dateTimeMs = System.currentTimeMillis()
        val paramsHex = kugouAesEncryptHex(
            data = JSONObject().put("mobile", mobile).put("code", code).toString(),
            tempKey = tempKey,
        )
        val keySign = md5Hex("$KUGOU_APPID$KUGOU_SALT_ANDROID$KUGOU_CLIENTVER$dateTimeMs")
        val pkPlain = JSONObject()
            .put("clienttime_ms", dateTimeMs)
            .put("key", tempKey)
            .toString()
        val bodyText = JSONObject()
            .put("plat", 1)
            .put("support_multi", 1)
            .put("t1", 0)
            .put("t2", 0)
            .put("clienttime_ms", dateTimeMs)
            .put("mobile", maskKugouMobile(mobile))
            .put("key", keySign)
            .put("pk", kugouRawRsaUppercaseHex(pkPlain))
            .put("params", paramsHex)
            .put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            .toString()
        val params = mutableMapOf<String, String>()
        addKugouDefaults(params, deviceGuid)
        params["signature"] = kugouSignature(KUGOU_SALT_ANDROID, params, bodyText)
        val response = LxHttpClient.execute(
            url = "https://loginserviceretry.kugou.com/v7/login_by_verifycode?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "POST")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kugouLoginHeaders())
                .put("body", bodyText),
        )
        ensureSuccess(response, "酷狗登录")
        val root = JSONObject(response.body)
        if (root.optInt("status", 0) != 1) {
            throw IllegalStateException(kugouLoginError(root, "登录失败"))
        }
        val data = root.optJSONObject("data") ?: JSONObject()
        // secu_params 是用本次会话 tempKey 加密的凭据串，解出 token/userid
        val secured = data.optString("secu_params").takeIf(String::isNotBlank)
        val credentials = secured?.let { hex ->
            runCatching { kugouAesDecryptHex(hex, tempKey) }.getOrNull()
        } ?: JSONObject()
        val userid = credentials.optString("userid")
            .takeIf(String::isNotBlank)
            ?: data.optLong("userid").takeIf { it > 0 }?.toString()
            ?: throw IllegalStateException("酷狗登录成功但缺少用户信息")
        val token = credentials.optString("token").takeIf(String::isNotBlank)
            ?: data.optString("token").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("酷狗登录成功但缺少凭据")
        return PlatformAccount(
            platform = BuiltinMusicSource.KUGOU,
            userId = userid,
            nickname = data.optString("nickname").ifBlank { BuiltinMusicSource.KUGOU.displayName },
            avatarUrl = data.optString("avatar").takeIf(String::isNotBlank),
            cookies = mapOf("userid" to userid, "token" to token),
            loggedAtMs = System.currentTimeMillis(),
        )
    }

    /** 酷狗登录接口公共头（LOGIN 协议 UA）。 */
    private fun kugouLoginHeaders(): JSONObject = JSONObject()
        .put("User-Agent", KUGOU_LOGIN_UA)
        .put("support-calm", "1")
        .put("dfid", "-")
        .put("mid", kugouMid(deviceGuid))
        .put("clienttime", (System.currentTimeMillis() / 1000).toString())
        .put("kg-rc", "1")
        .put("Content-Type", "application/json;charset=utf-8")

    /** 服务端失败信息优先取 data 字段（字符串或对象内 info/msg）。 */
    private fun kugouLoginError(root: JSONObject, fallback: String): String {
        val data = root.opt("data")
        when (data) {
            is String -> if (data.isNotBlank()) return data
            is JSONObject -> {
                firstText(data, "info", "msg", "message").takeIf(String::isNotBlank)
                    ?.let { return it }
            }
        }
        val errorCode = root.optInt("error_code", 0)
        if (errorCode == 20021) return "验证码错误"
        return if (errorCode == 0) fallback else "$fallback（错误码 $errorCode）"
    }

    private fun maskKugouMobile(mobile: String): String =
        if (mobile.length >= 11) {
            mobile.take(2) + "*****" + mobile[10]
        } else {
            mobile
        }

    private fun randomLowercase(length: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        return buildString {
            repeat(length) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    /**
     * AES-256-CBC/PKCS7 输出 hex：key = md5(tempKey) 的 32 个 ASCII 字节，
     * iv = key 字符串的后 16 字节（与酷狗客户端协议一致）。
     */
    private fun kugouAesEncryptHex(data: String, tempKey: String): String {
        val keyString = md5Hex(tempKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyString.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(keyString.substring(16).toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun kugouAesDecryptHex(hex: String, tempKey: String): JSONObject {
        val keyString = md5Hex(tempKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyString.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(keyString.substring(16).toByteArray(Charsets.UTF_8)),
        )
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val plain = cipher.doFinal(bytes).toString(Charsets.UTF_8)
        return JSONObject(plain)
    }

    /**
     * 裸 RSA（消息零填充到 128 字节后直接 modPow，不做 PKCS#1 填充），
     * 输出大写 hex。酷狗登录协议按此实现，官方端同构。
     */
    private fun kugouRawRsaUppercaseHex(data: String): String {
        val message = data.toByteArray(Charsets.UTF_8)
        val padded = ByteArray(KUGOU_RSA_KEY_BYTES)
        message.copyInto(padded)
        val result = BigInteger(padded).modPow(KUGOU_RSA_E, KUGOU_RSA_N)
        return result.toString(16).padStart(KUGOU_RSA_KEY_BYTES * 2, '0').uppercase()
    }

    // ---- 酷狗：云歌单网关（android 签名）----

    private fun fetchKugouPlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val body = JSONObject()
            .put("userid", account.userId.toLongOrNull() ?: 0L)
            .put("token", account.cookies["token"].orEmpty())
            .put("total_ver", 979)
            .put("type", 2)
            .put("page", 1)
            .put("pagesize", KUGOU_PAGE_SIZE)
        // 签名与请求体必须用完全相同的一串 JSON 文本
        val bodyText = body.toString()
        val params = mutableMapOf(
            "plat" to "1",
            "userid" to account.userId,
            "token" to account.cookies["token"].orEmpty(),
        )
        addKugouDefaults(params, deviceGuid)
        params["signature"] = kugouSignature(KUGOU_SALT_ANDROID, params, bodyText)
        val response = LxHttpClient.execute(
            url = "https://gateway.kugou.com/v7/get_all_list?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "POST")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kugouGatewayHeaders())
                .put("body", bodyText),
        )
        return buildKugouPlaylists(response)
    }

    private fun buildKugouPlaylists(response: LxHttpResponse): List<PlatformPlaylist> {
        ensureSuccess(response, "拉取酷狗歌单")
        val root = JSONObject(response.body)
        val errorCode = root.optInt("error_code", 0)
        if (errorCode != 0) {
            throw IllegalStateException(kugouErrorText(errorCode))
        }
        val data = root.optJSONObject("data") ?: JSONObject()
        val array = firstArray(data, "list", "info", "special_list", "data")
            ?: JSONArray()
        val playlists = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = firstText(item, "name", "specialname", "list_name", "title")
                    .ifBlank { "未命名歌单" }
                val gid = firstText(item, "global_collection_id", "global_specialid", "gid")
                val id = gid.ifBlank {
                    firstText(item, "id", "specialid", "listid")
                }
                if (id.isBlank()) continue
                val cover = firstText(item, "img", "pic", "imgurl", "cover", "img_cover")
                    .takeIf(String::isNotBlank)
                    ?.replace("{size}", "240")
                add(
                    PlatformPlaylist(
                        platform = BuiltinMusicSource.KUGOU,
                        id = id,
                        name = name,
                        coverUrl = cover,
                        trackCount = firstInt(item, "count", "song_count", "track_count", "total", "songcnt"),
                        collected = item.optInt("type", 0) == 2 || item.optBoolean("is_collected", false),
                    ),
                )
            }
        }
        return playlists
    }

    /** 酷狗歌单详情：公开 CDN 接口，按 global_collection_id 取歌。 */
    private fun fetchKugouTracks(playlist: PlatformPlaylist): List<LxMusicSong> {
        val params = linkedMapOf(
            "specialid" to "0",
            "need_sort" to "1",
            "module" to "CloudMusic",
            "clientver" to "11409",
            "pagesize" to "500",
            "global_collection_id" to playlist.id,
            "userid" to "0",
            "page" to "1",
            "type" to "1",
            "area_code" to "1",
            "appid" to "1005",
        )
        val joined = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val signature = kugouSignature(KUGOU_SALT_ANDROID, params, "")
        val response = LxHttpClient.execute(
            url = "http://pubsongscdn.tx.kugou.com/v2/get_other_list_file?$joined&signature=$signature",
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", defaultHeaders(BuiltinMusicSource.KUGOU)),
        )
        ensureSuccess(response, "拉取酷狗歌单歌曲")
        val data = JSONObject(response.body).optJSONObject("data") ?: JSONObject()
        val array = data.optJSONArray("info") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val hash = item.optString("hash").takeIf(String::isNotBlank) ?: continue
                // 酷狗该接口的 name 是 "歌手 - 歌名" 拼串
                val combined = cleanText(item.optString("name").ifBlank { item.optString("filename") })
                val (artist, title) = splitCombinedName(combined)
                val durationSeconds = item.optLong("duration").coerceAtLeast(0L)
                val raw = JSONObject()
                    .put("hash", hash)
                    .put("songmid", hash)
                    .put("id", hash)
                    .put("rid", item.optString("audio_id"))
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", item.optString("remark", ""))
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = hash,
                        sourceKey = BuiltinMusicSource.KUGOU.key,
                        sourceName = BuiltinMusicSource.KUGOU.displayName,
                        title = title,
                        artist = artist,
                        album = item.optString("remark", ""),
                        durationMs = durationSeconds * 1000L,
                        coverUrl = item.optString("cover").takeIf(String::isNotBlank)
                            ?.replace("{size}", "240"),
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
    }

    // ---- QQ 音乐 ----

    private fun fetchQqPlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val created = fetchQqCreatedPlaylists(account)
        val collected = fetchQqCollectedPlaylists(account)
        return created + collected
    }

    private fun fetchQqCreatedPlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val uin = account.userId
        val params = mapOf(
            "hostUin" to "0",
            "hostuin" to uin,
            "sin" to "0",
            "size" to "300",
            "g_tk" to qqGtk(account),
            "loginUin" to uin,
            "format" to "json",
            "inCharset" to "utf8",
            "outCharset" to "utf-8",
            "notice" to "0",
            "platform" to "yqq.json",
            "needNewCode" to "0",
        )
        val response = LxHttpClient.execute(
            url = "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", qqHeaders(account, "https://y.qq.com/portal/profile.html")),
        )
        ensureSuccess(response, "拉取QQ音乐歌单")
        val data = JSONObject(response.body).optJSONObject("data") ?: JSONObject()
        val array = data.optJSONArray("disslist") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = firstText(item, "dirid", "dissid", "tid").takeIf(String::isNotBlank) ?: continue
                add(
                    PlatformPlaylist(
                        platform = BuiltinMusicSource.QQ,
                        id = id,
                        name = cleanText(firstText(item, "diss_name", "dissname", "name")).ifBlank { "未命名歌单" },
                        coverUrl = firstText(item, "diss_cover", "imgurl", "logo", "pic").takeIf(String::isNotBlank),
                        trackCount = firstInt(item, "song_cnt", "songcount", "song_count"),
                        collected = false,
                    ),
                )
            }
        }
    }

    private fun fetchQqCollectedPlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val uin = account.userId
        val params = mapOf(
            "ct" to "20",
            "cid" to "205360956",
            "userid" to uin,
            "reqtype" to "3",
            "sin" to "0",
            "ein" to "99",
            "g_tk" to qqGtk(account),
            "format" to "json",
        )
        val response = LxHttpClient.execute(
            url = "https://c.y.qq.com/fav/fcgi-bin/fcg_get_profile_order_asset.fcg?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", qqHeaders(account, "https://y.qq.com/portal/profile.html")),
        )
        if (response.statusCode !in 200..299) return emptyList()
        val data = runCatching {
            JSONObject(response.body).optJSONObject("data") ?: JSONObject()
        }.getOrDefault(JSONObject())
        if (data.optInt("code", 0) == 4000) return emptyList()
        val array = data.optJSONArray("cdlist") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = firstText(item, "dissid", "disstid", "tid").takeIf(String::isNotBlank) ?: continue
                add(
                    PlatformPlaylist(
                        platform = BuiltinMusicSource.QQ,
                        id = id,
                        name = cleanText(firstText(item, "dissname", "diss_name", "name")).ifBlank { "未命名歌单" },
                        coverUrl = firstText(item, "logo", "diss_cover", "imgurl", "pic").takeIf(String::isNotBlank),
                        trackCount = firstInt(item, "song_cnt", "songcount", "song_count"),
                        collected = true,
                    ),
                )
            }
        }
    }

    private fun fetchQqTracks(account: PlatformAccount, playlist: PlatformPlaylist): List<LxMusicSong> {
        val params = mapOf(
            "type" to "1",
            "json" to "1",
            "utf8" to "1",
            "onlysong" to "0",
            "new_format" to "1",
            "disstid" to playlist.id,
            "loginUin" to "0",
            "hostUin" to "0",
            "format" to "json",
            "inCharset" to "utf8",
            "outCharset" to "utf-8",
            "notice" to "0",
            "platform" to "yqq.json",
            "needNewCode" to "0",
        )
        val response = LxHttpClient.execute(
            url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", qqHeaders(account, "https://y.qq.com/")),
        )
        ensureSuccess(response, "拉取QQ音乐歌单歌曲")
        val root = JSONObject(response.body)
        val cd = root.optJSONArray("cdlist")?.optJSONObject(0)
            ?: throw IllegalStateException("QQ音乐歌单为空或不可见")
        val array = cd.optJSONArray("songlist") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val mid = firstText(item, "songmid", "mid", "id").takeIf(String::isNotBlank) ?: continue
                val title = cleanText(firstText(item, "songname", "name", "title")).ifBlank { "未知歌曲" }
                val artist = item.optJSONArray("singer")?.joinNames().orEmpty().ifBlank { "未知歌手" }
                val album = when (val albumValue = item.opt("albumname") ?: item.opt("album")) {
                    is JSONObject -> albumValue.optString("name")
                    else -> albumValue?.toString().orEmpty()
                }
                val durationSeconds = item.optLong("interval").coerceAtLeast(0L)
                val raw = JSONObject()
                    .put("songmid", mid)
                    .put("id", mid)
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = mid,
                        sourceKey = BuiltinMusicSource.QQ.key,
                        sourceName = BuiltinMusicSource.QQ.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSeconds * 1000L,
                        coverUrl = null,
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
    }

    // ---- 酷我 ----

    private fun fetchKuwoPlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val token = account.cookies["kw_token"].orEmpty()
        val params = mapOf(
            "uid" to account.userId,
            "pn" to "1",
            "rn" to "300",
            "httpsStatus" to "1",
        )
        val response = LxHttpClient.execute(
            url = "https://kuwo.cn/api/www/user/playList?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", kuwoHeaders(token)),
        )
        ensureSuccess(response, "拉取酷我歌单")
        val root = parseLooseJson(response.body)
        if (root.optBoolean("success", true).not()) {
            throw IllegalStateException("酷我接口拒绝访问，请重新登录后再试")
        }
        val data = root.optJSONObject("data") ?: JSONObject()
        val array = firstArray(data, "list", "playlist", "data") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = firstText(item, "pid", "id", "playlistId").takeIf(String::isNotBlank) ?: continue
                val trackCount = firstInt(item, "total", "count", "res_total", "musicList")
                    .takeIf { it > 0 }
                    ?: item.optJSONArray("musicList")?.length()
                    ?: 0
                add(
                    PlatformPlaylist(
                        platform = BuiltinMusicSource.KUWO,
                        id = id,
                        name = cleanText(firstText(item, "name", "specialname", "list_name", "title"))
                            .ifBlank { "未命名歌单" },
                        coverUrl = firstText(item, "img", "pic", "imgurl", "cover").takeIf(String::isNotBlank),
                        trackCount = trackCount,
                        collected = false,
                    ),
                )
            }
        }
    }

    /** 酷我歌单详情走公开的 nplserver 老接口，无需登录态。 */
    private fun fetchKuwoTracks(playlist: PlatformPlaylist): List<LxMusicSong> {
        val params = mapOf(
            "op" to "getlistinfo",
            "pid" to playlist.id,
            "pn" to "0",
            "rn" to "500",
            "encode" to "utf8",
            "keyset" to "pl2012",
            "identity" to "kuwo",
            "pcmp4" to "1",
            "vipver" to "MUSIC_9.0.5.0_W1",
            "newver" to "1",
        )
        val response = LxHttpClient.execute(
            url = "http://nplserver.kuwo.cn/pl.svc?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", defaultHeaders(BuiltinMusicSource.KUWO)),
        )
        ensureSuccess(response, "拉取酷我歌单歌曲")
        val root = parseLooseJson(response.body)
        val array = root.optJSONArray("musiclist") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val rid = firstText(item, "musicrid", "id", "MUSICRID")
                    .removePrefix("MUSIC_")
                    .takeIf(String::isNotBlank) ?: continue
                val title = cleanText(firstText(item, "name", "NAME", "songname")).ifBlank { "未知歌曲" }
                val artist = cleanText(firstText(item, "artist", "ARTIST")).ifBlank { "未知歌手" }
                val album = cleanText(firstText(item, "album", "ALBUM"))
                val durationSeconds = item.optLong("duration").coerceAtLeast(0L)
                val raw = JSONObject()
                    .put("musicrid", rid)
                    .put("rid", rid)
                    .put("id", rid)
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = rid,
                        sourceKey = BuiltinMusicSource.KUWO.key,
                        sourceName = BuiltinMusicSource.KUWO.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSeconds * 1000L,
                        coverUrl = cleanText(item.optString("albumpic")).takeIf(String::isNotBlank),
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
    }

    // ---- 网易云：扫码登录（主）+ 手机号短信验证码登录（备） ----
    // 短信接口（weapi/eapi 均是）对第三方客户端普遍返回"当前账号存在安全风险"风控（2026-10 真机实测），
    // 扫码登录走官方 App 认可的网页协议，不受该风控影响，为默认方式。
    // 短信登录主走 eapi（App 端协议），eapi 无有效响应时自动回落 weapi（网页端协议）；
    // 两边都被风控拦截时，界面提供"改用网页登录"的交互式兜底。

    /** 发送短信验证码。失败时抛出带服务端信息的异常。 */
    fun sendNeteaseCaptcha(mobile: String) {
        val eapiData = JSONObject().put("cellphone", mobile).put("ctcode", 86).toString()
        val weapiText = """{"cellphone":"$mobile","ctcode":86,"csrf_token":""}"""
        val response = neteaseSmsRequest(
            eapiPath = "/eapi/sms/captcha/sent",
            eapiData = eapiData,
            weapiPath = "/weapi/sms/captcha/sent",
            weapiText = weapiText,
        )
        ensureSuccess(response, "发送验证码")
        val root = parseLooseJson(response.body)
        if (root.optInt("code", 0) != 200) {
            throw IllegalStateException(neteaseRiskHint(neteaseWeapiError(root, "验证码发送失败")))
        }
    }

    /** 手机号 + 短信验证码登录，凭据取响应里的 MUSIC_U cookie。 */
    fun loginNeteaseBySms(mobile: String, code: String): PlatformAccount {
        val eapiData = JSONObject()
            .put("phone", mobile)
            .put("countrycode", "86")
            .put("captcha", code)
            .put("rememberLogin", "true")
            .toString()
        val weapiText = """{"phone":"$mobile","countrycode":"86","captcha":"$code","rememberLogin":"true","csrf_token":""}"""
        val response = neteaseSmsRequest(
            eapiPath = "/eapi/login/cellphone",
            eapiData = eapiData,
            weapiPath = "/weapi/login/cellphone",
            weapiText = weapiText,
        )
        ensureSuccess(response, "网易云登录")
        val root = parseLooseJson(response.body)
        if (root.optInt("code", 0) != 200) {
            throw IllegalStateException(neteaseRiskHint(neteaseWeapiError(root, "网易云登录失败")))
        }
        val profile = root.optJSONObject("profile")
            ?: throw IllegalStateException("网易云登录成功但缺少用户信息")
        val musicU = extractNeteaseMusicU(root, response)
            ?: throw IllegalStateException("网易云登录成功但缺少凭据")
        return PlatformAccount(
            platform = BuiltinMusicSource.NETEASE,
            userId = profile.optLong("userId").takeIf { it > 0 }?.toString()
                ?: throw IllegalStateException("网易云登录成功但缺少用户信息"),
            nickname = profile.optString("nickname").ifBlank { BuiltinMusicSource.NETEASE.displayName },
            avatarUrl = profile.optString("avatarUrl").takeIf(String::isNotBlank),
            cookies = mapOf("MUSIC_U" to musicU),
            loggedAtMs = System.currentTimeMillis(),
        )
    }

    // ---- 网易云：扫码登录 ----
    // 协议（2026-10 本机实测：unikey 返回 200、轮询返回 801 等待扫码）：
    // weapi POST /weapi/login/qrcode/unikey（{"type":"1"}）拿 unikey；
    // 二维码内容 https://music.163.com/login?codekey=<unikey>，本地渲染（服务端不提供图）；
    // 轮询 weapi POST /weapi/login/qrcode/client/login（{"csrf_token":"","key":...,"type":"1"}），
    // 800 过期 / 801 等待 / 802 已扫码 / 803 成功（MUSIC_U 走 Set-Cookie）。

    /** 获取扫码登录会话。 */
    fun fetchNeteaseQrSession(): NeteaseQrSession {
        val response = weapiPost("/weapi/login/qrcode/unikey", """{"type":"1"}""")
        ensureSuccess(response, "获取网易云登录二维码")
        val root = parseLooseJson(response.body)
        if (root.optInt("code", 0) != 200) {
            throw IllegalStateException(neteaseWeapiError(root, "获取网易云登录二维码失败"))
        }
        val unikey = root.optString("unikey").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("网易云未返回登录二维码")
        return NeteaseQrSession(unikey = unikey, qrContent = neteaseQrContentOf(unikey))
    }

    /** 轮询扫码状态；803 时取 MUSIC_U 并拉取用户信息组装账号。 */
    fun pollNeteaseQr(session: NeteaseQrSession): NeteaseQrPollState {
        val text = """{"csrf_token":"","key":"${session.unikey}","type":"1"}"""
        val response = weapiPost("/weapi/login/qrcode/client/login", text)
        ensureSuccess(response, "检查网易云扫码状态")
        val root = parseLooseJson(response.body)
        val status = neteaseQrStatusOf(root.optInt("code", 0))
            ?: throw IllegalStateException(neteaseWeapiError(root, "检查网易云扫码状态失败"))
        if (status != 3) return NeteaseQrPollState(status = status, account = null)
        val musicU = extractNeteaseMusicU(root, response)
            ?: throw IllegalStateException("扫码成功但缺少登录凭据")
        return NeteaseQrPollState(status = 3, account = fetchNeteaseProfile(musicU))
    }

    /** 用 MUSIC_U 拉取 userId/昵称（/api/w/nuser/account/get）。 */
    private fun fetchNeteaseProfile(musicU: String): PlatformAccount {
        val response = LxHttpClient.execute(
            url = "https://music.163.com/api/w/nuser/account/get",
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put(
                    "headers",
                    defaultHeaders(BuiltinMusicSource.NETEASE)
                        .put("Cookie", "MUSIC_U=$musicU"),
                ),
        )
        ensureSuccess(response, "获取网易云用户信息")
        val root = parseLooseJson(response.body)
        if (root.optInt("code", 200) !in 200..299) {
            throw IllegalStateException("获取网易云用户信息失败（code ${root.optInt("code")}）")
        }
        val profile = root.optJSONObject("profile")
        val userId = profile?.optLong("userId")?.takeIf { it > 0 }?.toString()
            ?: root.optJSONObject("account")?.optLong("id")?.takeIf { it > 0 }?.toString()
            ?: throw IllegalStateException("网易云登录成功但缺少用户信息")
        return PlatformAccount(
            platform = BuiltinMusicSource.NETEASE,
            userId = userId,
            nickname = profile?.optString("nickname").orEmpty()
                .ifBlank { BuiltinMusicSource.NETEASE.displayName },
            avatarUrl = profile?.optString("avatarUrl")?.takeIf(String::isNotBlank),
            cookies = mapOf("MUSIC_U" to musicU),
            loggedAtMs = System.currentTimeMillis(),
        )
    }

    /** 短信接口被风控时在报错后提示改走扫码。 */
    private fun neteaseRiskHint(message: String): String =
        if (message.contains("安全风险")) "$message\n短信通道被风控，请改用扫码登录" else message

    /**
     * 短信登录双协议：先 eapi；eapi 无有效 JSON 响应（空体常见于风控/地域限制）
     * 时自动回落 weapi。
     */
    private fun neteaseSmsRequest(
        eapiPath: String,
        eapiData: String,
        weapiPath: String,
        weapiText: String,
    ): LxHttpResponse {
        val eapiResponse = runCatching { neteaseEapiPost(eapiPath, eapiData) }.getOrNull()
        if (eapiResponse != null &&
            eapiResponse.statusCode in 200..299 &&
            eapiResponse.body.isNotBlank() &&
            eapiResponse.body.trimStart().startsWith("{")
        ) {
            return eapiResponse
        }
        return weapiPost(weapiPath, weapiText)
    }

    /** eapi POST：path+JSON 做参数 AES-ECB 加密，网易云 App 端统一协议。 */
    private fun neteaseEapiPost(path: String, dataJson: String): LxHttpResponse {
        val fullData = JSONObject(dataJson)
            .put(
                "header",
                JSONObject()
                    .put("os", "android")
                    .put("appver", NETEASE_APPVER)
                    .put("osver", "Android-14")
                    .put("deviceId", deviceGuid)
                    .put("clienttime", System.currentTimeMillis()),
            )
            .toString()
        val encrypted = neteaseEapiEncrypt(path, fullData)
        return LxHttpClient.execute(
            url = "https://interface3.music.163.com$path",
            options = JSONObject()
                .put("method", "POST")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", neteaseEapiHeaders())
                .put("body", "params=" + encode(encrypted)),
        )
    }

    private fun neteaseEapiEncrypt(path: String, dataJson: String): String {
        val message = (path + "-36cd479b6b5-" + dataJson).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(NETEASE_EAPI_KEY, "AES"))
        return cipher.doFinal(message)
            .joinToString("") { byte -> "%02X".format(byte) }
    }

    private fun neteaseEapiHeaders(): JSONObject = JSONObject()
        .put("User-Agent", NETEASE_APP_UA)
        .put("Content-Type", "application/x-www-form-urlencoded")
        .put("Cookie", "os=android; appver=$NETEASE_APPVER; osver=Android-14")

    /** weapi POST：表单 params + encSecKey，网易云网页端统一协议。 */
    private fun weapiPost(path: String, text: String): LxHttpResponse {
        val (params, encSecKey) = neteaseWeapiEncrypt(text)
        val response = LxHttpClient.execute(
            url = "https://music.163.com$path",
            options = JSONObject()
                .put("method", "POST")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", neteaseWeapiHeaders())
                .put(
                    "body",
                    "params=" + encode(params) + "&encSecKey=" + encode(encSecKey),
                ),
        )
        return response
    }

    private fun neteaseWeapiHeaders(): JSONObject = JSONObject()
        .put("User-Agent", USER_AGENT)
        .put("Referer", "https://music.163.com/")
        .put("Content-Type", "application/x-www-form-urlencoded")
        .put("Cookie", "os=pc; appver=2.10.13")

    private fun neteaseWeapiError(root: JSONObject, fallback: String): String {
        val message = root.optString("message").ifBlank { root.optString("msg") }
        return message.ifBlank {
            val code = root.optInt("code", 0)
            if (code == 0) fallback else "$fallback（code $code）"
        }
    }

    /** MUSIC_U 优先取 body 的 cookie 数组，退回响应 Set-Cookie 头。 */
    private fun extractNeteaseMusicU(
        root: JSONObject,
        response: LxHttpResponse,
    ): String? {
        val pattern = Regex("MUSIC_U=([^;\\s]+)")
        val cookieArray = root.optJSONArray("cookie")
        if (cookieArray != null) {
            for (index in 0 until cookieArray.length()) {
                pattern.find(cookieArray.optString(index))?.let {
                    return it.groupValues[1]
                }
            }
        }
        val setCookie = response.headers.entries
            .firstOrNull { it.key.equals("set-cookie", ignoreCase = true) }
            ?.value
            ?: return null
        return pattern.find(setCookie)?.groupValues?.get(1)
    }

    /**
     * weapi 加密：明文先以固定密钥、再以随机密钥做两次 AES-128-CBC（base64），
     * 随机密钥反转后做无填充 RSA（hex，256 位），与网易云网页端一致。
     */
    private fun neteaseWeapiEncrypt(
        text: String,
        secret: String = randomBase62(NETEASE_SECRET_LEN),
    ): Pair<String, String> {
        val step1 = neteaseAesBase64(text.toByteArray(Charsets.UTF_8), NETEASE_PRESET_KEY)
        val params = neteaseAesBase64(step1.toByteArray(Charsets.UTF_8), secret)
        val encSecKey = BigInteger(1, secret.reversed().toByteArray(Charsets.UTF_8))
            .modPow(NETEASE_RSA_E, NETEASE_RSA_N)
            .toString(16)
            .padStart(NETEASE_RSA_HEX_LEN, '0')
        return params to encSecKey
    }

    private fun neteaseAesBase64(data: ByteArray, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(NETEASE_WEAPI_IV),
        )
        return android.util.Base64.encodeToString(
            cipher.doFinal(data),
            android.util.Base64.NO_WRAP,
        )
    }

    private fun randomBase62(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(length) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    // ---- 网易云 ----

    private fun fetchNeteasePlaylists(account: PlatformAccount): List<PlatformPlaylist> {
        val params = mapOf(
            "uid" to account.userId,
            "limit" to "300",
            "offset" to "0",
        )
        val response = LxHttpClient.execute(
            url = "https://music.163.com/api/user/playlist?" + encodeQuery(params),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", DEFAULT_TIMEOUT_MS)
                .put("headers", neteaseHeaders(account)),
        )
        ensureSuccess(response, "拉取网易云歌单")
        val root = JSONObject(response.body)
        if (root.optInt("code", 200) !in 200..299) {
            throw IllegalStateException("网易云返回 code ${root.optInt("code")}，登录可能已过期")
        }
        val array = root.optJSONArray("playlist") ?: JSONArray()
        val uid = account.userId
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optLong("id").takeIf { it > 0 }?.toString() ?: continue
                val creatorUserId = item.optJSONObject("creator")?.optString("userId").orEmpty()
                val collected = item.optBoolean("subscribed", false) ||
                    (creatorUserId.isNotBlank() && creatorUserId != uid)
                add(
                    PlatformPlaylist(
                        platform = BuiltinMusicSource.NETEASE,
                        id = id,
                        name = cleanText(item.optString("name")).ifBlank { "未命名歌单" },
                        coverUrl = item.optString("coverImgUrl").takeIf(String::isNotBlank),
                        trackCount = item.optInt("trackCount"),
                        collected = collected,
                    ),
                )
            }
        }
    }

    private fun fetchNeteaseTracks(
        account: PlatformAccount,
        playlist: PlatformPlaylist,
    ): List<LxMusicSong> {
        // 详情接口对匿名/高频请求不稳定（偶发空 tracks 或 406），做三级兜底：
        // v1 详情 → v6 详情 → trackIds + song/detail 批量补全。
        val results = mutableListOf<LxMusicSong>()
        val trackIds = mutableListOf<Long>()
        for (path in listOf("/api/v1/playlist/detail", "/api/v6/playlist/detail")) {
            // n 参数必须带上：不带 n 时该接口对部分新歌单会返回空 tracks
            val response = runCatching {
                LxHttpClient.execute(
                    url = "https://music.163.com$path?id=" +
                        encode(playlist.id) + "&n=1000",
                    options = JSONObject()
                        .put("method", "GET")
                        .put("timeout", DEFAULT_TIMEOUT_MS)
                        .put("headers", neteaseHeaders(account)),
                )
            }.getOrElse { continue }
            if (response.statusCode !in 200..299) continue
            val result = runCatching {
                JSONObject(response.body).optJSONObject("result")
            }.getOrNull() ?: continue
            val tracks = result.optJSONArray("tracks") ?: JSONArray()
            if (tracks.length() > 0) {
                for (index in 0 until tracks.length()) {
                    tracks.optJSONObject(index)?.let { item ->
                        parseNeteaseTrack(item)?.let(results::add)
                    }
                }
                return results
            }
            val ids = result.optJSONArray("trackIds") ?: JSONArray()
            for (index in 0 until ids.length()) {
                ids.optJSONObject(index)?.optLong("id")?.takeIf { it > 0 }?.let(trackIds::add)
            }
            if (trackIds.isNotEmpty()) break
        }
        if (results.isEmpty() && trackIds.isNotEmpty()) {
            results += fetchNeteaseSongsByIds(trackIds)
        }
        if (results.isEmpty()) {
            throw IllegalStateException("网易云歌单详情为空，请稍后重试")
        }
        return results
    }

    /** song/detail 批量补全（该公开接口实测稳定，无需鉴权）。 */
    private fun fetchNeteaseSongsByIds(ids: List<Long>): List<LxMusicSong> {
        val songs = mutableListOf<LxMusicSong>()
        ids.chunked(NETEASE_SONG_DETAIL_BATCH).forEach { chunk ->
            val joined = "[" + chunk.joinToString(",") + "]"
            val response = runCatching {
                LxHttpClient.execute(
                    url = "https://music.163.com/api/song/detail?ids=" +
                        encode(joined),
                    options = JSONObject()
                        .put("method", "GET")
                        .put("timeout", DEFAULT_TIMEOUT_MS)
                        .put("headers", defaultHeaders(BuiltinMusicSource.NETEASE)),
                )
            }.getOrElse { return@forEach }
            if (response.statusCode !in 200..299) return@forEach
            val array = runCatching {
                JSONObject(response.body).optJSONArray("songs")
            }.getOrNull() ?: return@forEach
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { item ->
                    parseNeteaseTrack(item)?.let(songs::add)
                }
            }
        }
        return songs
    }

    private fun parseNeteaseTrack(item: JSONObject): LxMusicSong? {
        val id = item.optLong("id").takeIf { it > 0 }?.toString() ?: return null
        val title = cleanText(item.optString("name")).ifBlank { "未知歌曲" }
        val artist = item.optJSONArray("artists")?.joinNames().orEmpty().ifBlank { "未知歌手" }
        val album = item.optJSONObject("album")?.optString("name").orEmpty()
        val durationMs = item.optLong("duration").coerceAtLeast(0L)
        val raw = JSONObject()
            .put("id", id)
            .put("songmid", id)
            .put("name", title)
            .put("singer", artist)
            .put("albumName", album)
            .put("duration", durationMs / 1000L)
        return LxMusicSong(
            id = id,
            sourceKey = BuiltinMusicSource.NETEASE.key,
            sourceName = BuiltinMusicSource.NETEASE.displayName,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = item.optJSONObject("album")?.optString("picUrl")?.takeIf(String::isNotBlank),
            rawJson = raw.toString(),
        )
    }

    // ---- 请求头 / 签名 / 通用工具 ----

    private fun defaultHeaders(source: BuiltinMusicSource): JSONObject = JSONObject()
        .put("User-Agent", USER_AGENT)
        .put(
            "Referer",
            when (source) {
                BuiltinMusicSource.KUGOU -> "https://www.kugou.com/"
                BuiltinMusicSource.KUWO -> "https://www.kuwo.cn/"
                BuiltinMusicSource.NETEASE -> "https://music.163.com/"
                BuiltinMusicSource.QQ -> "https://y.qq.com/"
            },
        )
        .put("Accept", "application/json,text/plain,*/*")

    private fun neteaseHeaders(account: PlatformAccount): JSONObject =
        defaultHeaders(BuiltinMusicSource.NETEASE)
            .put("Cookie", account.cookieHeader())

    private fun qqHeaders(account: PlatformAccount, referer: String): JSONObject =
        defaultHeaders(BuiltinMusicSource.QQ)
            .put("Referer", referer)
            .put("Cookie", account.cookieHeader())

    private fun kuwoHeaders(token: String): JSONObject = JSONObject()
        .put("User-Agent", USER_AGENT)
        .put("Referer", "https://www.kuwo.cn/")
        .put("csrf", token)
        .put("Accept", "application/json, text/plain, */*")
        .put("Cookie", "kw_token=$token; csrf=$token")

    /** 酷狗公共参数（dfid/mid/uuid/appid/clientver/clienttime）；appid 已存在时不覆盖（扫码接口用 1001）。 */
    private fun addKugouDefaults(params: MutableMap<String, String>, deviceGuid: String) {
        params["dfid"] = "-"
        params["mid"] = kugouMid(deviceGuid)
        params["uuid"] = "-"
        if (params["appid"].isNullOrBlank()) params["appid"] = "1005"
        params["clientver"] = "20489"
        params["clienttime"] = (System.currentTimeMillis() / 1000).toString()
    }

    private fun kugouHeaders(deviceGuid: String): JSONObject = JSONObject()
        .put("User-Agent", KUGOU_GATEWAY_UA)
        .put("dfid", "-")
        .put("mid", kugouMid(deviceGuid))
        .put("clienttime", (System.currentTimeMillis() / 1000).toString())

    private fun kugouGatewayHeaders(): JSONObject = JSONObject()
        .put("User-Agent", KUGOU_GATEWAY_UA)
        .put("x-router", "cloudlist.service.kugou.com")
        .put("dfid", "-")
        .put("mid", kugouMid(deviceGuid))
        .put("clienttime", (System.currentTimeMillis() / 1000).toString())
        .put("kg-rc", "1")
        .put("kg-thash", "5d816a0")
        .put("kg-rec", "1")
        .put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        .put("Content-Type", "application/json;charset=utf-8")

    /**
     * 酷狗签名：盐 + 按 key 排序的 "k=v" 串 + 请求体 + 盐，取 MD5。
     * android 盐用于网关接口，web 盐用于 login-user 接口。
     */
    private fun kugouSignature(salt: String, params: Map<String, String>, body: String): String =
        kugouSign(salt, params, body)

    /** 设备 mid：MD5(guid) 十六进制串按 16 进制大整数转十进制。 */
    private fun kugouMid(deviceGuid: String): String = kugouMidOf(deviceGuid)

    /** QQ g_tk：以登录 key 计算的 CSRF 校验值，无 key 时退回默认 5381。 */
    private fun qqGtk(account: PlatformAccount): String {
        val key = account.cookies["qm_keyst"]
            ?: account.cookies["qqmusic_key"]
            ?: account.cookies["skey"]
            ?: return "5381"
        var hash = 5381L
        for (ch in key) {
            hash += (hash shl 5) + ch.code
        }
        return (hash and 0x7FFFFFFFL).toString()
    }

    private fun kugouErrorText(errorCode: Int): String = when (errorCode) {
        20010 -> "酷狗登录已过期，请重新扫码登录"
        else -> "酷狗返回错误码 $errorCode"
    }

    private fun splitCombinedName(combined: String): Pair<String, String> {
        val index = combined.indexOf(" - ")
        if (index <= 0) return "" to combined.ifBlank { "未知歌曲" }
        return combined.substring(0, index).trim() to combined.substring(index + 3).trim()
    }

    private fun firstArray(root: JSONObject, vararg keys: String): JSONArray? =
        keys.firstNotNullOfOrNull { key ->
            root.optJSONArray(key)
        }

    private fun firstText(item: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            item.optString(key).takeIf(String::isNotBlank)
        }.orEmpty()

    private fun firstInt(item: JSONObject, vararg keys: String): Int =
        keys.firstNotNullOfOrNull { key ->
            val value = when {
                item.has(key) && item.get(key) is Number -> item.optInt(key)
                else -> item.optString(key).toIntOrNull()
            }
            value?.takeIf { it > 0 }
        } ?: 0

    private fun JSONArray.joinNames(): String = buildList {
        for (index in 0 until length()) {
            when (val value = opt(index)) {
                is JSONObject -> add(value.optString("name"))
                else -> add(value?.toString().orEmpty())
            }
        }
    }.filter(String::isNotBlank).joinToString(" / ")

    /** 酷我老接口偶发返回单引号 JSON，做个兜底。 */
    private fun parseLooseJson(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrElse {
            JSONObject(body.trim().replace('\'', '"'))
        }

    private fun ensureSuccess(response: LxHttpResponse, operation: String) {
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("$operation 失败：HTTP ${response.statusCode}")
        }
        if (response.body.isBlank()) {
            throw IllegalStateException("$operation 返回为空")
        }
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun encodeQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000
        const val KUGOU_PAGE_SIZE = 100
        const val KUGOU_APPID = "1005"
        const val KUGOU_CLIENTVER = "20489"
        const val KUGOU_LOGIN_UA = "Android16-1070-11440-130-0-LOGIN-wifi"
        const val KUGOU_RSA_KEY_BYTES = 128

        const val NETEASE_SECRET_LEN = 16
        const val NETEASE_APPVER = "9.1.11"
        val NETEASE_EAPI_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
        const val NETEASE_APP_UA =
            "NeteaseMusic $NETEASE_APPVER.202691027(209); Mozilla/5.0 " +
                "(Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/99.0.4844.73 Mobile Safari/537.36"
        const val NETEASE_RSA_HEX_LEN = 256
        const val NETEASE_PRESET_KEY = "0CoJUm6Qyw8W8jud"
        val NETEASE_WEAPI_IV = "0102030405060708".toByteArray(Charsets.UTF_8)
        val NETEASE_RSA_E = BigInteger("010001", 16)
        val NETEASE_RSA_N = BigInteger(
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
                "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e03" +
                "12ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10" +
                "b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db" +
                "0a22b8e7",
            16,
        )

        const val KUGOU_SALT_ANDROID = "OIlwieks28dk2k092lksi2UIkp"
        const val KUGOU_SALT_WEB = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        const val KUGOU_GATEWAY_UA =
            "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
        const val KUGOU_QRCODE_TXT =
            "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1005&"

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val NETEASE_SONG_DETAIL_BATCH = 50

        /** 酷狗登录协议 RSA 公钥（1024-bit，取自客户端内置 PEM）。 */
        val KUGOU_RSA_E = BigInteger("010001", 16)
        val KUGOU_RSA_N = BigInteger(
            "c8006ed03842d2628209bd314984ca5ed6cfe06e30c95f9d4704d9c49791d7a935" +
                "ba950ecb0bc8ebf5f5994f0bac927a7eb151b3c1de343303fa539c83136eccfd" +
                "7d7e511e2dbce18eaa9f784c9b50d443e75865979e0a5e216e46c684066a8d6b" +
                "998580bbaa22d73f5790286bb14742e83244e44db6d707ffe162c5c7002d45",
            16,
        )
    }
}

/** 酷狗签名：盐 + 按 key 排序的 "k=v" 串 + 请求体 + 盐，取 MD5（internal 以便单测对照）。 */
internal fun kugouSign(salt: String, params: Map<String, String>, body: String): String {
    val joined = params.keys.sorted().joinToString("") { key -> "$key=${params[key]}" }
    return md5HexTopLevel(salt + joined + body + salt)
}

/** 设备 mid：MD5(guid) 十六进制串按 16 进制大整数转十进制。 */
internal fun kugouMidOf(deviceGuid: String): String =
    BigInteger(
        1,
        MessageDigest.getInstance("MD5").digest(deviceGuid.toByteArray(Charsets.UTF_8)),
    ).toString(10)

internal fun md5HexTopLevel(value: String): String =
    MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

/** 网易云扫码服务端 code → 内部状态：800→0 过期，801→1 等待，802→2 已扫码，803→3 成功（internal 以便单测）。 */
internal fun neteaseQrStatusOf(code: Int): Int? = when (code) {
    800 -> 0
    801 -> 1
    802 -> 2
    803 -> 3
    else -> null
}

/** 网易云扫码二维码内容（internal 以便单测）。 */
internal fun neteaseQrContentOf(unikey: String): String =
    "https://music.163.com/login?codekey=$unikey"
