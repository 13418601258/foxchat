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
        OutboxEntity::class,
        PetEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FoxChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun mediaDao(): MediaAttachmentDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun participantDao(): ParticipantDao
    abstract fun outboxDao(): OutboxDao
    abstract fun petDao(): PetDao

    companion object {
        @Volatile private var instance: FoxChatDatabase? = null

        fun get(context: Context): FoxChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoxChatDatabase::class.java,
                    "foxchat.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pet` (`id` INTEGER NOT NULL, `food` INTEGER NOT NULL, `drink` INTEGER NOT NULL, `condition` REAL NOT NULL, `love` INTEGER NOT NULL, `days` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
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
