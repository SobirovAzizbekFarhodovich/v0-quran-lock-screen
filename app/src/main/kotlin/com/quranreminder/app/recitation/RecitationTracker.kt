package com.quranreminder.app.recitation

/**
 * Follows one recitation from the transcripts the server sends: which words of the Quran
 * are being read and how each was read.
 *
 * - Chosen passage: the words of the chosen ayah (or the whole surah) are known up front.
 * - Automatic: nothing is known until [QuranLocator] recognises the place from the first
 *   words; from then on the passage runs from that ayah to the end of the surah, so the
 *   reader can go on ayah after ayah. The final result is cut after the last ayah reached.
 */
class RecitationTracker(
    private val locator: QuranLocator,
    /** Index of the first word of the chosen passage, or null for automatic. */
    private val chosenStart: Int?,
    /** Index after the last word of the chosen passage. */
    private val chosenEnd: Int?
) {
    /** Index (in [QuranLocator.words]) of the passage's first word; null until located. */
    var start: Int? = chosenStart
        private set
    var end: Int? = chosenEnd
        private set
    var alignment: WordAlignment? = null
        private set
    /** Words of the passage, in order (empty until located). */
    val passage: List<QuranWord>
        get() {
            val s = start ?: return emptyList()
            val e = end ?: return emptyList()
            return locator.words.subList(s, e)
        }

    val located: Boolean get() = start != null

    /** Feeds a live (final = false) or final transcript. */
    fun update(transcript: String, final: Boolean): WordAlignment? {
        val heard = RecitationAlignment.words(transcript)
        var spoken = heard
        if (chosenStart == null) {
            val preamble = locator.preambleLength(heard)
            if (start == null) {
                val at = locator.locate(heard.drop(preamble)) ?: run {
                    alignment = null
                    return null
                }
                var first = at.wordIndex
                // Al-Fatiha's basmala is its first ayah: keep it when the reading goes on with 1:2.
                if (at.surah == 1 && at.ayah == 2 && preamble >= 4) first = locator.firstWordOf(1, 1) ?: first
                start = first
                end = minOf(locator.endOfSurah(at.surah), first + MAX_FOLLOW_WORDS)
            }
            val basmalaKept = start == locator.firstWordOf(1, 1)
            spoken = heard.drop(if (basmalaKept) maxOf(0, preamble - 4) else preamble)
        }
        val words = passage.map { it.text }
        if (words.isEmpty()) return null
        var result = RecitationAlignment.align(words, spoken.joinToString(" "), final = false)
        if (final) {
            if (chosenStart == null) {
                // Automatic: the reader chose where to stop. Keep the ayahs reached, through
                // the end of the last one, and judge only those.
                val reached = result.reached.coerceAtLeast(1)
                val lastAyah = passage[reached - 1]
                var cut = reached
                while (cut < passage.size && passage[cut].surah == lastAyah.surah && passage[cut].ayah == lastAyah.ayah) cut++
                end = start!! + cut
            }
            result = RecitationAlignment.align(passage.map { it.text }, spoken.joinToString(" "), final = true)
        }
        alignment = result
        return result
    }

    /** The word the reader is on (for the ayah and page shown), as an index into [passage]. */
    fun currentIndex(): Int {
        val size = passage.size
        if (size == 0) return 0
        return (alignment?.reached ?: 0).coerceIn(0, size - 1)
    }

    /** Per-ayah results of the final alignment, in reading order. */
    fun ayahResults(): List<AyahResult> {
        val marks = alignment?.marks ?: return emptyList()
        val words = passage
        if (marks.size != words.size) return emptyList()
        return words.indices.groupBy { words[it].surah to words[it].ayah }.map { (key, indexes) ->
            val statuses = indexes.map { marks[it].status }
            AyahResult(
                surah = key.first,
                ayah = key.second,
                words = statuses.size,
                correct = statuses.count { it == WordStatus.CORRECT },
                wrong = statuses.count { it == WordStatus.WRONG || it == WordStatus.HARAKAT },
                missed = statuses.count { it == WordStatus.MISSED }
            )
        }
    }

    companion object {
        /** How far ahead automatic mode follows (a long surah is trimmed to what was read). */
        const val MAX_FOLLOW_WORDS = 1500

        /** Chosen ayah (or whole surah when [fullSurah]). */
        fun chosen(locator: QuranLocator, surah: Int, ayah: Int, fullSurah: Boolean): RecitationTracker? {
            val start = locator.firstWordOf(surah, if (fullSurah) 1 else ayah) ?: return null
            val end = if (fullSurah) locator.endOfSurah(surah) else (locator.firstWordOf(surah, ayah + 1) ?: locator.endOfSurah(surah))
            return RecitationTracker(locator, start, end)
        }

        fun automatic(locator: QuranLocator) = RecitationTracker(locator, null, null)
    }
}

data class AyahResult(val surah: Int, val ayah: Int, val words: Int, val correct: Int, val wrong: Int, val missed: Int)
