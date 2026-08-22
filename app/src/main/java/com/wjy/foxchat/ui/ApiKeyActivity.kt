package com.wjy.foxchat.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.BuildConfig
import com.wjy.foxchat.data.DeviceIdentityStore
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.databinding.ActivityApiKeyBinding
import com.wjy.foxchat.notification.ChatNotificationManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ApiKeyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityApiKeyBinding
    private lateinit var repository: ChatRepository
    private lateinit var identity: DeviceIdentityStore
    private var avatarPath: String? = null

    private val chooseAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleAvatar(uri)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ChatRepository.get(this)
        identity = DeviceIdentityStore(this)

        if (repository.isPaired) {
            openChat()
            return
        }

        binding = ActivityApiKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ChatNotificationManager.canPost(this)
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        binding.etBaseUrl.setText(BuildConfig.AI_BASE_URL.ifBlank { "https://api.deepseek.com" })
        binding.etApiKey.setText(BuildConfig.AI_API_KEY)
        binding.etBaseUrl.isEnabled = false
        binding.etApiKey.isEnabled = false
        binding.tvSupabaseStatus.text = if (
            BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
        ) {
            "云端同步已配置"
        } else {
            "未配置 Supabase：当前设备可离线聊天，跨设备同步将在配置后启用"
        }

        binding.etPairingKey.doAfterTextChanged { updateButtonState() }
        binding.btnStart.setOnClickListener { pairAndOpen() }
        binding.avatarCard.setOnClickListener { chooseAvatar.launch("image/*") }
        updateButtonState()
    }

    private fun handleAvatar(uri: Uri) {
        val target = runCatching {
            val dir = File(filesDir, "avatars").apply { mkdirs() }
            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取图片" }
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file
        }.getOrElse {
            binding.tvSupabaseStatus.text = "头像读取失败"
            return
        }
        avatarPath = target.absolutePath
        binding.ivAvatar.setImageURI(Uri.fromFile(target))
    }

    private fun updateButtonState() {
        val enabled = binding.etPairingKey.text?.trim()?.length ?: 0 >= 6
        binding.btnStart.isEnabled = enabled
        binding.btnStart.alpha = if (enabled) 1f else 0.55f
    }

    private fun pairAndOpen() {
        val role = if (binding.rbParticipantA.isChecked) "A" else "B"
        val key = binding.etPairingKey.text?.toString()?.trim().orEmpty()
        if (key.length < 6) {
            binding.tvSupabaseStatus.text = "配对密钥至少需要 6 位"
            return
        }

        binding.btnStart.isEnabled = false
        identity.saveAiConfiguration(
            BuildConfig.AI_BASE_URL.ifBlank { "https://api.deepseek.com" },
            BuildConfig.AI_API_KEY
        )
        lifecycleScope.launch {
            repository.pair(role, key, binding.cbAnalysisConsent.isChecked, avatarPath)
                .onSuccess {
                    hideKeyboard()
                    openChat()
                }
                .onFailure { error ->
                    binding.btnStart.isEnabled = true
                    binding.tvSupabaseStatus.text = error.message ?: "配对失败"
                }
        }
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
