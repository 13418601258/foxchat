package com.wjy.foxchat.notification

import org.json.JSONObject
import java.util.UUID

/** 一条定时通知。 */
data class ScheduledNotification(
    val id: String,
    val triggerAt: Long,
    val content: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("triggerAt", triggerAt)
        put("content", content)
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduledNotification = ScheduledNotification(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            triggerAt = obj.optLong("triggerAt"),
            content = obj.optString("content")
        )
    }
}
