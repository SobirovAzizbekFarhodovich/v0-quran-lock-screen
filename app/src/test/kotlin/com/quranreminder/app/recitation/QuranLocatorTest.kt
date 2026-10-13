package com.quranreminder.app.recitation

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class QuranLocatorTest {
    companion object {
        // The bundled Quran (Uthmani text), read once for every test.
        val locator: QuranLocator by lazy {
            val root = JsonParser.parseReader(File("src/main/assets/quran/uz_latin.json").reader()).asJsonObject
            val verses = root.getAsJsonArray("verses").map { it.asJsonObject }.map {
                (it["surahNumber"].asInt to it["ayahNumber"].asInt) to it["ayahTextArabic"].asString
            }
            QuranLocator(verses)
        }
    }

    private fun locate(text: String): QuranLocation? {
        val heard = RecitationAlignment.words(text)
        return locator.locate(heard.drop(locator.preambleLength(heard)))
    }

    @Test fun fatihaFromItsSecondAyah() {
        val at = locate("الحمد لله رب العالمين الرحمن الرحيم")!!
        assertEquals(1 to 2, at.surah to at.ayah)
    }

    @Test fun istiadhaAndBasmalaAreSetAside() {
        val heard = RecitationAlignment.words("أعوذ بالله من الشيطان الرجيم بسم الله الرحمن الرحيم قل هو الله أحد")
        assertEquals(9, locator.preambleLength(heard))
        val at = locate("أعوذ بالله من الشيطان الرجيم بسم الله الرحمن الرحيم قل هو الله أحد")!!
        assertEquals(112 to 1, at.surah to at.ayah)
    }

    @Test fun ayatAlKursiWaitsUntilItDiffersFromAalImran() {
        // «اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ» is all of 3:2 and the start of 2:255.
        assertNull(locate("الله لا إله إلا هو الحي القيوم"))
        val at = locate("الله لا إله إلا هو الحي القيوم لا تأخذه")!!
        assertEquals(2 to 255, at.surah to at.ayah)
    }

    @Test fun aRefrainFoundInManyPlacesIsNotGuessed() {
        assertNull(locate("فبأي آلاء ربكما تكذبان"))
    }

    @Test fun readingFromTheMiddleOfAnAyahSnapsToItsStart() {
        val at = locate("الذي خلق الموت والحياة ليبلوكم أيكم أحسن عملا")!!
        assertEquals(67 to 2, at.surah to at.ayah)
        assertEquals(locator.firstWordOf(67, 2), at.wordIndex)
    }

    @Test fun recognizerSpellingWithHarakatIsFound() {
        val at = locate("تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ")!!
        assertEquals(67 to 1, at.surah to at.ayah)
    }

    @Test fun tooFewWordsWait() {
        assertNull(locate("قل هو"))
    }
}
