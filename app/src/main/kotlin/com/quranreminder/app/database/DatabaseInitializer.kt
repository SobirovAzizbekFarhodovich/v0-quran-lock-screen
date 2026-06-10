package com.quranreminder.app.database

import android.content.Context
import com.quranreminder.app.data.QuranSampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseInitializer(private val context: Context) {
    suspend fun initializeDatabase(database: QuranDatabase) {
        withContext(Dispatchers.IO) {
            val verseCount = database.quranVerseDao().getVerseCount()
            
            // Only initialize if database is empty
            if (verseCount == 0) {
                val sampleVerses = QuranSampleData.getSampleVerses()
                database.quranVerseDao().insertVerses(sampleVerses)
                
                // Initialize default settings
                val defaultSettings = AppSettings(
                    id = 1,
                    notificationsEnabled = true,
                    vibrateOnReminder = true,
                    soundEnabled = true,
                    fontSize = 16,
                    darkMode = false,
                    lockScreenNotifications = true
                )
                database.appSettingsDao().insertSettings(defaultSettings)
            }
        }
    }

    companion object {
        suspend fun loadFullQuranFromAssets(context: Context, database: QuranDatabase) {
            withContext(Dispatchers.IO) {
                try {
                    // Try to load from assets/quran.json if it exists
                    val assetManager = context.assets
                    val files = assetManager.list("quran") ?: emptyArray()
                    
                    if (files.isNotEmpty()) {
                        // Load verse data from JSON file
                        // This is a placeholder - implement based on your JSON structure
                        val jsonString = assetManager.open("quran/verses.json").bufferedReader().use { it.readText() }
                        // Parse and insert verses
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
