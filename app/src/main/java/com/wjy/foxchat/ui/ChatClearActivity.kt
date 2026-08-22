package com.wjy.foxchat.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityChatClearBinding
import kotlinx.coroutines.launch

class ChatClearActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatClearBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatClearBinding.inflate(layoutInflater)
        setContentView(binding.root)
        findViewById<android.widget.TextView>(com.wjy.foxchat.R.id.tvTitle).text = "清空聊天"
        findViewById<android.widget.ImageButton>(com.wjy.foxchat.R.id.btnBack).setOnClickListener { finish() }
        binding.btnClear.setOnClickListener {
            binding.btnClear.isEnabled = false
            lifecycleScope.launch {
                ChatRepository.get(this@ChatClearActivity).clearConversation()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, ChatClearActivity::class.java)
    }
}
