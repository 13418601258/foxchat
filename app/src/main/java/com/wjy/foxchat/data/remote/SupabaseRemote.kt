package com.wjy.foxchat.data.remote

import com.wjy.foxchat.BuildConfig
import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.data.local.ParticipantEntity
import com.wjy.foxchat.data.local.WeeklyReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SupabaseRemote {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private var accessToken: String? = null

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun setAccessToken(token: String?) {
        accessToken = token?.takeIf { it.isNotBlank() }
    }

    suspend fun signInAnonymously(deviceId: String): Result<String> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONObject().put("data", JSONObject().put("device_id", deviceId))
        val raw = execute(
            Request.Builder()
                .url("${baseUrl()}/auth/v1/signup")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
        JSONObject(raw).optString("access_token").also {
            require(it.isNotBlank()) { "匿名会话创建失败" }
            accessToken = it
        }
    }

    suspend fun pairDevice(roomKey: String, role: String, deviceId: String): Result<String> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONObject().apply {
            put("room_key", roomKey)
            put("participant_role", role)
            put("device_id", deviceId)
        }
        val response = execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/pair_device")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
        val roomId = response.trim().let { body ->
            when {
                body.startsWith("[") -> JSONArray(body).optJSONObject(0)?.optString("room_id").orEmpty()
                body.startsWith("{") -> JSONObject(body).optString("room_id")
                else -> ""
            }
        }
        roomId.ifBlank { throw IllegalStateException("Pairing service did not return a room ID") }
    }

    suspend fun upsertMessage(message: MessageEntity): Result<Unit> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONArray().put(message.toJson())
        execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/messages?on_conflict=id")
                .headers(defaultHeaders("resolution=merge-duplicates,return=minimal"))
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
    }

    suspend fun uploadAndSetAvatar(conversationId: String, role: String, localPath: String): Result<Unit> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val file = File(localPath)
        require(file.exists()) { "头像文件不存在" }
        val mimeType = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val remotePath = "avatars/$conversationId/$role.jpg"
        execute(
            Request.Builder()
                .url("${baseUrl()}/storage/v1/object/chat-media/$remotePath")
                .headers(defaultHeaders(includeJsonContentType = false))
                .post(file.asRequestBody(mimeType.toMediaType()))
                .build()
        )
        val payload = JSONObject().apply {
            put("room_id", conversationId)
            put("avatar", remotePath)
        }
        execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/set_avatar")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
    }

    suspend fun uploadBackground(conversationId: String, localPath: String): Result<String> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val file = File(localPath)
        require(file.exists()) { "背景文件不存在" }
        val mimeType = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val remotePath = "backgrounds/$conversationId.jpg"
        execute(
            Request.Builder()
                .url("${baseUrl()}/storage/v1/object/chat-media/$remotePath")
                .headers(defaultHeaders(includeJsonContentType = false))
                .post(file.asRequestBody(mimeType.toMediaType()))
                .build()
        )
        remotePath
    }

    suspend fun setRoomBackground(conversationId: String, backgroundUri: String?): Result<Unit> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONObject().apply {
            put("room_id", conversationId)
            put("background_uri", backgroundUri)
        }
        execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/set_room_background")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
    }

    suspend fun fetchRoomBackground(conversationId: String): Result<String?> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val raw = execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/rooms?id=eq.$conversationId&select=background_uri")
                .headers(defaultHeaders())
                .get()
                .build()
        )
        val array = JSONArray(raw)
        if (array.length() == 0) null
        else array.getJSONObject(0).optString("background_uri").takeIf { it.isNotBlank() }
    }

    suspend fun updateAnalysisConsent(conversationId: String, consent: Boolean): Result<Unit> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONObject().apply {
            put("room_id", conversationId)
            put("consent", consent)
        }
        execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/set_analysis_consent")
                .headers(defaultHeaders())
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
    }

    suspend fun fetchParticipants(conversationId: String): Result<List<ParticipantEntity>> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val raw = execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/room_members?room_id=eq.$conversationId")
                .headers(defaultHeaders())
                .get()
                .build()
        )
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val role = item.getString("participant_role")
                add(
                    ParticipantEntity(
                        id = "$conversationId:$role",
                        conversationId = conversationId,
                        role = role,
                        deviceId = item.optString("device_id"),
                        analysisConsent = item.optBoolean("analysis_consent", false),
                        avatar = item.optString("avatar").takeIf { it.isNotBlank() },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun upsertWeeklyReport(report: WeeklyReportEntity): Result<Unit> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val payload = JSONArray().put(JSONObject().apply {
            put("id", report.id)
            put("conversation_id", report.conversationId)
            put("week_key", report.weekKey)
            put("summary", report.summary)
            put("topics", report.topics)
            put("mood_trend", report.moodTrend)
            put("interaction_change", report.interactionChange)
            put("important_events", report.importantEvents)
        })
        execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/weekly_reports?on_conflict=id")
                .headers(defaultHeaders("resolution=merge-duplicates,return=minimal"))
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        )
    }

    suspend fun fetchWeeklyReports(conversationId: String): Result<List<WeeklyReportEntity>> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val raw = execute(
            Request.Builder()
                .url("${baseUrl()}/rest/v1/weekly_reports?conversation_id=eq.$conversationId&order=created_at.desc")
                .headers(defaultHeaders())
                .get()
                .build()
        )
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WeeklyReportEntity(
                        id = item.getString("id"),
                        conversationId = item.getString("conversation_id"),
                        weekKey = item.getString("week_key"),
                        summary = item.getString("summary"),
                        topics = item.optString("topics"),
                        moodTrend = item.optString("mood_trend"),
                        interactionChange = item.optString("interaction_change"),
                        importantEvents = item.optString("important_events"),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun fetchMessages(conversationId: String, after: Long): Result<List<MessageEntity>> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val url = "${baseUrl()}/rest/v1/messages?conversation_id=eq.$conversationId" +
            "&created_at_ms=gt.$after&order=created_at_ms.asc"
        val raw = execute(
            Request.Builder()
                .url(url)
                .headers(defaultHeaders())
                .get()
                .build()
        )
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toMessageEntity())
            }
        }
    }

    suspend fun uploadMedia(
        conversationId: String,
        messageId: String,
        file: File,
        mimeType: String
    ): Result<String> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val path = "$conversationId/$messageId-${file.name}"
        execute(
            Request.Builder()
                .url("${baseUrl()}/storage/v1/object/chat-media/$path")
                .headers(defaultHeaders(includeJsonContentType = false))
                .post(file.asRequestBody(mimeType.toMediaType()))
                .build()
        )
        path
    }

    suspend fun downloadMedia(path: String, target: File): Result<File> = runCatching {
        require(isConfigured) { "Supabase 未配置" }
        val request = Request.Builder()
            .url("${baseUrl()}/storage/v1/object/chat-media/$path")
            .headers(defaultHeaders(includeJsonContentType = false))
            .get()
            .build()
        val bytes = executeBytes(request)
        target.parentFile?.mkdirs()
        target.outputStream().use { it.write(bytes) }
        target
    }

    private fun MessageEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("conversation_id", conversationId)
        put("sender_id", senderId)
        put("sender_role", senderRole)
        put("type", type)
        put("text", text)
        put("media_path", mediaPath)
        put("media_mime_type", mediaMimeType)
        put("media_duration_ms", mediaDurationMs)
        put("reply_to_message_id", replyToMessageId)
        put("created_at_ms", createdAt)
        put("edited_at_ms", editedAt)
        put("recalled_at_ms", recalledAt)
        put("delivery_status", deliveryStatus)
        put("read_at_ms", readAt)
    }

    private fun JSONObject.toMessageEntity() = MessageEntity(
        id = getString("id"),
        conversationId = getString("conversation_id"),
        senderId = getString("sender_id"),
        senderRole = optString("sender_role", "UNKNOWN"),
        type = optString("type", "TEXT"),
        text = optString("text").takeIf { it.isNotBlank() },
        mediaPath = optString("media_path").takeIf { it.isNotBlank() },
        mediaMimeType = optString("media_mime_type").takeIf { it.isNotBlank() },
        mediaDurationMs = optLong("media_duration_ms", 0L),
        replyToMessageId = optString("reply_to_message_id").takeIf { it.isNotBlank() },
        createdAt = optLong("created_at_ms", System.currentTimeMillis()),
        editedAt = optLong("edited_at_ms").takeIf { it > 0L },
        recalledAt = optLong("recalled_at_ms").takeIf { it > 0L },
        deliveryStatus = optString("delivery_status", "DELIVERED"),
        readAt = optLong("read_at_ms").takeIf { it > 0L },
        syncStatus = "SYNCED"
    )

    private fun baseUrl() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private fun defaultHeaders(
        prefer: String? = null,
        includeJsonContentType: Boolean = true
    ): okhttp3.Headers =
        okhttp3.Headers.Builder()
            .add("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .add("Authorization", "Bearer ${accessToken ?: BuildConfig.SUPABASE_ANON_KEY}")
            .apply { if (includeJsonContentType) add("Content-Type", "application/json") }
            .apply { if (prefer != null) add("Prefer", prefer) }
            .build()

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (it.isSuccessful) {
                            continuation.resume(body)
                        } else if (!continuation.isCancelled) {
                            continuation.resumeWithException(
                                IllegalStateException("Supabase HTTP ${it.code}: $body")
                            )
                        }
                    }
                }
            })
        }
    }

    private suspend fun executeBytes(request: Request): ByteArray = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) {
                            continuation.resume(it.body?.bytes() ?: ByteArray(0))
                        } else if (!continuation.isCancelled) {
                            continuation.resumeWithException(
                                IllegalStateException("Supabase HTTP ${it.code}")
                            )
                        }
                    }
                }
            })
        }
    }
}
