package com.wjy.foxchat.data.repository

import android.content.Context
import com.wjy.foxchat.data.DeviceIdentityStore
import com.wjy.foxchat.data.LegacyDataMigrator
import com.wjy.foxchat.data.local.ConversationEntity
import com.wjy.foxchat.data.local.FoxChatDatabase
import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.data.local.OutboxEntity
import com.wjy.foxchat.data.local.ParticipantEntity
import com.wjy.foxchat.data.remote.SupabaseRemote
import com.wjy.foxchat.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ChatRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = FoxChatDatabase.get(appContext)
    private val identity = DeviceIdentityStore(appContext)
    private val remote = SupabaseRemote()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializeMutex = Mutex()
    private val syncMutex = Mutex()
    @Volatile private var initialized = false

    val isRemoteConfigured: Boolean
        get() = remote.isConfigured

    val isPaired: Boolean
        get() = identity.isPaired

    val currentRole: String
        get() = identity.participantRole ?: "A"

    val currentConversationId: String
        get() = identity.roomId ?: DEFAULT_CONVERSATION_ID

    val myAnalysisConsent: Boolean
        get() = identity.analysisConsent

    val myAvatar: String?
        get() = identity.avatarPath()

    suspend fun ensureInitialized() {
        if (initialized) return
        initializeMutex.withLock {
            if (initialized) return
            LegacyDataMigrator.migrate(appContext, database)
            database.conversationDao().upsert(
                ConversationEntity(
                    id = currentConversationId,
                    title = "FoxChat",
                    updatedAt = System.currentTimeMillis()
                )
            )
            initialized = true
        }
    }

    suspend fun pair(
        role: String,
        pairingKey: String,
        analysisConsent: Boolean,
        avatar: String?
    ): Result<Unit> = runCatching {
        require(role == "A" || role == "B") { "请选择使用者 A 或 B" }
        require(pairingKey.trim().length >= 6) { "配对密钥至少需要 6 位" }

        val localRoomId = roomIdFromKey(pairingKey)
        val remoteRoomId = if (remote.isConfigured) {
            identity.authToken()?.let(remote::setAccessToken)
                ?: remote.signInAnonymously(identity.deviceId)
                    .onSuccess(identity::saveAuthToken)
                    .getOrThrow()
            remote.pairDevice(pairingKey.trim(), role, identity.deviceId).getOrThrow()
        } else {
            localRoomId
        }

        identity.savePairing(role, remoteRoomId, analysisConsent)
        if (avatar != null) identity.saveAvatarPath(avatar)
        initialized = false
        ensureInitialized()
        database.participantDao().upsert(
            ParticipantEntity(
                id = "$remoteRoomId:$role",
                conversationId = remoteRoomId,
                role = role,
                deviceId = identity.deviceId,
                analysisConsent = analysisConsent,
                avatar = avatar
            )
        )
        if (remote.isConfigured && avatar != null) {
            remote.updateAnalysisConsent(remoteRoomId, analysisConsent)
            remote.uploadAndSetAvatar(remoteRoomId, role, avatar)
        } else if (remote.isConfigured) {
            remote.updateAnalysisConsent(remoteRoomId, analysisConsent)
        }
    }

    fun observeParticipants(): Flow<List<ParticipantEntity>> =
        database.participantDao().observeForConversation(currentConversationId)

    /** 将远程头像路径解析为本地可显示路径（必要时下载到本地缓存）。 */
    suspend fun resolveAvatarLocalPath(remotePath: String?): String? {
        if (remotePath.isNullOrBlank()) return null
        if (File(remotePath).exists()) return remotePath
        val target = File(appContext.filesDir, "avatars/cache/${remotePath.hashCode()}.jpg")
        if (target.exists()) return target.absolutePath
        return remote.downloadMedia(remotePath, target).getOrNull()?.absolutePath
    }

    fun observeMessages(): Flow<List<Message>> {
        val since = System.currentTimeMillis() - RECENT_WINDOW_MS
        return database.messageDao().observeRecent(currentConversationId, since).map { items ->
            items.map { entity ->
                Message(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    senderRole = entity.senderRole,
                    type = entity.type,
                    content = entity.text.orEmpty(),
                    mediaPath = entity.mediaPath,
                    mediaMimeType = entity.mediaMimeType,
                    mediaDurationMs = entity.mediaDurationMs,
                    replyToMessageId = entity.replyToMessageId,
                    timestamp = entity.createdAt,
                    recalledAt = entity.recalledAt,
                    recalledText = entity.recalledText,
                    deliveryStatus = entity.deliveryStatus,
                    isMine = entity.senderId == identity.deviceId ||
                        entity.senderRole == "LEGACY_USER"
                )
            }
        }
    }

    suspend fun sendText(text: String, replyToMessageId: String? = null) {
        ensureInitialized()
        val targetType = replyToMessageId?.let { rid ->
            database.messageDao().find(rid)?.type
        }
        val type = if (targetType == Message.TYPE_CHECKIN) {
            Message.TYPE_CHECKIN_REPLY
        } else {
            Message.TYPE_TEXT
        }
        insertOutgoing(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = currentConversationId,
                senderId = identity.deviceId,
                senderRole = currentRole,
                type = type,
                text = text,
                replyToMessageId = replyToMessageId
            )
        )
    }

    suspend fun sendCheckin(title: String) {
        ensureInitialized()
        insertOutgoing(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = currentConversationId,
                senderId = identity.deviceId,
                senderRole = currentRole,
                type = Message.TYPE_CHECKIN,
                text = title
            )
        )
    }

    suspend fun sendMedia(
        type: String,
        localPath: String,
        mimeType: String,
        durationMs: Long = 0L,
        replyToMessageId: String? = null
    ) {
        ensureInitialized()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = currentConversationId,
            senderId = identity.deviceId,
            senderRole = currentRole,
            type = type,
            mediaPath = localPath,
            mediaMimeType = mimeType,
            mediaDurationMs = durationMs,
            replyToMessageId = replyToMessageId
        )
        insertOutgoing(message)
    }

    suspend fun recallMessage(messageId: String) {
        database.messageDao().recall(messageId)
        database.outboxDao().insert(OutboxEntity(UUID.randomUUID().toString(), messageId))
        syncNow()
    }

    suspend fun deleteMessage(messageId: String) {
        val message = database.messageDao().find(messageId)
        database.messageDao().delete(messageId)
        database.outboxDao().deleteForMessage(messageId)
        message?.mediaPath?.let { path ->
            if (path.startsWith("/")) File(path).delete()
        }
    }

    suspend fun markAsRead(messageId: String) {
        database.messageDao().markRead(messageId)
    }

    suspend fun searchMessages(query: String): List<Message> =
        database.messageDao().search(currentConversationId, query).map { entity ->
            Message(
                id = entity.id,
                conversationId = entity.conversationId,
                senderId = entity.senderId,
                senderRole = entity.senderRole,
                type = entity.type,
                content = entity.text.orEmpty(),
                mediaPath = entity.mediaPath,
                mediaMimeType = entity.mediaMimeType,
                mediaDurationMs = entity.mediaDurationMs,
                replyToMessageId = entity.replyToMessageId,
                timestamp = entity.createdAt,
                recalledAt = entity.recalledAt,
                recalledText = entity.recalledText,
                deliveryStatus = entity.deliveryStatus,
                isMine = entity.senderId == identity.deviceId
            )
        }

    suspend fun setBackground(uri: String?) {
        ensureInitialized()
        if (uri == null) {
            database.conversationDao().updateBackground(currentConversationId, null)
            if (remote.isConfigured) {
                remote.setRoomBackground(currentConversationId, null)
            }
            return
        }
        if (remote.isConfigured) {
            val remotePath = remote.uploadBackground(currentConversationId, uri).getOrNull()
            if (remotePath != null) {
                database.conversationDao().updateBackground(currentConversationId, remotePath)
                remote.setRoomBackground(currentConversationId, remotePath)
            } else {
                database.conversationDao().updateBackground(currentConversationId, uri)
            }
        } else {
            database.conversationDao().updateBackground(currentConversationId, uri)
        }
    }

    suspend fun backgroundUri(): String? {
        ensureInitialized()
        return database.conversationDao().find(currentConversationId)?.backgroundUri
    }

    fun observeBackground(): Flow<String?> =
        database.conversationDao().observe(currentConversationId).map { it?.backgroundUri }

    /** 将远程背景路径解析为本地可显示路径（必要时下载到本地缓存）。 */
    suspend fun resolveBackgroundLocalPath(remotePath: String?): String? {
        if (remotePath.isNullOrBlank()) return null
        if (File(remotePath).exists()) return remotePath
        val target = File(appContext.filesDir, "backgrounds/cache/${remotePath.hashCode()}.jpg")
        if (target.exists()) return target.absolutePath
        return remote.downloadMedia(remotePath, target).getOrNull()?.absolutePath
    }

    suspend fun clearConversation() {
        database.messageDao().clearConversation(currentConversationId)
    }

    suspend fun exportMessages(): String {
        ensureInitialized()
        return database.messageDao().allForConversation(currentConversationId)
            .joinToString("\n") { message ->
                val time = android.text.format.DateFormat
                    .format("yyyy-MM-dd HH:mm:ss", message.createdAt)
                val sender = if (message.senderId == identity.deviceId) "我" else "对方"
                val body = when {
                    message.recalledAt != null -> "消息已撤回"
                    message.type == Message.TYPE_TEXT -> message.text.orEmpty()
                    else -> "[${message.type}] ${message.mediaPath.orEmpty()}"
                }
                "$time\t$sender\t$body"
            }
    }

    suspend fun updateAnalysisConsent(consent: Boolean) {
        identity.updateAnalysisConsent(consent)
        if (identity.isPaired) {
            database.participantDao().upsert(
                ParticipantEntity(
                    id = "${currentConversationId}:${currentRole}",
                    conversationId = currentConversationId,
                    role = currentRole,
                    deviceId = identity.deviceId,
                    analysisConsent = consent
                )
            )
            if (remote.isConfigured) {
                identity.authToken()?.let(remote::setAccessToken)
                remote.updateAnalysisConsent(currentConversationId, consent)
            }
        }
    }

    suspend fun hasBothAnalysisConsent(): Boolean {
        ensureInitialized()
        val participants = database.participantDao().findForConversation(currentConversationId)
        val roles = participants.map { it.role }.toSet()
        return roles.containsAll(setOf("A", "B")) &&
            participants.filter { it.role == "A" || it.role == "B" }.all { it.analysisConsent }
    }

    suspend fun messagesForAnalysis(since: Long): List<MessageEntity> =
        database.messageDao().since(currentConversationId, since)

    suspend fun latestReport() =
        database.weeklyReportDao().latest(currentConversationId)

    suspend fun saveReport(report: com.wjy.foxchat.data.local.WeeklyReportEntity) {
        database.weeklyReportDao().upsert(report)
        if (remote.isConfigured && identity.isPaired) {
            remote.upsertWeeklyReport(report)
        }
    }

    fun observeReports() =
        database.weeklyReportDao().observeForConversation(currentConversationId)

    data class SyncOutcome(
        val incomingMessages: List<MessageEntity>,
        val initialSync: Boolean
    )

    suspend fun syncNow(): Result<Unit> =
        syncNowWithOutcome().map { Unit }

    suspend fun syncNowWithOutcome(): Result<SyncOutcome> = syncMutex.withLock {
        runCatching {
            ensureInitialized()
            if (!remote.isConfigured || !identity.isPaired) {
                return@runCatching SyncOutcome(emptyList(), initialSync = false)
            }
            val previousSyncTime = identity.remoteSyncTime()
            val initialSync = previousSyncTime == 0L
            identity.authToken()?.let(remote::setAccessToken)
                ?: remote.signInAnonymously(identity.deviceId)
                    .onSuccess(identity::saveAuthToken)
                    .getOrThrow()
            val pending = database.messageDao().pendingForConversation(currentConversationId)
            pending.forEach { message ->
                val outbound = if (
                    message.type == Message.TYPE_IMAGE || message.type == Message.TYPE_AUDIO
                ) {
                    val localFile = message.mediaPath?.let(::File)
                    if (localFile != null && localFile.exists()) {
                        val remotePath = remote.uploadMedia(
                            currentConversationId,
                            message.id,
                            localFile,
                            message.mediaMimeType ?: "application/octet-stream"
                        ).getOrThrow()
                        message.copy(mediaPath = remotePath)
                    } else {
                        message
                    }
                } else {
                    message
                }
                remote.upsertMessage(outbound).getOrThrow()
                database.messageDao().updateSyncState(message.id, "SYNCED", "DELIVERED")
                database.outboxDao().deleteForMessage(message.id)
            }

            val remoteMessages = remote.fetchMessages(currentConversationId, previousSyncTime).getOrThrow()
            val incomingMessages = if (initialSync) {
                emptyList()
            } else {
                remoteMessages.filter { it.senderId != identity.deviceId }
            }
            if (remoteMessages.isNotEmpty()) {
                val localMessages = remoteMessages.map { message ->
                    if (
                        (message.type == Message.TYPE_IMAGE || message.type == Message.TYPE_AUDIO) &&
                        !message.mediaPath.isNullOrBlank() &&
                        !File(message.mediaPath).exists()
                    ) {
                        val extension = message.mediaMimeType
                            ?.substringAfter('/', "bin")
                            ?.substringBefore(';')
                            ?: "bin"
                        val target = File(appContext.filesDir, "media/synced/${message.id}.$extension")
                        remote.downloadMedia(message.mediaPath, target)
                            .getOrNull()
                            ?.let { message.copy(mediaPath = it.absolutePath) }
                            ?: message
                    } else {
                        message
                    }
                }
                database.messageDao().upsertAll(localMessages)
                identity.saveRemoteSyncTime(remoteMessages.maxOf { it.createdAt })
            }
            remote.fetchParticipants(currentConversationId)
                .getOrNull()
                ?.forEach { database.participantDao().upsert(it) }
            remote.fetchWeeklyReports(currentConversationId)
                .getOrNull()
                ?.forEach { database.weeklyReportDao().upsert(it) }
            remote.fetchRoomBackground(currentConversationId)
                .getOrNull()
                ?.let { remoteUri ->
                    val current = database.conversationDao().find(currentConversationId)?.backgroundUri
                    if (remoteUri != current) {
                        database.conversationDao().updateBackground(currentConversationId, remoteUri)
                    }
                }
            SyncOutcome(incomingMessages, initialSync)
        }
    }

    suspend fun uploadMedia(localPath: String, messageId: String, mimeType: String): Result<String> {
        if (!remote.isConfigured || !identity.isPaired) {
            return Result.failure(IllegalStateException("Supabase 尚未配置"))
        }
        return remote.uploadMedia(currentConversationId, messageId, File(localPath), mimeType)
    }

    private suspend fun insertOutgoing(message: MessageEntity) {
        database.messageDao().upsert(message)
        database.outboxDao().insert(OutboxEntity(UUID.randomUUID().toString(), message.id))
        scope.launch { syncNow() }
    }

    private fun roomIdFromKey(pairingKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pairingKey.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "room_${digest.take(32)}"
    }

    companion object {
        const val DEFAULT_CONVERSATION_ID = "local-private-room"
        const val RECENT_WINDOW_MS = 3L * 24L * 60L * 60L * 1000L

        @Volatile private var instance: ChatRepository? = null

        fun get(context: Context): ChatRepository =
            instance ?: synchronized(this) {
                instance ?: ChatRepository(context).also { instance = it }
            }
    }
}
