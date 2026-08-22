package com.wjy.foxchat.model

/** 本地聊天统计数据（不依赖 AI，直接从数据库消息计算）。 */
data class ChatStats(
    val totalMessages: Int,
    val myMessages: Int,
    val partnerMessages: Int,
    val textCount: Int,
    val imageCount: Int,
    val audioCount: Int,
    val checkinCount: Int,
    val checkinReplyCount: Int,
    val activeDays: Int,
    val longestStreak: Int,
    val mostActiveHour: Int
) {
    companion object {
        val EMPTY = ChatStats(
            totalMessages = 0,
            myMessages = 0,
            partnerMessages = 0,
            textCount = 0,
            imageCount = 0,
            audioCount = 0,
            checkinCount = 0,
            checkinReplyCount = 0,
            activeDays = 0,
            longestStreak = 0,
            mostActiveHour = -1
        )
    }
}
