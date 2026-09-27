package com.wjy.foxchat.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.model.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 聊天页 ViewModel：持有全部 Compose 可观察状态，并在自身协程作用域内完成
 * 数据收集（消息 / 参与者 / 背景）与后台同步循环。
 *
 * Activity 只负责设备交互（录音、播放、相册、相机）与导航，
 * 不再直接持有业务状态，旋转屏幕时状态随 ViewModel 保留。
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ChatRepository.get(app)

    // ---- Compose 可观察状态（旋转不丢失）----
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    var replyToMessageId by mutableStateOf<String?>(null)
        private set
    var syncStatus by mutableStateOf("仅本地")
        private set
    var inlineStatus by mutableStateOf<String?>(null)
        private set
    var backgroundPath by mutableStateOf<String?>(null)
        private set
    var myAvatar by mutableStateOf<String?>(null)
        private set
    var partnerAvatar by mutableStateOf<String?>(null)
        private set

    val currentRole: String
        get() = repository.currentRole
    val isPaired: Boolean
        get() = repository.isPaired
    val replyToMessage: Message?
        get() = replyToMessageId?.let { id -> messages.firstOrNull { it.id == id } }

    private var uiJob: Job? = null
    private var statusClearJob: Job? = null

    /** UI 可见（onStart）时启动：初始化 + 本地数据收集 + 后台同步 + 周期同步循环。 */
    fun onUiStarted() {
        if (uiJob != null) return
        uiJob = viewModelScope.launch {
            repository.ensureInitialized()
            updateSyncStatus()
            myAvatar = repository.myAvatar
            // 先启动本地数据收集：Room 会立即 emit 本地已存消息，实现文字秒开
            launch { collectMessages() }
            launch { collectParticipants() }
            launch { collectBackground() }
            // 网络同步放到后台执行，不阻塞消息列表显示
            repository.syncNow()
            // 媒体文件后台预取（仅最近窗口内缺失的图片/语音）
            launch { repository.prefetchRecentMedia() }
            launch {
                while (isActive) {
                    repository.syncNow()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }
    }

    /** UI 不可见（onStop）时停止数据收集与同步循环。 */
    fun onUiStopped() {
        uiJob?.cancel()
        uiJob = null
    }

    /** 前台恢复（onResume）时主动同步一次。 */
    fun syncOnce() {
        viewModelScope.launch {
            repository.syncNow()
            updateSyncStatus()
        }
    }

    private suspend fun collectMessages() {
        repository.observeMessages().collect { collected ->
            messages = collected
            collected
                .filter { !it.isMine && it.deliveryStatus != "READ" }
                .forEach { message ->
                    viewModelScope.launch { repository.markAsRead(message.id) }
                }
        }
    }

    private suspend fun collectParticipants() {
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

    private suspend fun collectBackground() {
        repository.observeBackground().collect { remotePath ->
            backgroundPath = repository.resolveBackgroundLocalPath(remotePath)
        }
    }

    // ---- 发送 / 操作 ----
    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendText(text, replyToMessageId)
            clearReply()
            repository.syncNow()
            updateSyncStatus()
        }
    }

    fun sendMedia(type: String, path: String, mimeType: String, durationMs: Long = 0L) {
        viewModelScope.launch {
            repository.sendMedia(type, path, mimeType, durationMs, replyToMessageId)
            clearReply()
            repository.syncNow()
            updateSyncStatus()
        }
    }

    fun recall(message: Message) {
        viewModelScope.launch { repository.recallMessage(message.id) }
    }

    fun delete(message: Message) {
        viewModelScope.launch { repository.deleteMessage(message.id) }
    }

    fun setReplyTo(message: Message) {
        replyToMessageId = message.id
    }

    fun clearReply() {
        replyToMessageId = null
    }

    fun setBackground(path: String?) {
        viewModelScope.launch {
            repository.setBackground(path)
            backgroundPath = path
        }
    }

    fun resetBackground() {
        viewModelScope.launch {
            repository.setBackground(null)
            backgroundPath = null
        }
    }

    /** 顶部提示条：展示后自动消失。 */
    fun showInlineStatus(message: String) {
        inlineStatus = message
        statusClearJob?.cancel()
        statusClearJob = viewModelScope.launch {
            delay(STATUS_DURATION_MS)
            inlineStatus = null
        }
    }

    fun clearInlineStatus() {
        statusClearJob?.cancel()
        inlineStatus = null
    }

    private fun updateSyncStatus() {
        syncStatus = if (repository.isRemoteConfigured) "已同步" else "仅本地"
    }

    companion object {
        private const val SYNC_INTERVAL_MS = 1_000L
        private const val STATUS_DURATION_MS = 2_600L
    }
}
