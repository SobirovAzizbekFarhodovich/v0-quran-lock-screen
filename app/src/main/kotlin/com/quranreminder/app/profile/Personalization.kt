package com.quranreminder.app.profile

import com.quranreminder.app.recitation.RecitationAlignment

/**
 * What the app changes for the reader's Quran level and goal (both from the profile).
 * One place, so every screen agrees.
 */
object Personalization {
    /** The goal to plan for: the one chosen, or the one the level suggests. */
    fun focus(profile: Profile?): String? = profile?.goal ?: when (profile?.quranLevel) {
        "beginner" -> "learn"
        "fluent" -> "understand"
        "hafiz" -> "memorize"
        else -> null
    }

    /** Beginners are learning the words first: harakat slips are not marked for them. */
    fun marksHarakat(profile: Profile?): Boolean = profile?.quranLevel != "beginner"

    /** Huffaz and those memorising start recitation with the ayah hidden. */
    fun recitationStartsInMemorize(profile: Profile?): Boolean =
        profile?.quranLevel == "hafiz" || profile?.goal == "memorize"

    /** Applies the settings that live outside the UI (the recitation checker). */
    fun apply(profile: Profile?) {
        RecitationAlignment.checkHarakat = marksHarakat(profile)
    }
}
