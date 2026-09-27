package com.wjy.foxchat.analysis

import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.model.Message
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 周报本地统计：把聊天数据算成准确数字（B 方案），
 * 生成一段文本交给 AI，让 AI 基于真实数据做趣味盘点。
 */
object WeeklyStatsCalculator {

    fun compute(messages: List<MessageEntity>): String {
        val text = messages.filter {
            it.type == Message.TYPE_TEXT && it.text?.isNotBlank() == true
        }
        if (text.isEmpty()) return "本周暂无文字聊天记录"

        val sb = StringBuilder()
        val total = text.size
        val byRole = text.groupBy { it.senderRole ?: "未知" }
        val zone = ZoneId.systemDefault()

        // 基础
        sb.append("总消息数：$total 条\n")
        byRole.forEach { (role, list) ->
            sb.append("角色$role 发 ${list.size} 条（占比 ${list.size * 100 / total}%）\n")
        }
        sb.append("总字数：${text.sumOf { it.text?.length ?: 0 }} 字\n")
        byRole.maxByOrNull { it.value.size }?.let {
            sb.append("话痨王：角色${it.key}（${it.value.size * 100 / total}%）\n")
        }

        // 时间分布
        val hourGroups = text.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).hour }
        hourGroups.maxByOrNull { it.value.size }?.let {
            sb.append("最活跃时段：${it.key}:00-${it.key + 1}:00（${it.value.size} 条）\n")
        }

        // 最早/最晚
        val sorted = text.sortedBy { it.createdAt }
        sb.append("最早消息：${fmtTime(sorted.first().createdAt)} 「${sorted.first().text?.take(20)}」\n")
        sb.append("最晚消息：${fmtTime(sorted.last().createdAt)} 「${sorted.last().text?.take(20)}」\n")

        // 高频单字
        val stop = "的了在是我你有他这那和就都也一不吧啊呢吗嗯个上"
        val charFreq = mutableMapOf<Char, Int>()
        text.forEach { msg ->
            msg.text?.forEach { c ->
                if (c.isLetter() && c.code > 0x4E00 && c !in stop) {
                    charFreq[c] = (charFreq[c] ?: 0) + 1
                }
            }
        }
        charFreq.entries.sortedByDescending { it.value }.take(5).let { top ->
            if (top.isNotEmpty()) {
                sb.append("高频单字TOP5：${top.joinToString { "${it.key}(${it.value}次)" }}\n")
            }
        }

        // 高频词（预设常用口头禅/语气词）
        val commonWords = listOf(
            "哈哈哈哈", "哈哈哈", "哈哈", "嗯嗯", "好的", "可以", "真的", "什么",
            "不是", "知道", "今天", "明天", "但是", "所以", "已经", "不行"
        )
        val wordFreq = commonWords.mapNotNull { w ->
            val count = text.sumOf {
                it.text?.let { t -> Regex(w).findAll(t).count() } ?: 0
            }
            if (count > 0) w to count else null
        }.sortedByDescending { it.second }.take(10)
        if (wordFreq.isNotEmpty()) {
            sb.append("高频词TOP${wordFreq.size}：${wordFreq.joinToString { "${it.first}(${it.second}次)" }}\n")
        }

        // 表情符号偏好
        val emojis = listOf("😂", "😭", "🥰", "😘", "👍", "💪", "❤️", "🤣", "😅", "😳", "🙄")
        val emojiFreq = emojis.mapNotNull { e ->
            val count = text.sumOf {
                it.text?.let { t -> Regex(Regex.escape(e)).findAll(t).count() } ?: 0
            }
            if (count > 0) e to count else null
        }.sortedByDescending { it.second }.take(5)
        if (emojiFreq.isNotEmpty()) {
            sb.append("表情符号TOP：${emojiFreq.joinToString { "${it.first}(${it.second}次)" }}\n")
        }

        // 平均回复速度（相邻不同角色消息的时间差）
        val replies = mutableListOf<Pair<String, Long>>()
        sorted.zipWithNext().forEach { (a, b) ->
            if (a.senderRole != b.senderRole) {
                replies.add(b.senderRole to (b.createdAt - a.createdAt).coerceAtLeast(0L))
            }
        }
        replies.groupBy { it.first }.forEach { (role, list) ->
            val avgSec = list.map { it.second }.average().toLong() / 1000
            sb.append("角色$role 平均回复速度：${fmtDuration(avgSec)}（${list.size} 次）\n")
        }

        // 话题发起者：按发言段（连续同角色算一段）
        var lastRole: String? = null
        val initiator = mutableMapOf<String, Int>()
        sorted.forEach { m ->
            val role = m.senderRole ?: "未知"
            if (role != lastRole) initiator[role] = (initiator[role] ?: 0) + 1
            lastRole = role
        }
        initiator.entries.sortedByDescending { it.value }.forEach { (role, count) ->
            sb.append("角色$role 发起话题 $count 次\n")
        }

        return sb.toString()
    }

    private fun fmtTime(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    private fun fmtDuration(seconds: Long): String = when {
        seconds < 60 -> "$seconds 秒"
        seconds < 3600 -> "${seconds / 60} 分 ${seconds % 60} 秒"
        else -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    }
}
