package com.quranreminder.app.lockscreen

import android.content.Context
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.database.QuranVerse

object LockScreenVerseSelector {
    suspend fun select(context: Context, database: QuranDatabase): QuranVerse? {
        val prefs = context.applicationContext
            .getSharedPreferences(LockScreenWallpaperApplier.PREFS, Context.MODE_PRIVATE)
        val selectedSurah = prefs.getInt(LockScreenWallpaperApplier.KEY_SELECTED_SURAH, -1)
        val selectedAyah = prefs.getInt(LockScreenWallpaperApplier.KEY_SELECTED_AYAH, -1)
        if (selectedSurah > 0 && selectedAyah > 0) {
            database.quranVerseDao().getVerseOnce(selectedSurah, selectedAyah)?.let { return it }
        }

        val selectedCategory = LockScreenWallpaperApplier.getSelectedCategory(context)
        if (selectedCategory != null) {
            database.quranVerseDao()
                .getAllVersesOnce()
                .filter { verse -> selectedCategory.matches(verse.translation) }
                .randomOrNull()
                ?.let { return it }
        }

        return database.quranVerseDao().getRandomVerse()
    }
}

fun LockScreenAyahCategory.matches(text: String): Boolean {
    val normalized = text.lowercase()
    return keywords.any { keyword -> normalized.contains(keyword) }
}
