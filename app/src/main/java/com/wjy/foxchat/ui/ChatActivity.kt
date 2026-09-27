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
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.wjy.foxchat.model.Message
import com.wjy.foxchat.notification.ChatNotificationManager
import com.wjy.foxchat.ui.compose.ChatScreen
import com.wjy.foxchat.ui.compose.ChatTheme
import java.io.File
import java.io.FileOutputStream

/**
 * 聊天页 Activity：仅负责设备交互（录音、播放、相册、相机、剪贴板、权限）与页面导航。
 * 全部业务状态由 [ChatViewModel] 持有，旋转屏幕状态不丢失。
 */
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    // 录音相关瞬时状态（设备资源绑定 Activity 生命周期，旋转即中断，符合预期）
    private var isRecording by mutableStateOf(false)
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
            viewModel.sendMedia(Message.TYPE_IMAGE, file.absolutePath, "image/jpeg")
        } else {
            file?.delete()
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) viewModel.showInlineStatus("未授予麦克风权限，无法录音")
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
        if (!viewModel.isPaired) {
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
                    messages = viewModel.messages,
                    backgroundPath = viewModel.backgroundPath,
                    replyToMessage = viewModel.replyToMessage,
                    myAvatarPath = viewModel.myAvatar,
                    partnerAvatarPath = viewModel.partnerAvatar,
                    syncStatus = viewModel.syncStatus,
                    currentRole = viewModel.currentRole,
                    isRecording = isRecording,
                    inlineStatus = viewModel.inlineStatus,
                    onSendText = viewModel::sendText,
                    onMoreClick = { openChatSettings.launch(ChatSettingsActivity.newIntent(this)) },
                    onSidebarAction = ::handleSidebarAction,
                    onReply = viewModel::setReplyTo,
                    onCopy = ::handleCopy,
                    onRecall = viewModel::recall,
                    onDelete = viewModel::delete,
                    onImageClick = ::showImagePreview,
                    onAudioClick = ::playAudio,
                    onClearReply = viewModel::clearReply,
                    onStartRecording = ::startRecording,
                    onStopRecording = { send -> stopRecording(send) },
                    onCamera = ::openCamera,
                    onGallery = { openGallery(GalleryPurpose.IMAGE) },
                    onBackground = { openGallery(GalleryPurpose.BACKGROUND) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onUiStarted()
    }

    override fun onStop() {
        viewModel.onUiStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        ChatNotificationManager.cancel(this)
        viewModel.syncOnce()
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

    private fun handleSettingsAction(action: String) {
        when (action) {
            ChatSettingsActivity.ACTION_SET_BACKGROUND -> openGallery(GalleryPurpose.BACKGROUND)
            ChatSettingsActivity.ACTION_RESET_BACKGROUND -> viewModel.resetBackground()
            ChatSettingsActivity.ACTION_SYNC -> viewModel.syncOnce()
        }
    }

    private fun handleSidebarAction(action: String) {
        when (action) {
            "pet" -> startActivity(PetActivity.newIntent(this))
            "checkin" -> startActivity(CheckinCreateActivity.newIntent(this))
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
            viewModel.showInlineStatus("图片读取失败")
            return
        }

        if (pendingGalleryPurpose == GalleryPurpose.BACKGROUND) {
            viewModel.setBackground(copied.absolutePath)
        } else {
            viewModel.sendMedia(
                Message.TYPE_IMAGE,
                copied.absolutePath,
                contentResolver.getType(uri) ?: "image/*"
            )
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
            viewModel.showInlineStatus("正在录音，松开发送，最长 60 秒")
            mainHandler.postDelayed(maxRecordingRunnable, MAX_RECORDING_MS)
        } catch (_: Exception) {
            newRecorder.release()
            file.delete()
            viewModel.showInlineStatus("录音启动失败")
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
        viewModel.clearReply()
        if (send && file != null && file.exists() && duration >= 500L) {
            viewModel.sendMedia(Message.TYPE_AUDIO, file.absolutePath, "audio/mp4", duration)
        } else {
            file?.delete()
        }
        viewModel.clearInlineStatus()
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
            viewModel.showInlineStatus("语音文件未就绪，请稍后重试")
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
                    viewModel.showInlineStatus("语音播放失败")
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            viewModel.showInlineStatus("语音播放失败")
            null
        }
    }

    private fun createMediaDirectory(name: String): File {
        return File(filesDir, "media/$name").apply { mkdirs() }
    }

    companion object {
        private const val MAX_RECORDING_MS = 60_000L
        fun newIntent(context: Context): Intent = Intent(context, ChatActivity::class.java)
    }

    private enum class GalleryPurpose {
        IMAGE,
        BACKGROUND
    }
}
