package com.wjy.foxchat.network

import com.wjy.foxchat.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI is intentionally limited to shared weekly reports. It is never used by normal messaging.
 */
class ChatClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateWeeklyReport(
        messages: List<MessageEntity>,
        stats: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("尚未配置周报 AI Key"))
        }
        val transcript = messages
            .filter { it.type == "TEXT" && it.text?.isNotBlank() == true }
            .joinToString("\n") { "${it.senderRole}: ${it.text}" }
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("没有可分析的文本消息"))
        }

        val requestMessages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    """
                    你是一位趣味聊天数据分析师。下面是【本地统计】的准确数据和【本周聊天记录】。
                    请基于真实聊天内容做轻松有趣的数据盘点，语言轻松接地气，多用数据说话，
                    挖掘有意思的细节，不做严肃的心理评判，不给任何一方贴标签。

                    请严格按以下结构输出：
                    一、基础数据大盘
                    二、语言指纹大揭秘
                    三、互动行为画像
                    四、话题内容地图
                    五、趣味冷知识盘点（至少 3 个细节）
                    六、一句话总结

                    重要：数据类指标（消息数、占比、高频字词、回复速度等）请直接采用【本地统计】
                    中的准确数字，不要自己重新数；趣味类分析（口头禅、话题、吐槽、默契时刻、冷知识）
                    基于聊天记录发挥。
                    """.trimIndent()
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put(
                    "content",
                    "【本地统计（准确数据，请直接采用）】\n$stats\n\n【本周聊天记录】\n$transcript"
                )
            })
        }
        requestChatCompletion(requestMessages)
    }

    private suspend fun requestChatCompletion(messages: JSONArray): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", messages)
                put("temperature", 0.6)
                put("max_tokens", 1800)
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                val raw = response.body?.string().orEmpty()
                                if (!response.isSuccessful) {
                                    continuation.resume(Result.failure(
                                        IllegalStateException("AI HTTP ${response.code}: $raw")
                                    ))
                                    return
                                }
                                val content = JSONObject(raw)
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                    .trim()
                                continuation.resume(Result.success(content))
                            }
                        } catch (error: Exception) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }
                })
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
