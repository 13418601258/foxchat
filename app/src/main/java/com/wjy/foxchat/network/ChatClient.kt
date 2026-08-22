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

    suspend fun generateWeeklyReport(messages: List<MessageEntity>): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("尚未配置周报 AI Key"))
        }
        val transcript = messages
            .filter { it.type == "TEXT" && it.text?.isNotBlank() == true }
            .joinToString("\n") { "${it.senderRole}: ${it.text}" }
        if (transcript.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("本周没有可分析的文本消息"))
        }

        val requestMessages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    """
                    你负责整理一份双方共同可见的聊天周报。
                    只根据聊天内容总结本周聊天摘要、高频话题、情绪变化、互动频率、
                    重要事件和共同计划。不得进行心理诊断，不给任何一方贴人格标签，
                    不猜测未明确表达的私人事实。使用简洁中文分段输出。
                    """.trimIndent()
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", transcript)
            })
        }
        requestChatCompletion(requestMessages)
    }

    private suspend fun requestChatCompletion(messages: JSONArray): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", messages)
                put("temperature", 0.2)
                put("max_tokens", 1200)
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
