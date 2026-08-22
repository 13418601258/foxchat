package com.wjy.foxchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wjy.foxchat.databinding.ActivityChatToolsBinding

class ChatToolsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCamera.setOnClickListener { select(ACTION_CAMERA) }
        binding.btnGallery.setOnClickListener { select(ACTION_GALLERY) }
        binding.btnBackground.setOnClickListener { select(ACTION_BACKGROUND) }
    }

    private fun select(action: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "chat_tool_action"
        const val ACTION_CAMERA = "camera"
        const val ACTION_GALLERY = "gallery"
        const val ACTION_BACKGROUND = "background"

        fun newIntent(context: Context) = Intent(context, ChatToolsActivity::class.java)
    }
}
