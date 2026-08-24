package com.wjy.foxchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET backgroundUri = :uri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBackground(id: String, uri: String?, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND createdAt > :since ORDER BY createdAt ASC")
    fun observeRecent(conversationId: String, since: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun pendingForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND (text LIKE '%' || :query || '%' OR senderId LIKE '%' || :query || '%') ORDER BY createdAt ASC")
    suspend fun search(conversationId: String, query: String): List<MessageEntity>

    @Query("UPDATE messages SET syncStatus = :status, deliveryStatus = :deliveryStatus WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String, deliveryStatus: String)

    @Query("UPDATE messages SET mediaPath = :path WHERE id = :id")
    suspend fun updateMediaPath(id: String, path: String)

    @Query("UPDATE messages SET recalledAt = :time, recalledText = text, text = NULL, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun recall(id: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET readAt = :time, deliveryStatus = 'READ' WHERE id = :id")
    suspend fun markRead(id: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND createdAt > :since ORDER BY createdAt ASC")
    suspend fun since(conversationId: String, since: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun allForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun find(id: String): MessageEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun all(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)
}

@Dao
interface ParticipantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(participant: ParticipantEntity)

    @Query("SELECT * FROM participants WHERE conversationId = :conversationId")
    suspend fun findForConversation(conversationId: String): List<ParticipantEntity>

    @Query("SELECT * FROM participants WHERE conversationId = :conversationId")
    fun observeForConversation(conversationId: String): Flow<List<ParticipantEntity>>

    @Query("UPDATE participants SET analysisConsent = :consent, updatedAt = :time WHERE id = :id")
    suspend fun updateConsent(id: String, consent: Boolean, time: Long = System.currentTimeMillis())
}

@Dao
interface WeeklyReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: WeeklyReportEntity)

    @Query("SELECT * FROM weekly_reports WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun observeForConversation(conversationId: String): Flow<List<WeeklyReportEntity>>

    @Query("SELECT * FROM weekly_reports WHERE conversationId = :conversationId AND weekKey = :weekKey LIMIT 1")
    suspend fun find(conversationId: String, weekKey: String): WeeklyReportEntity?

    @Query("SELECT * FROM weekly_reports WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(conversationId: String): WeeklyReportEntity?
}

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observe(): Flow<PetEntity?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun get(): PetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pet: PetEntity)
}
