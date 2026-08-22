package com.wjy.foxchat.analysis

import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.model.ChatStats
import com.wjy.foxchat.model.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 纯本地统计：从消息列表计算互动频率、消息构成、打卡、活跃度。 */
object StatisticsCalculator {

    fun compute(messages: List<MessageEntity>, myRole: String): ChatStats {
        val valid = messages.filter { it.type != Message.TYPE_SYSTEM }
        if (valid.isEmpty()) return ChatStats.EMPTY

        val total = valid.size
        val mine = valid.count { it.senderRole == myRole }
        val partner = total - mine

        val text = valid.count { it.type == Message.TYPE_TEXT }
        val image = valid.count { it.type == Message.TYPE_IMAGE }
        val audio = valid.count { it.type == Message.TYPE_AUDIO }
        val checkin = valid.count { it.type == Message.TYPE_CHECKIN }
        val checkinReply = valid.count { it.type == Message.TYPE_CHECKIN_REPLY }

        val zone = ZoneId.systemDefault()
        val days = valid
            .map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .toSet()
            .sorted()
        val activeDays = days.size

        // 最长连续活跃天数（从最近活跃日往前数）
        var longestStreak = 0
        if (days.isNotEmpty()) {
            var expected: LocalDate = days.last()
            var count = 0
            for (d in days.asReversed()) {
                if (d == expected) {
                    count++
                    expected = expected.minusDays(1)
                } else {
                    break
                }
            }
            longestStreak = count
        }

        val mostActiveHour = valid
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).hour }
            .maxByOrNull { it.value.size }
            ?.key
            ?: -1

        return ChatStats(
            totalMessages = total,
            myMessages = mine,
            partnerMessages = partner,
            textCount = text,
            imageCount = image,
            audioCount = audio,
            checkinCount = checkin,
            checkinReplyCount = checkinReply,
            activeDays = activeDays,
            longestStreak = longestStreak,
            mostActiveHour = mostActiveHour
        )
    }
}
