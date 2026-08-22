package com.wjy.foxchat.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.R
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityAnalysisConsentBinding
import kotlinx.coroutines.launch

class AnalysisConsentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalysisConsentBinding
    private lateinit var repository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = ChatRepository.get(this)
        findViewById<android.widget.TextView>(com.wjy.foxchat.R.id.tvTitle).text = "每周分析同意"
        findViewById<android.widget.ImageButton>(com.wjy.foxchat.R.id.btnBack).setOnClickListener { finish() }
        render()
        binding.btnToggle.setOnClickListener {
            lifecycleScope.launch {
                repository.updateAnalysisConsent(!repository.myAnalysisConsent)
                repository.syncNow()
                render()
                setResult(Activity.RESULT_OK)
            }
        }
    }

    private fun render() {
        val enabled = repository.myAnalysisConsent
        binding.tvState.text = if (enabled) "每周分析已开启" else "每周分析未开启"
        binding.btnToggle.text = if (enabled) "关闭每周分析" else "开启每周分析"
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, AnalysisConsentActivity::class.java)
    }
}
