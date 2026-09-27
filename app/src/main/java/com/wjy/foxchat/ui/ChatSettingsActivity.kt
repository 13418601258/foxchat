package com.wjy.foxchat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.BuildConfig
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityChatSettingsBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatSettingsBinding
    private lateinit var repository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = ChatRepository.get(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.rowSearch.setOnClickListener { startActivity(ChatSearchActivity.newIntent(this)) }
        binding.rowChangeAvatar.setOnClickListener { chooseAvatar.launch("image/*") }
        binding.rowSetBackground.setOnClickListener { select(ACTION_SET_BACKGROUND) }
        binding.rowResetBackground.setOnClickListener { select(ACTION_RESET_BACKGROUND) }
        binding.rowReports.setOnClickListener { startActivity(WeeklyReportsActivity.newIntent(this)) }
        binding.rowAnalysis.setOnClickListener { startActivity(AnalysisConsentActivity.newIntent(this)) }
        binding.rowExport.setOnClickListener { startActivity(ChatExportActivity.newIntent(this)) }
        binding.rowSync.setOnClickListener { select(ACTION_SYNC) }
        binding.rowClear.setOnClickListener { startActivity(ChatClearActivity.newIntent(this)) }
        binding.rowCheckUpdate.setOnClickListener { UpdatePrompter.checkAndPrompt(this) }
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updateStatus()
    }

    private fun updateStatus() {
        binding.tvSyncStatus.text = if (repository.isRemoteConfigured) {
            "已同步"
        } else {
            "仅本地"
        }
        binding.tvAnalysisState.text = if (repository.myAnalysisConsent) {
            getString(com.wjy.foxchat.R.string.enabled)
        } else {
            getString(com.wjy.foxchat.R.string.disabled)
        }
    }

    private val chooseAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) changeAvatar(uri)
    }

    private fun changeAvatar(uri: Uri) {
        val file = runCatching {
            val dir = File(filesDir, "avatars").apply { mkdirs() }
            val target = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取图片" }
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target
        }.getOrElse {
            Toast.makeText(this, "头像读取失败", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            repository.changeAvatar(file.absolutePath).onSuccess {
                Toast.makeText(this@ChatSettingsActivity, "头像已更新", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@ChatSettingsActivity, "头像更新失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun select(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "chat_settings_action"
        const val ACTION_SEARCH = "search"
        const val ACTION_SET_BACKGROUND = "set_background"
        const val ACTION_RESET_BACKGROUND = "reset_background"
        const val ACTION_REPORTS = "reports"
        const val ACTION_ANALYSIS_CONSENT = "analysis_consent"
        const val ACTION_EXPORT = "export"
        const val ACTION_SYNC = "sync"
        const val ACTION_CLEAR = "clear"

        fun newIntent(context: Context): Intent =
            Intent(context, ChatSettingsActivity::class.java)
    }
}
