package com.quranreminder.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuranVerse::class, Reminder::class, Bookmark::class, AppSettings::class],
    version = 3,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranVerseDao(): QuranVerseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null

        /** Adds verse footnotes. Bookmarks, reminders and settings are kept (no destructive reset). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quran_verses ADD COLUMN footnotes TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_database"
                )
                    .addMigrations(MIGRATION_2_3)
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
