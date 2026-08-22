package com.wjy.foxchat.data

import android.content.Context
import com.wjy.foxchat.data.local.ConversationEntity
import com.wjy.foxchat.data.local.FoxChatDatabase
import com.wjy.foxchat.data.local.MessageEntity
import org.json.JSONArray
import java.util.UUID

object LegacyDataMigrator {
    const val LEGACY_CONVERSATION_ID = "legacy-ai-history"
    private const val PREFERENCES_NAME = "foxchat"
    private const val KEY_MIGRATED = "room_migration_v1_complete"
    private const val KEY_HISTORY = "chat_history"
    private const val KEY_STYLE = "style_profile"
    private const val KEY_PERSONAL = "personal_profile"

    suspend fun migrate(context: Context, database: FoxChatDatabase) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val messages = mutableListOf<MessageEntity>()
        val rawHistory = prefs.getString(KEY_HISTORY, null)
        if (!rawHistory.isNullOrBlank()) {
            runCatching {
                val history = JSONArray(rawHistory)
                for (index in 0 until history.length()) {
                    val entry = history.getJSONObject(index)
                    val role = entry.optString("role", "AI")
                    val isUser = role.equals("USER", ignoreCase = true)
                    messages += MessageEntity(
                        id = "legacy-${index}-${UUID.randomUUID()}",
                        conversationId = LEGACY_CONVERSATION_ID,
                        senderId = if (isUser) "legacy-user" else "legacy-ai",
                        senderRole = if (isUser) "LEGACY_USER" else "LEGACY_AI",
                        type = "TEXT",
                        text = entry.optString("content"),
                        createdAt = entry.optLong("timestamp", System.currentTimeMillis()),
                        deliveryStatus = "LOCAL",
                        syncStatus = "LOCAL_ONLY"
                    )
                }
            }
        }

        val styleProfile = prefs.getString(KEY_STYLE, "").orEmpty()
        val personalProfile = prefs.getString(KEY_PERSONAL, "").orEmpty()
        if (messages.isNotEmpty() || styleProfile.isNotBlank() || personalProfile.isNotBlank()) {
            database.conversationDao().upsert(
                ConversationEntity(
                    id = LEGACY_CONVERSATION_ID,
                    title = "旧版 AI 记录",
                    updatedAt = System.currentTimeMillis()
                )
            )
            database.messageDao().upsertAll(messages)
        }

        // Keep the original SharedPreferences values intact so an upgrade can be audited or retried.
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}
