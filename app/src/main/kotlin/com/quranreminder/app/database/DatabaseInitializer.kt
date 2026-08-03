package com.quranreminder.app.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseInitializer(private val context: Context) {
    suspend fun initializeDatabase(database: QuranDatabase) {
        withContext(Dispatchers.IO) {
            if (database.appSettingsDao().getSettingsCount() == 0) {
                database.appSettingsDao().insertSettings(AppSettings())
            }
        }
    }

}
