package com.quranreminder.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuranVerse::class, Reminder::class, Bookmark::class, AppSettings::class, RecitationResultEntity::class],
    version = 5,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranVerseDao(): QuranVerseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun recitationHistoryDao(): RecitationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null

        /** Adds verse footnotes. Bookmarks, reminders and settings are kept (no destructive reset). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quran_verses ADD COLUMN footnotes TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Recitation history, kept on the device. Everything else is untouched. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recitation_results` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surahNumber` INTEGER NOT NULL, " +
                        "`ayahNumber` INTEGER NOT NULL, `wordCount` INTEGER NOT NULL, `correctCount` INTEGER NOT NULL, " +
                        "`wrongCount` INTEGER NOT NULL, `missedCount` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `day` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitation_results_surahNumber_ayahNumber` ON `recitation_results` (`surahNumber`, `ayahNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitation_results_day` ON `recitation_results` (`day`)")
            }
        }

        /** Recitation history gets a client id and a synced flag for the server backup. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recitation_results ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recitation_results ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                // Existing rows get random ids (UUID-shaped hex) before the unique index exists.
                db.execSQL(
                    "UPDATE recitation_results SET clientId = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || " +
                        "hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6)))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recitation_results_clientId` ON `recitation_results` (`clientId`)")
            }
        }

        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .addCallback(RoomDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class RoomDatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Database creation callback - add initial data if needed
        }
    }
}
