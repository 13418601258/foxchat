package com.wjy.foxchat.ui

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.R
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityChatSearchBinding
import kotlinx.coroutines.launch

class ChatSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatSearchBinding
    private lateinit var repository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = ChatRepository.get(this)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { search() }
        binding.etQuery.setOnEditorActionListener { _, _, _ -> search(); true }
    }

    private fun search() {
        val query = binding.etQuery.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            binding.tvState.text = "请输入关键词"
            binding.resultsContainer.removeAllViews()
            return
        }
        binding.tvState.text = "正在搜索..."
        lifecycleScope.launch {
            val results = repository.searchMessages(query)
            binding.resultsContainer.removeAllViews()
            if (results.isEmpty()) {
                binding.tvState.text = "没有找到相关消息"
                return@launch
            }
            binding.tvState.text = "找到 ${results.size} 条消息"
            results.take(50).forEach { message ->
                val item = TextView(this@ChatSearchActivity).apply {
                    setTextColor(Color.parseColor("#253033"))
                    textSize = 14f
                    setPadding(16, 14, 16, 14)
                    text = "${android.text.format.DateFormat.format("MM-dd HH:mm", message.timestamp)}\n" +
                        "${if (message.isMine) "我" else "对方"}：${message.content.ifBlank { "媒体消息" }}"
                    setBackgroundColor(Color.WHITE)
                }
                binding.resultsContainer.addView(
                    item,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                val divider = TextView(this@ChatSearchActivity).apply {
                    setBackgroundColor(Color.parseColor("#E2E8E5"))
                    minimumHeight = 1
                }
                binding.resultsContainer.addView(divider)
            }
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, ChatSearchActivity::class.java)
    }
}
