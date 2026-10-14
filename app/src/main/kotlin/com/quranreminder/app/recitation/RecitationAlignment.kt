package com.quranreminder.app.recitation

/** How one word of the ayah was read. */
enum class WordStatus {
    /** Read as written. */
    CORRECT,

    /** The right word, with a harakat read differently (final results only). */
    HARAKAT,

    /** Another word was heard in its place. */
    WRONG,

    /** Skipped (final results only). */
    MISSED,

    /** Not reached yet (live results only). */
    PENDING
}

data class WordMark(val status: WordStatus, val heard: String? = null, val slip: HarakatSlip? = null)

/** One word of the passage being recited, with the ayah it belongs to. */
data class TargetWord(val surah: Int, val ayah: Int, val text: String)

data class WordAlignment(
    /** One mark per target word, in order. */
    val marks: List<WordMark>,
    /** Words heard that match nothing in the ayah. */
    val extraWords: List<String>
) {
    val total: Int get() = marks.size
    val correct: Int get() = marks.count { it.status == WordStatus.CORRECT }
    val wrong: Int get() = marks.count { it.status == WordStatus.WRONG }
    val harakat: Int get() = marks.count { it.status == WordStatus.HARAKAT }
    val missed: Int get() = marks.count { it.status == WordStatus.MISSED }

    /** Target words already reached: the karaoke position while reciting. */
    val reached: Int get() = marks.indexOfLast { it.status != WordStatus.PENDING } + 1

    /** Share of the ayah's words read correctly, 0..100. */
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 + total / 2) / total
}

/**
 * Aligns what the recognizer heard with the words of the ayah, word by word, so the reader
 * sees which words were read, which were read wrongly and which were skipped.
 *
 * Words are compared on a letter skeleton that ignores harakat, Quranic marks and alef
 * spellings: the Uthmani script writes «ٱلصِّرَٰطَ», «ٱلرَّحْمَٰنِ» where plain Arabic has
 * «الصراط», «الرحمن», and that is not a reading mistake. A one-letter difference in a longer
 * word is also accepted, since it is far more often the recognizer's spelling than a mistake.
 */
