package com.qymusic.player.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新清单解析：OSS 上发布的 latest.json 必须能按这套字段解析出来，
 * 否则应用启动时永远“检测不到新版”。
 */
class AppUpdaterTest {

    /** 与 apk/latest.json 保持同一结构（发布流程里手工维护的就是它）。 */
    private val ossLatestJson = """
        {
          "versionName": "0.1.7",
          "versionCode": 7,
          "notes": "1. 平台歌单整单进队列\n2. 更新检查改用阿里云 OSS",
          "apkUrl": "https://bql-qymusic1.oss-cn-hongkong.aliyuncs.com/updates/QYMusic-0.1.7-202611101900.apk"
        }
    """.trimIndent()

    @Test
    fun parsesOssLatestJson() {
        val info = AppUpdater.parseSimpleJson(JSONObject(ossLatestJson))
        assertEquals("0.1.7", info?.versionName)
        assertEquals(7L, info?.versionCode)
        assertTrue(info?.notes?.contains("阿里云 OSS") == true)
        assertTrue(info?.apkUrl?.endsWith(".apk") == true)
    }

    @Test
    fun simpleJsonWithoutApkUrlIsNotAnUpdate() {
        assertNull(AppUpdater.parseSimpleJson(JSONObject("""{"versionName":"0.1.8"}""")))
    }

    /** latest.json 可以直接把 apkUrl 指向网盘：解析出提取码，并标记为网盘渠道。 */
    @Test
    fun parsesNetdiskManifestWithPassword() {
        val info = AppUpdater.parseSimpleJson(
            JSONObject(
                """
                {
                  "versionName": "1.2.0",
                  "versionCode": 21,
                  "notes": "网盘分发",
                  "apkUrl": "https://motianlun.lanzouw.com/iWNYW4fhq9tg",
                  "password": "6scv"
                }
                """.trimIndent(),
            ),
        )
        assertEquals("6scv", info?.sharePassword)
        assertTrue(info?.isNetdisk == true)
    }

    /** 手写清单时用 `shareCode` 也认，避免只认一个字段名。 */
    @Test
    fun acceptsShareCodeAsPasswordAlias() {
        val info = AppUpdater.parseSimpleJson(
            JSONObject(
                """
                {
                  "versionName": "1.2.0",
                  "apkUrl": "https://pan.baidu.com/s/abcdef",
                  "shareCode": "abc1"
                }
                """.trimIndent(),
            ),
        )
        assertEquals("abc1", info?.sharePassword)
        assertTrue(info?.isNetdisk == true)
    }

    /** 直链（OSS/GitHub）不能被误判成网盘，否则会白白跳浏览器。 */
    @Test
    fun detectsNetdiskHostsOnly() {
        assertTrue(AppUpdater.isNetdiskUrl("https://motianlun.lanzouw.com/iWNYW4fhq9tg"))
        assertTrue(AppUpdater.isNetdiskUrl("https://www.123pan.com/s/abcd"))
        assertTrue(AppUpdater.isNetdiskUrl("https://pan.baidu.com/s/1xyz"))
        assertFalse(
            AppUpdater.isNetdiskUrl(
                "https://bql-qymusic1.oss-cn-hongkong.aliyuncs.com/updates/QYMusic-1.2.0-202611111000.apk",
            ),
        )
        assertFalse(AppUpdater.isNetdiskUrl("https://github.com/o/r/releases/download/v1/app.apk"))
        assertFalse(AppUpdater.isNetdiskUrl("not a url"))
    }

    @Test
    fun parsesGitHubReleaseShape() {
        val info = AppUpdater.parseGitHubRelease(
            JSONObject(
                """
                {
                  "tag_name": "v0.1.7",
                  "body": "notes",
                  "assets": [
                    {"name": "app-release.apk", "browser_download_url": "https://example.com/a.apk"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        assertEquals("0.1.7", info?.versionName)
        assertEquals("https://example.com/a.apk", info?.apkUrl)
    }

    /**
     * 更新源固定指向香港地域的 bql-qymusic1 桶。
     * 2026-11 踩过：桶在别的 region 时用香港 endpoint 访问会返回 403，
     * 表现是“检查更新一直失败”，所以这里把 endpoint + bucket 钉死。
     */
    @Test
    fun updateSourceUsesHongKongBucket() {
        assertTrue(
            AppUpdater.UPDATES_URL.startsWith("https://bql-qymusic1.oss-cn-hongkong.aliyuncs.com/"),
        )
    }

    /** 版本比较只看 versionName 的三段数字：0.1.14 比 0.1.6 新。 */
    @Test
    fun versionCodeOfComparesThreeSegments() {
        assertEquals(107L, AppUpdater.versionCodeOf("0.1.7"))
        assertEquals(114L, AppUpdater.versionCodeOf("v0.1.14"))
        assertTrue(AppUpdater.versionCodeOf("0.2.0") > AppUpdater.versionCodeOf("0.1.99"))
        assertTrue(AppUpdater.versionCodeOf("1.0.0") > AppUpdater.versionCodeOf("0.9.9"))
    }
}
