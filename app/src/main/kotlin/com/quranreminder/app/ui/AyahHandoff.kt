package com.quranreminder.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One-shot hand-over from an ayah's action menu to a top-level tab. The tabs keep their plain
 * routes (the bottom bar matches on them), so the reader leaves the request here and the tab
 * takes it when it opens.
 */
object AyahHandoff {
    /** A question for the assistant, put into its input box (not sent). */
    var askText by mutableStateOf<String?>(null)

    /** Surah and ayah to open the recitation check on. */
    var recite by mutableStateOf<Pair<Int, Int>?>(null)
}
