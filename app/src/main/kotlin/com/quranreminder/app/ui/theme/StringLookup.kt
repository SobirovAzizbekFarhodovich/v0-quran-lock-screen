package com.quranreminder.app.ui.theme

/**
 * Non-composable lookup for code that runs outside Compose (repositories, clients).
 * Same fallback chain as [t]: requested language, then Uzbek Latin, then the key.
 */
fun AppStrings.text(language: String?, key: String): String =
    forLanguage(language.orEmpty())[key] ?: uzLatin[key] ?: key

/** Looks [key] up in an already-resolved strings map (e.g. `LocalAppStrings.current`). */
fun Map<String, String>.text(key: String): String = this[key] ?: AppStrings.uzLatin[key] ?: key
