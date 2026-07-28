package com.quranreminder.app.database

import android.content.Context
import com.quranreminder.app.data.QuranDataLoader
import com.quranreminder.app.data.QuranSampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseInitializer(private val context: Context) {
    suspend fun initializeDatabase(database: QuranDatabase) {
        withContext(Dispatchers.IO) {
            val verseCount = database.quranVerseDao().getVerseCount()
            
            val serverVerses = QuranDataLoader.loadFromServer()
            if (verseCount == 0 || (serverVerses.size >= FULL_QURAN_VERSE_COUNT && verseCount < FULL_QURAN_VERSE_COUNT)) {
                val verses = serverVerses.ifEmpty { QuranSampleData.getSampleVerses() }
                database.quranVerseDao().insertVerses(verses)

            }

            if (database.appSettingsDao().getSettingsCount() == 0) {
                database.appSettingsDao().insertSettings(AppSettings())
            }
        }
    }

    private companion object {
        const val FULL_QURAN_VERSE_COUNT = 6236
    }
}
