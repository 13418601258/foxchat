package com.wjy.foxchat.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderRole: String,
    val type: String,
    val content: String,
    val mediaPath: String?,
    val mediaMimeType: String?,
    val mediaDurationMs: Long,
    val replyToMessageId: String?,
    val timestamp: Long,
    val recalledAt: Long?,
    val recalledText: String?,
    val deliveryStatus: String,
    val isMine: Boolean
) {
    val isRecalled: Boolean get() = recalledAt != null
    val isImage: Boolean get() = type == TYPE_IMAGE
    val isAudio: Boolean get() = type == TYPE_AUDIO
    val isCheckin: Boolean get() = type == TYPE_CHECKIN
    val isCheckinReply: Boolean get() = type == TYPE_CHECKIN_REPLY

    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_IMAGE = "IMAGE"
        const val TYPE_AUDIO = "AUDIO"
        const val TYPE_SYSTEM = "SYSTEM"
        const val TYPE_CHECKIN = "CHECKIN"
        const val TYPE_CHECKIN_REPLY = "CHECKIN_REPLY"
    }
}