object RecitationAlignment {
    /** False for beginners (see Personalization): harakat slips then count as correct. */
    @Volatile
    var checkHarakat: Boolean = true

    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0640\\u0670\\u06D6-\\u06ED]")

    /** Letters only: no harakat or marks, letter variants folded. Also used for display matching. */
    fun normalize(word: String): String = marks.replace(word, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace("ء", "")
        .filter { it in 'ء'..'ي' }

    /** [normalize] without alefs: Uthmani and plain spellings differ mostly in long-a alefs. */
    private fun skeleton(word: String): String = skeletonCache.getOrPut(word) { normalize(word).replace("ا", "") }

    // Words repeat a lot (and the same passage is re-aligned on every live partial).
    private val skeletonCache = object : LinkedHashMap<String, String>(4096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 40_000
    }

    fun sameWord(written: String, heard: String): Boolean = sameSkeleton(skeleton(written), skeleton(heard), written, heard)

    /**
     * Same word, allowing only what is spelling rather than reading: long-vowel letters
     * (و ي ى, alefs are already gone) and hamza seats. A consonant swapped, dropped or added
     * is a mistake — «يسعرون» for «يَشْعُرُونَ» (س for ش) is exactly the makhraj error a reader
     * needs to see, and a one-letter tolerance used to let it through.
     */
    private fun sameSkeleton(a: String, b: String, written: String, heard: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return normalize(written) == normalize(heard)
        if (a == b) return true
        return minOf(a.length, b.length) >= 3 && consonants(a) == consonants(b)
    }

    private fun consonants(skeleton: String): String = skeleton.filter { it != 'و' && it != 'ي' && it != 'ى' && it != 'ء' }

    fun words(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { normalize(it).isNotEmpty() }

    /**
     * [final] = false while the reader is still reciting: words after the last one heard are
     * PENDING, not MISSED, and the ayah's unread tail costs nothing.
     */
    fun align(target: List<String>, transcript: String, final: Boolean): WordAlignment {
        val heard = words(transcript)
        val n = target.size
        val m = heard.size
        if (n == 0) return WordAlignment(emptyList(), heard)
        // A long passage (a whole surah, or automatic mode following to the surah's end) is
        // aligned only as far as the reader can have got; the rest is pending, or missed.
        val reachable = m * 2 + 40
        if (n > reachable) {
            val head = alignWithin(target.take(reachable), heard, final = false)
            if (!final) return WordAlignment(head.marks + List(n - reachable) { WordMark(WordStatus.PENDING) }, head.extraWords)
            val upTo = minOf(n, head.reached + 20)
            val judged = alignWithin(target.take(upTo), heard, final = true)
            return WordAlignment(judged.marks + List(n - upTo) { WordMark(WordStatus.MISSED) }, judged.extraWords)
        }
        return alignWithin(target, heard, final)
    }

    private const val INF = Int.MAX_VALUE / 4

    /** Edit-distance alignment; banded around the diagonal when the table would be huge. */
    private fun alignWithin(target: List<String>, heard: List<String>, final: Boolean): WordAlignment {
        val n = target.size
        val m = heard.size
        val ts = Array(n) { skeleton(target[it]) }
        val hs = Array(m) { skeleton(heard[it]) }
        fun same(i: Int, j: Int) = sameSkeleton(ts[i], hs[j], target[i], heard[j])

        // Full table for ordinary sizes; a band of ±BAND words around the diagonal for a
        // whole long surah read through (Al-Baqara is ~6,100 words).
        val banded = (n + 1L) * (m + 1L) > 4_000_000L
        val band = if (banded) maxOf(BAND, kotlin.math.abs(n - m) + BAND) else m
        val lo = IntArray(n + 1) { i -> if (banded) maxOf(0, (i.toLong() * m / maxOf(n, 1)).toInt() - band) else 0 }
        val hi = IntArray(n + 1) { i -> if (banded) minOf(m, (i.toLong() * m / maxOf(n, 1)).toInt() + band) else m }
        val cost = Array(n + 1) { i -> IntArray(hi[i] - lo[i] + 1) { INF } }
        fun get(i: Int, j: Int): Int = if (j < lo[i] || j > hi[i]) INF else cost[i][j - lo[i]]
        for (j in lo[0]..hi[0]) cost[0][j - lo[0]] = j
        for (i in 1..n) {
            for (j in lo[i]..hi[i]) {
                var best = if (j == 0) i else INF
                if (j > 0) {
                    val diagonal = get(i - 1, j - 1)
                    if (diagonal < INF) best = minOf(best, diagonal + if (same(i - 1, j - 1)) 0 else 1)
                    val left = get(i, j - 1)
                    if (left < INF) best = minOf(best, left + 1)
                }
                val up = get(i - 1, j)
                if (up < INF) best = minOf(best, up + 1)
                cost[i][j - lo[i]] = best
            }
        }

        // Live: the reader may have stopped anywhere in the passage, so end where it is cheapest.
        var i = if (final) n else (0..n).filter { get(it, m) < INF }.minByOrNull { get(it, m).toLong() * (n + 1) + (n - it) } ?: n
        val reachedEnd = i
        var j = m
        val result = arrayOfNulls<WordMark>(n)
        val extras = ArrayList<String>()
        while (i > 0 || j > 0) {
            val here = get(i, j)
            when {
                i > 0 && j > 0 && get(i - 1, j - 1) < INF && here == get(i - 1, j - 1) + if (same(i - 1, j - 1)) 0 else 1 -> {
                    result[i - 1] = if (same(i - 1, j - 1)) {
                        // Harakat are judged only once the reader has finished: live partials
                        // are fast, rough transcriptions.
                        val slip = if (final && checkHarakat) HarakatCheck.slip(target[i - 1], heard[j - 1]) else null
                        if (slip != null) WordMark(WordStatus.HARAKAT, heard[j - 1], slip)
                        else WordMark(WordStatus.CORRECT, heard[j - 1])
                    } else {
                        WordMark(WordStatus.WRONG, heard[j - 1])
                    }
                    i--; j--
                }
                i > 0 && get(i - 1, j) < INF && here == get(i - 1, j) + 1 -> {
                    result[i - 1] = WordMark(WordStatus.MISSED)
                    i--
                }
                j > 0 -> {
                    extras.add(0, heard[j - 1])
                    j--
                }
                else -> {
                    result[i - 1] = WordMark(WordStatus.MISSED)
                    i--
                }
            }
        }
        val marks = List(n) { index ->
            if (index >= reachedEnd) WordMark(if (final) WordStatus.MISSED else WordStatus.PENDING)
            else result[index] ?: WordMark(WordStatus.MISSED)
        }
        return WordAlignment(marks, extras)
    }

    private const val BAND = 300

    private fun editDistance(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        for (x in 1..a.length) {
            var diagonal = row[0]
            row[0] = x
            for (y in 1..b.length) {
                val above = row[y]
                row[y] = minOf(row[y] + 1, row[y - 1] + 1, diagonal + if (a[x - 1] == b[y - 1]) 0 else 1)
                diagonal = above
            }
        }
        return row[b.length]
    }
}
