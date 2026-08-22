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
            val reports = ChatRepository.get(this@WeeklyReportsActivity).observeReports().first()
            binding.container.removeAllViews()
            if (reports.isEmpty()) {
                addText("暂时还没有周报。双方都同意并产生新的聊天记录后会自动生成。", false)
            } else {
                reports.forEach { report ->
                    addText("${report.weekKey}\n\n${report.summary}", true)
                }
            }
        }
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
