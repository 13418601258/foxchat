package com.wjy.foxchat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val backgroundUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("createdAt"),
        Index("senderId")
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderRole: String,
    val type: String = "TEXT",
    val text: String? = null,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaDurationMs: Long = 0L,
    val replyToMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long? = null,
    val recalledAt: Long? = null,
    val recalledText: String? = null,
    val deliveryStatus: String = "SENT",
    val readAt: Long? = null,
    val syncStatus: String = "PENDING"
)

@Entity(tableName = "media_attachments", indices = [Index("messageId")])
data class MediaAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val localPath: String,
    val remotePath: String? = null,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val thumbnailPath: String? = null,
    val uploadStatus: String = "PENDING"
)

@Entity(tableName = "weekly_reports", indices = [Index(value = ["conversationId", "weekKey"], unique = true)])
data class WeeklyReportEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val weekKey: String,
    val summary: String,
    val topics: String,
    val moodTrend: String,
    val interactionChange: String,
    val importantEvents: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val deviceId: String,
    val analysisConsent: Boolean = false,
    val avatar: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "outbox", indices = [Index("messageId")])
data class OutboxEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val kind: String = "MESSAGE",
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pet")
data class PetEntity(
    @PrimaryKey val id: Int = 1,
    val food: Int = 10,
    val drink: Int = 10,
    val condition: Double = 10.0,
    val love: Int = 0,
    val days: Int = 0,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis()
)
