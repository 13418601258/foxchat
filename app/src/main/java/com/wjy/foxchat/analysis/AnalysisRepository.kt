package com.wjy.foxchat.analysis

import android.content.Context
import com.wjy.foxchat.data.DeviceIdentityStore
import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.data.local.WeeklyReportEntity
import com.wjy.foxchat.network.ChatClient
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class AnalysisRepository(context: Context) {
    private val identity = DeviceIdentityStore(context)
    private val client by lazy { ChatClient(identity.aiBaseUrl(), identity.aiApiKey()) }

    suspend fun generateWeeklyReport(
        conversationId: String,
        messages: List<MessageEntity>
    ): Result<WeeklyReportEntity> {
        return client.generateWeeklyReport(messages).map { content ->
            val week = currentWeekKey()
            WeeklyReportEntity(
                id = "$conversationId:$week",
                conversationId = conversationId,
                weekKey = week,
                summary = content,
                topics = "",
                moodTrend = "",
                interactionChange = "",
                importantEvents = ""
            )
        }
    }

    companion object {
        fun currentWeekKey(): String {
            val fields = WeekFields.of(Locale.getDefault())
            val now = LocalDate.now()
            return "${now.get(fields.weekBasedYear())}-W${now.get(fields.weekOfWeekBasedYear())}"
        }
    }
}
