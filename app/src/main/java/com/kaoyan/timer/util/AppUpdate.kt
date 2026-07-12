package com.kaoyan.timer.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 设置页「检查更新」:拉 GitHub latest release,与本机 versionName 比较;
 * 有新版且含可信 APK 下载链时走系统 DownloadManager。
 */
object AppUpdate {

    private const val OWNER = "38yuanzhao"
    private const val REPO = "kaoyan-timer"
    private const val API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val TRUSTED_PREFIX =
        "https://github.com/$OWNER/$REPO/releases/download/"

    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result(val message: String) {
        data object Latest : Result("已是最新版")
        data object Failed : Result("检查失败")
        data object NoApk : Result("无 APK")
        data object Downloading : Result("开始下载")
    }

    /**
     * 语义化版本比较:剥前导 v/V,按 `.` 分段取整,缺段当 0。
     * 保证 v2.10 > v2.9(字符串比较会反)。
     * @return >0 表示 a 更新, 0 相等, <0 表示 a 更旧
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isTrustedApkUrl(url: String): Boolean =
        url.startsWith(TRUSTED_PREFIX) && url.endsWith(".apk", ignoreCase = true)

    fun parseVersion(raw: String): List<Int> {
        val s = raw.trim().removePrefix("v").removePrefix("V")
        if (s.isEmpty()) return listOf(0)
        return s.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    /** 后台线程调用:查 latest、比版本、必要时入队下载。 */
    fun check(context: Context): Result {
        return try {
            val release = fetchLatest() ?: return Result.Failed
            val installed = installedVersion(context) ?: return Result.Failed
            if (compareVersions(release.tag, installed) <= 0) return Result.Latest
            val apkUrl = release.apkUrl ?: return Result.NoApk
            if (!isTrustedApkUrl(apkUrl)) return Result.NoApk
            enqueueDownload(context, apkUrl, release.tag)
            Result.Downloading
        } catch (_: Exception) {
            Result.Failed
        }
    }

    private data class Release(val tag: String, val apkUrl: String?)

    private fun fetchLatest(): Release? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "kaoyan-timer")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
            val assets = root["assets"]?.jsonArray.orEmpty()
            val apkUrl = assets.firstNotNullOfOrNull { el ->
                val o = el.jsonObject
                val name = o["name"]?.jsonPrimitive?.content.orEmpty()
                val url = o["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
                if (name.endsWith(".apk", ignoreCase = true) && isTrustedApkUrl(url)) url else null
            }
            return Release(tag, apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    private fun installedVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
        null
    }

    private fun enqueueDownload(context: Context, url: String, tag: String) {
        val safeTag = tag.removePrefix("v").removePrefix("V")
            .filter { it.isLetterOrDigit() || it in ".-_" }
            .ifBlank { "update" }
        val fileName = "kaoyan-timer-$safeTag.apk"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("考研 $tag")
            .setDescription("正在下载更新…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(req)
    }
}
