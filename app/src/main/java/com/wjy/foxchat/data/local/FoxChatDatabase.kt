package com.wjy.foxchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MediaAttachmentEntity::class,
        WeeklyReportEntity::class,
        ParticipantEntity::class,
        OutboxEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FoxChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun mediaDao(): MediaAttachmentDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun participantDao(): ParticipantDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var instance: FoxChatDatabase? = null

        fun get(context: Context): FoxChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoxChatDatabase::class.java,
                    "foxchat.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN recalledText TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participants ADD COLUMN avatar TEXT")
            }
        }
    }
}

@androidx.room.Dao
interface MediaAttachmentDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: MediaAttachmentEntity)

    @androidx.room.Query("DELETE FROM media_attachments WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)
}
