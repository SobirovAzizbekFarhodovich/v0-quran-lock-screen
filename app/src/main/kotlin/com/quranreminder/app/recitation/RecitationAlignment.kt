package com.quranreminder.app.recitation

/** How one word of the ayah was read. */
enum class WordStatus {
    /** Read as written. */
    CORRECT,

    /** Another word was heard in its place. */
    WRONG,

    /** Skipped (final results only). */
    MISSED,

    /** Not reached yet (live results only). */
    PENDING
}

data class WordMark(val status: WordStatus, val heard: String? = null)

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
    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0640\\u0670\\u06D6-\\u06ED]")

    /** Letters only: no harakat or marks, letter variants folded. Also used for display matching. */
    fun normalize(word: String): String = marks.replace(word, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace("ء", "")
        .filter { it in 'ء'..'ي' }

    /** [normalize] without alefs: Uthmani and plain spellings differ mostly in long-a alefs. */
    private fun skeleton(word: String): String = normalize(word).replace("ا", "")

    fun sameWord(written: String, heard: String): Boolean {
        val a = skeleton(written)
        val b = skeleton(heard)
        if (a.isEmpty() || b.isEmpty()) return normalize(written) == normalize(heard)
        if (a == b) return true
        return minOf(a.length, b.length) >= 4 && editDistance(a, b) <= 1
    }

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

        // cost[i][j]: aligning target[0 until i] with heard[0 until j].
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val substitute = cost[i - 1][j - 1] + if (sameWord(target[i - 1], heard[j - 1])) 0 else 1
                cost[i][j] = minOf(substitute, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
            }
        }

        // Live: the reader may have stopped anywhere in the ayah, so end where it is cheapest.
        var i = if (final) n else (0..n).minByOrNull { cost[it][m] * (n + 1) + (n - it) } ?: n
        val reachedEnd = i
        var j = m
        val result = arrayOfNulls<WordMark>(n)
        val extras = ArrayList<String>()
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + if (sameWord(target[i - 1], heard[j - 1])) 0 else 1 -> {
                    result[i - 1] = if (sameWord(target[i - 1], heard[j - 1])) {
                        WordMark(WordStatus.CORRECT, heard[j - 1])
                    } else {
                        WordMark(WordStatus.WRONG, heard[j - 1])
                    }
                    i--; j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> {
                    result[i - 1] = WordMark(WordStatus.MISSED)
                    i--
                }
                else -> {
                    extras.add(0, heard[j - 1])
                    j--
                }
            }
        }
        val marks = List(n) { index ->
            if (index >= reachedEnd) WordMark(if (final) WordStatus.MISSED else WordStatus.PENDING)
            else result[index] ?: WordMark(WordStatus.MISSED)
        }
        return WordAlignment(marks, extras)
    }

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
