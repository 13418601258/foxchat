package com.wjy.foxchat.ui

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.R
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityWeeklyReportsBinding
import com.wjy.foxchat.model.ChatStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeeklyReportsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWeeklyReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        findViewById<android.widget.TextView>(com.wjy.foxchat.R.id.tvTitle).text = "每周分析"
        findViewById<android.widget.ImageButton>(com.wjy.foxchat.R.id.btnBack).setOnClickListener { finish() }
        lifecycleScope.launch {
            val repo = ChatRepository.get(this@WeeklyReportsActivity)
            val stats = repo.computeStats(7)
            val reports = repo.observeReports().first()
            binding.container.removeAllViews()

            addText(buildStatsText(stats), true)
            addText("每周 AI 分析", false)
            if (reports.isEmpty()) {
                addText("暂时还没有周报。双方都同意并产生新的聊天记录后会自动生成。", false)
            } else {
                reports.forEach { report ->
                    addText("${report.weekKey}\n\n${report.summary}", true)
                }
            }
        }
    }

    private fun buildStatsText(stats: ChatStats): String {
        val sb = StringBuilder("最近 7 天数据\n")
        if (stats.totalMessages == 0) {
            sb.append("暂无聊天记录")
            return sb.toString()
        }
        sb.append("消息总数：${stats.totalMessages} 条（你 ${stats.myMessages} · 对方 ${stats.partnerMessages}）\n")
        sb.append("文字 ${stats.textCount} · 图片 ${stats.imageCount} · 语音 ${stats.audioCount}\n")
        if (stats.checkinCount > 0 || stats.checkinReplyCount > 0) {
            sb.append("打卡：发起 ${stats.checkinCount} 次 · 完成 ${stats.checkinReplyCount} 次\n")
        }
        sb.append("活跃 ${stats.activeDays} 天 · 最长连续 ${stats.longestStreak} 天")
        if (stats.mostActiveHour >= 0) {
            val end = (stats.mostActiveHour + 1) % 24
            sb.append("\n最活跃时段：${stats.mostActiveHour}:00 - $end:00")
        }
        return sb.toString()
    }

    private fun addText(value: String, framed: Boolean) {
        val view = TextView(this).apply {
            setTextColor(Color.parseColor("#253033"))
            textSize = 14f
            setPadding(16, 16, 16, 16)
            text = value
            if (framed) setBackgroundColor(Color.WHITE)
        }
        binding.container.addView(view, ViewGroup.LayoutParams(-1, -2))
        if (framed) {
            val divider = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#E2E8E5"))
                minimumHeight = 1
            }
            binding.container.addView(divider, ViewGroup.LayoutParams(-1, 1))
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, WeeklyReportsActivity::class.java)
    }
}
