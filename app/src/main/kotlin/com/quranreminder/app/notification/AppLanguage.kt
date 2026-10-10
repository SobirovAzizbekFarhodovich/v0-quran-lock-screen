package com.quranreminder.app.notification

import android.content.Context
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.lockscreen.LockScreenWallpaperApplier
import com.quranreminder.app.ui.theme.AppStrings
import kotlinx.coroutines.flow.first

/**
 * Resolves UI text for code that runs outside Compose (receivers, workers, sharing).
 * The canonical language is [com.quranreminder.app.database.AppSettings.language]; the
 * lock-screen language pref (kept in sync on language selection) is the fallback for the
 * window before settings are written.
 */
object AppLanguage {
    suspend fun current(context: Context): String {
        val fromSettings = runCatching {
            QuranDatabase.getDatabase(context).appSettingsDao().getSettings().first()?.language
        }.getOrNull()
        return fromSettings?.takeIf { it.isNotBlank() }
            ?: LockScreenWallpaperApplier.getLockScreenLanguage(context)
    }

    fun text(language: String, key: String): String =
        AppStrings.forLanguage(language)[key] ?: AppStrings.uzLatin[key] ?: key
}
