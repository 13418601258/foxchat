package com.wjy.foxchat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityChatExportBinding
import kotlinx.coroutines.launch
import java.io.File

class ChatExportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatExportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        findViewById<android.widget.TextView>(com.wjy.foxchat.R.id.tvTitle).text = "导出聊天记录"
        findViewById<android.widget.ImageButton>(com.wjy.foxchat.R.id.btnBack).setOnClickListener { finish() }
        binding.btnExport.setOnClickListener { export() }
    }

    private fun export() {
        binding.btnExport.isEnabled = false
        lifecycleScope.launch {
            val file = File(cacheDir, "exports/chat-${System.currentTimeMillis()}.txt")
                .apply {
                    parentFile?.mkdirs()
                    writeText(ChatRepository.get(this@ChatExportActivity).exportMessages())
                }
            val uri: Uri = FileProvider.getUriForFile(
                this@ChatExportActivity,
                "$packageName.fileprovider",
                file
            )
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "导出聊天记录"
                )
            )
            binding.btnExport.isEnabled = true
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, ChatExportActivity::class.java)
    }
}
