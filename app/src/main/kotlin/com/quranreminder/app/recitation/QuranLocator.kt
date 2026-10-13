package com.quranreminder.app.recitation

/** One word of the Quran in reading order. */
data class QuranWord(val surah: Int, val ayah: Int, val text: String)

/** Where the reader is: the ayah they started from and its first word's index in [QuranLocator.words]. */
data class QuranLocation(val surah: Int, val ayah: Int, val wordIndex: Int, val matchedWords: Int)

/**
 * Finds which ayah is being recited from the first words heard, so the reader can just start
 * reading instead of typing a surah and ayah number.
 *
 * Every three-word run of the Quran is indexed by its letter skeleton; the words heard are
 * looked up and each candidate scored word by word. A place is reported only when it is
 * clearly better than every other one: a phrase that occurs in many places (e.g. «فَبِأَيِّ
 * ءَالَآءِ رَبِّكُمَا تُكَذِّبَانِ») waits for more words. The isti'adha and the basmala said before
 * reading are set aside.
 */
class QuranLocator(verses: List<Pair<Pair<Int, Int>, String>>) {
    val words: List<QuranWord>
    private val skeletons: List<String>
    private val ayahFirstWord = HashMap<Pair<Int, Int>, Int>()
    private val trigrams = HashMap<String, MutableList<Int>>()

    init {
        val list = ArrayList<QuranWord>(80_000)
        verses.sortedWith(compareBy({ it.first.first }, { it.first.second })).forEach { (key, text) ->
            ayahFirstWord[key] = list.size
            RecitationAlignment.words(text).forEach { list.add(QuranWord(key.first, key.second, it)) }
        }
        words = list
        skeletons = list.map { skeleton(it.text) }
        for (i in 0 until skeletons.size - 2) {
            trigrams.getOrPut(trigramKey(skeletons[i], skeletons[i + 1], skeletons[i + 2])) { ArrayList(2) }.add(i)
        }
    }

    fun firstWordOf(surah: Int, ayah: Int): Int? = ayahFirstWord[surah to ayah]

    /** Every ayah in mushaf order (index = ordinal, as in assets/quran/pages.json). */
    val ayahs: List<Pair<Int, Int>> by lazy { ayahFirstWord.keys.sortedWith(compareBy({ it.first }, { it.second })) }
    private val ordinals: Map<Pair<Int, Int>, Int> by lazy { ayahs.withIndex().associate { it.value to it.index } }
    fun ordinalOf(surah: Int, ayah: Int): Int? = ordinals[surah to ayah]

    /** Index after the last word of the given ayah. */
    fun endOfAyah(surah: Int, ayah: Int): Int = firstWordOf(surah, ayah + 1) ?: endOfSurah(surah)

    /** Index of the word after the last word of [surah]. */
    fun endOfSurah(surah: Int): Int {
        var i = ayahFirstWord[surah to 1] ?: return words.size
        while (i < words.size && words[i].surah == surah) i++
        return i
    }

    /**
     * How many leading words of [heard] are the isti'adha or a basmala said before reading
     * (not counted as part of the recitation).
     */
    fun preambleLength(heard: List<String>): Int {
        var at = 0
        for (phrase in PREAMBLES) {
            if (heard.size - at >= phrase.size && phrase.indices.all { skeleton(heard[at + it]) == phrase[it] }) {
                at += phrase.size
            }
        }
        return at
    }

    /** The ayah being read, or null while it is not yet clear. [heard] excludes the preamble. */
    fun locate(heard: List<String>): QuranLocation? {
        val probe = heard.take(PROBE_WORDS).map(::skeleton)
        if (probe.size < MIN_MATCHED) return null
        val scores = HashMap<Int, Int>()
        // The first word or two may be garbled: also try runs starting one or two words later.
        for (shift in 0..minOf(2, probe.size - 3)) {
            val key = trigramKey(probe[shift], probe[shift + 1], probe[shift + 2])
            trigrams[key]?.forEach { position ->
                val start = position - shift
                if (start >= 0 && start !in scores) scores[start] = score(start, probe)
            }
        }
        if (scores.isEmpty()) return null
        // Readers start at the beginning of an ayah: prefer such places when scores tie.
        val ranked = scores.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenByDescending { isAyahStart(it.key) }
        )
        val best = ranked.first()
        if (best.value < MIN_MATCHED) return null
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && runnerUp.value == best.value && isAyahStart(runnerUp.key) == isAyahStart(best.key)) return null
        // Snap back to the start of the ayah the match falls in.
        val word = words[best.key]
        val first = ayahFirstWord[word.surah to word.ayah] ?: best.key
        return QuranLocation(word.surah, word.ayah, first, best.value)
    }

    private fun isAyahStart(index: Int): Boolean {
        val w = words[index]
        return ayahFirstWord[w.surah to w.ayah] == index
    }

    private fun score(start: Int, probe: List<String>): Int {
        var matched = 0
        for (j in probe.indices) {
            val i = start + j
            if (i >= skeletons.size) break
            if (similar(skeletons[i], probe[j])) matched++
        }
        return matched
    }

    private fun similar(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 4 && kotlin.math.abs(a.length - b.length) <= 1 && commonPrefix(a, b) >= minOf(a.length, b.length) - 1)

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    companion object {
        private const val PROBE_WORDS = 8
        private const val MIN_MATCHED = 3

        private fun skeleton(word: String): String = RecitationAlignment.normalize(word).replace("ا", "")
        private fun trigramKey(a: String, b: String, c: String) = "$a $b $c"

        private val PREAMBLES = listOf(
            "أعوذ بالله من الشيطان الرجيم",
            "بسم الله الرحمن الرحيم"
        ).map { phrase -> phrase.split(" ").map(::skeleton) }
    }
}
