package com.wjy.foxchat.data.remote

import android.content.Context
import com.wjy.foxchat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 一次可用的更新信息。 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

/**
 * 应用自更新：从 Supabase 公开桶读取版本信息，下载 APK。
 *
 * 版本信息文件约定为 public bucket「releases」下的 latest.json：
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1",
 *   "downloadUrl": "releases/foxchat-1.1.apk",
 *   "changelog": "新增打卡功能；修复通知问题"
 * }
 * downloadUrl 支持完整 URL（http/https 开头），也支持桶内相对路径。
 */
object UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun latestJsonUrl(): String =
        "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/releases/latest.json"

    private fun resolveDownloadUrl(downloadUrl: String): String =
        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            downloadUrl
        } else {
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/releases/$downloadUrl"
        }

    /** 检查是否有新版本；无新版本或未配置 Supabase 时返回 null。 */
    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            if (BuildConfig.SUPABASE_URL.isBlank()) return@runCatching null
            val request = Request.Builder().url(latestJsonUrl()).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                val remoteVersion = json.optInt("versionCode", 0)
                if (remoteVersion <= BuildConfig.VERSION_CODE) return@runCatching null
                UpdateInfo(
                    versionCode = remoteVersion,
                    versionName = json.optString("versionName", "新版本"),
                    downloadUrl = json.optString("downloadUrl"),
                    changelog = json.optString("changelog", "暂无更新说明")
                )
            }
        }
    }

    /** 下载 APK 到应用私有目录，onProgress 回调百分比（0-100，运行在 IO 线程）。 */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val target = File(dir, "foxchat-${info.versionName}.apk")
            if (target.exists()) target.delete()

            val request = Request.Builder().url(resolveDownloadUrl(info.downloadUrl)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载失败（HTTP ${response.code}）")
                val body = response.body ?: error("下载失败：空响应")
                val total = body.contentLength()
                var loaded = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            loaded += n
                            if (total > 0) {
                                onProgress(((loaded * 100) / total).coerceIn(0, 100).toInt())
                            }
                        }
                    }
                }
            }
            target
        }
    }
}
