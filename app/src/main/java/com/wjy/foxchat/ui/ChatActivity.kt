package com.wjy.foxchat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.model.Message
import com.wjy.foxchat.notification.ChatNotificationManager
import com.wjy.foxchat.ui.compose.ChatScreen
import com.wjy.foxchat.ui.compose.ChatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatActivity : ComponentActivity() {
    private lateinit var repository: ChatRepository

    // Compose UI 状态
    private var messages by mutableStateOf<List<Message>>(emptyList())
    private var replyToMessageId by mutableStateOf<String?>(null)
    private var syncStatus by mutableStateOf("仅本地")
    private var inlineStatus by mutableStateOf<String?>(null)
    private var isRecording by mutableStateOf(false)
    private var backgroundPath by mutableStateOf<String?>(null)
    private var myAvatar by mutableStateOf<String?>(null)
    private var partnerAvatar by mutableStateOf<String?>(null)

    private var pendingGalleryPurpose = GalleryPurpose.IMAGE
    private var pendingCameraFile: File? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAt = 0L
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val maxRecordingRunnable = Runnable { stopRecording(send = true) }

    private val chooseImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handlePickedImage(uri)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (saved && file != null) {
            sendMedia(Message.TYPE_IMAGE, file.absolutePath, "image/jpeg")
        } else {
            file?.delete()
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showInlineStatus("未授予麦克风权限，无法录音")
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val openChatSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data
                ?.getStringExtra(ChatSettingsActivity.EXTRA_ACTION)
                ?.let(::handleSettingsAction)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ChatRepository.get(this)
        if (!repository.isPaired) {
            startActivity(Intent(this, ApiKeyActivity::class.java))
            finish()
            return
        }

        ChatNotificationManager.cancel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ChatNotificationManager.canPost(this)
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        UpdatePrompter.checkAndPrompt(this, silent = true)

        setContent {
            ChatTheme {
                ChatScreen(
                    messages = messages,
                    backgroundPath = backgroundPath,
                    replyToMessage = replyToMessageId?.let { id ->
                        messages.firstOrNull { it.id == id }
                    },
                    myAvatarPath = myAvatar,
                    partnerAvatarPath = partnerAvatar,
                    syncStatus = syncStatus,
                    currentRole = repository.currentRole,
                    isRecording = isRecording,
                    inlineStatus = inlineStatus,
                    onSendText = ::sendText,
                    onMoreClick = { openChatSettings.launch(ChatSettingsActivity.newIntent(this)) },
                    onSidebarAction = ::handleSidebarAction,
                    onReply = ::handleReply,
                    onCopy = ::handleCopy,
                    onRecall = { message -> lifecycleScope.launch { repository.recallMessage(message.id) } },
                    onDelete = { message -> lifecycleScope.launch { repository.deleteMessage(message.id) } },
                    onImageClick = ::showImagePreview,
                    onAudioClick = ::playAudio,
                    onClearReply = ::clearReply,
                    onStartRecording = ::startRecording,
                    onStopRecording = { send -> stopRecording(send) },
                    onCamera = ::openCamera,
                    onGallery = { openGallery(GalleryPurpose.IMAGE) },
                    onBackground = { openGallery(GalleryPurpose.BACKGROUND) }
                )
            }
        }

        lifecycleScope.launch {
            repository.ensureInitialized()
            updateSyncStatus()
            myAvatar = repository.myAvatar
            repository.syncNow()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeMessages().collect { collected ->
                    messages = collected
                    collected
                        .filter { !it.isMine && it.deliveryStatus != "READ" }
                        .forEach { message ->
                            lifecycleScope.launch { repository.markAsRead(message.id) }
                        }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeParticipants().collect { participants ->
                    val myRole = repository.currentRole
                    val mine = participants.firstOrNull { it.role == myRole }
                    val partner = participants.firstOrNull { it.role != myRole }
                    myAvatar = repository.myAvatar
                        ?: mine?.avatar?.let { repository.resolveAvatarLocalPath(it) }
                    partnerAvatar = partner?.avatar
                        ?.let { repository.resolveAvatarLocalPath(it) }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeBackground().collect { remotePath ->
                    applyBackground(repository.resolveBackgroundLocalPath(remotePath))
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    repository.syncNow()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ChatNotificationManager.cancel(this)
        lifecycleScope.launch {
            repository.syncNow()
            updateSyncStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        if (recorder != null) stopRecording(send = true)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(maxRecordingRunnable)
        recorder?.release()
        mediaPlayer?.release()
        super.onDestroy()
    }

    private fun sendText(text: String) {
        if (text.isBlank()) return
        lifecycleScope.launch {
            repository.sendText(text, replyToMessageId)
            clearReply()
            repository.syncNow()
            updateSyncStatus()
        }
    }

    private fun handleSettingsAction(action: String) {
        when (action) {
            ChatSettingsActivity.ACTION_SET_BACKGROUND -> openGallery(GalleryPurpose.BACKGROUND)
            ChatSettingsActivity.ACTION_RESET_BACKGROUND -> lifecycleScope.launch {
                repository.setBackground(null)
                applyBackground(null)
            }
            ChatSettingsActivity.ACTION_SYNC -> lifecycleScope.launch {
                repository.syncNow()
                updateSyncStatus()
            }
        }
    }

    private fun handleSidebarAction(action: String) {
        when (action) {
            "pet" ->
                startActivity(PetActivity.newIntent(this))
            "checkin" ->
                startActivity(CheckinCreateActivity.newIntent(this))
            "scheduled_notification" ->
                startActivity(ScheduledNotificationActivity.newIntent(this))
        }
    }

    private fun openGallery(purpose: GalleryPurpose) {
        pendingGalleryPurpose = purpose
        chooseImage.launch("image/*")
    }

    private fun openCamera() {
        val file = File(createMediaDirectory("images"), "IMG_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(uri)
    }

    private fun handlePickedImage(uri: Uri) {
        val copied = runCatching {
            val extension = contentResolver.getType(uri)
                ?.substringAfter('/', "jpg")
                ?.substringBefore(';')
                ?: "jpg"
            val target = File(
                createMediaDirectory(if (pendingGalleryPurpose == GalleryPurpose.BACKGROUND) {
                    "backgrounds"
                } else {
                    "images"
                }),
                "IMG_${System.currentTimeMillis()}.$extension"
            )
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取图片" }
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target
        }.getOrElse {
            showInlineStatus("图片读取失败")
            return
        }

        if (pendingGalleryPurpose == GalleryPurpose.BACKGROUND) {
            lifecycleScope.launch {
                repository.setBackground(copied.absolutePath)
                applyBackground(copied.absolutePath)
            }
        } else {
            sendMedia(
                Message.TYPE_IMAGE,
                copied.absolutePath,
                contentResolver.getType(uri) ?: "image/*"
            )
        }
    }

    private fun sendMedia(type: String, path: String, mimeType: String, durationMs: Long = 0L) {
        lifecycleScope.launch {
            repository.sendMedia(type, path, mimeType, durationMs, replyToMessageId)
            clearReply()
            repository.syncNow()
            updateSyncStatus()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recorder != null) return
        val file = File(createMediaDirectory("audio"), "AUD_${System.currentTimeMillis()}.m4a")
        // Android 12+ 用带 Context 的构造器，避免录音在部分设备上启动失败
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            recordingFile = file
            recordingStartedAt = System.currentTimeMillis()
            isRecording = true
            inlineStatus = "正在录音，松开发送，最长 60 秒"
            mainHandler.postDelayed(maxRecordingRunnable, MAX_RECORDING_MS)
        } catch (_: Exception) {
            newRecorder.release()
            file.delete()
            showInlineStatus("录音启动失败")
        }
    }

    private fun stopRecording(send: Boolean) {
        val activeRecorder = recorder ?: return
        recorder = null
        isRecording = false
        mainHandler.removeCallbacks(maxRecordingRunnable)
        val file = recordingFile
        recordingFile = null
        val duration = System.currentTimeMillis() - recordingStartedAt
        runCatching {
            activeRecorder.stop()
            activeRecorder.release()
        }
        clearReply()
        if (send && file != null && file.exists() && duration >= 500L) {
            sendMedia(Message.TYPE_AUDIO, file.absolutePath, "audio/mp4", duration)
        } else {
            file?.delete()
        }
        inlineStatus = null
    }

    private fun handleReply(message: Message) {
        replyToMessageId = message.id
    }

    private fun handleCopy(message: Message) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("FoxChat 消息", message.content))
    }

    private fun showImagePreview(message: Message) {
        val path = message.mediaPath ?: return
        startActivity(
            Intent(this, ImagePreviewActivity::class.java)
                .putExtra(ImagePreviewActivity.EXTRA_PATH, path)
        )
    }

    private fun playAudio(message: Message) {
        val raw = message.mediaPath ?: return
        val file = File(raw)
        if (!file.exists()) {
            // 语音文件尚未下载到本地（远程路径未同步完）
            showInlineStatus("语音文件未就绪，请稍后重试")
            return
        }
        mediaPlayer?.release()
        mediaPlayer = try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { player ->
                    player.release()
                    if (mediaPlayer === player) mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    showInlineStatus("语音播放失败")
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            showInlineStatus("语音播放失败")
            null
        }
    }

    private fun applyBackground(path: String?) {
        backgroundPath = path
    }

    private fun updateSyncStatus() {
        syncStatus = if (repository.isRemoteConfigured) "已同步" else "仅本地"
    }

    private fun showInlineStatus(message: String) {
        inlineStatus = message
        mainHandler.postDelayed({
            if (!isFinishing) inlineStatus = null
        }, 2600L)
    }

    private fun clearReply() {
        replyToMessageId = null
    }

    private fun createMediaDirectory(name: String): File {
        return File(filesDir, "media/$name").apply { mkdirs() }
    }

    companion object {
        private const val MAX_RECORDING_MS = 60_000L
        private const val SYNC_INTERVAL_MS = 1_000L
        fun newIntent(context: Context): Intent = Intent(context, ChatActivity::class.java)
    }

    private enum class GalleryPurpose {
        IMAGE,
        BACKGROUND
    }
}
