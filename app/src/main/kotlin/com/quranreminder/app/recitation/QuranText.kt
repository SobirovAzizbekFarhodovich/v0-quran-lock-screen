package com.quranreminder.app.recitation

import android.content.Context
import com.google.gson.Gson
import com.quranreminder.app.database.QuranDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The whole Quran for recitation, built once per process from the on-device database (the
 * Arabic text is the same in every language) plus the Madinah mushaf's page of every ayah.
 */
object QuranText {
    private val mutex = Mutex()
    @Volatile private var cached: QuranLocator? = null
    @Volatile private var pages: IntArray? = null

    suspend fun locator(context: Context): QuranLocator = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.Default) {
            val verses = QuranDatabase.getDatabase(context).quranVerseDao().getAllVersesOnce()
            QuranLocator(verses.map { (it.surahNumber to it.ayahNumber) to it.ayahTextArabic })
        }.also { cached = it }
    }

    /** Page (1..604) of every ayah, by mushaf ordinal. */
    suspend fun pages(context: Context): IntArray = pages ?: withContext(Dispatchers.IO) {
        context.assets.open("quran/pages.json").bufferedReader().use { Gson().fromJson(it, IntArray::class.java) }
    }.also { pages = it }

    /** The ayahs printed on the page that holds [surah]:[ayah]. */
    fun pageAyahs(locator: QuranLocator, pages: IntArray, surah: Int, ayah: Int): Pair<Int, List<Pair<Int, Int>>> {
        val ordinal = locator.ordinalOf(surah, ayah) ?: return 0 to listOf(surah to ayah)
        val page = pages.getOrElse(ordinal) { 0 }
        var from = ordinal
        while (from > 0 && pages.getOrElse(from - 1) { -1 } == page) from--
        var to = ordinal
        while (to + 1 < locator.ayahs.size && pages.getOrElse(to + 1) { -1 } == page) to++
        return page to locator.ayahs.subList(from, to + 1)
    }
}
