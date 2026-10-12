package com.quranreminder.app.recitation

/** A vowel read differently inside a word: «X» with a zer where the mushaf has a zabar. */
data class HarakatSlip(
    /** The letter (as written, hamza forms unified to ء). */
    val letter: Char,
    /** 1-based position of the letter in the word. */
    val position: Int,
    val expected: Vowel,
    val heard: Vowel
)

enum class Vowel { FATHA, KASRA, DAMMA, SHADDA, NO_SHADDA }

/**
 * Compares the harakat the recognizer heard with the mushaf's, letter by letter, for words
 * whose letters already match.
 *
 * Measured on 499 words by three Hafs reciters (Tarteel's whisper-base-ar-quran, VAD off):
 * with these rules there were no false alarms, and on Warsh recitations it caught 16 of 18
 * real vowel differences (it writes what it hears, not the memorised text). The rules:
 * - a word's last letter is never judged: at a pause it is read with sukun, while the model
 *   writes the full grammatical ending, and a doubled letter can move across a word boundary;
 * - a letter the mushaf leaves unmarked is not judged (Uthmani omits marks on merged letters);
 * - sukun and "no mark" are the same; a letter written bare in the transcript is not judged;
 * - shadda on a word's first letter comes from merging with the previous word: ignored.
 * Uthmani spellings (dagger alef, small waw/yeh, maddah, pause signs, …) are normalised away.
 */
object HarakatCheck {
    private const val FATHA = 'َ'
    private const val DAMMA = 'ُ'
    private const val KASRA = 'ِ'
    private const val SUKUN = 'ْ'
    private const val FATHATAN = 'ً'
    private const val DAMMATAN = 'ٌ'
    private const val KASRATAN = 'ٍ'
    private const val SHADDA = 'ّ'
    private const val DAGGER_ALEF = 'ٰ'
    private const val HAMZA_ABOVE = 'ٔ'
    private const val HAMZA_BELOW = 'ٕ'

    private data class Unit(var letter: Char, var vowel: Char?, var shadda: Boolean)

    private fun isDropped(c: Char): Boolean {
        val code = c.code
        return code in 0x0610..0x061A || // honorifics and small signs
            code in 0x06D6..0x06DE || c == '۩' || // pause signs, sajdah
            c == 'ٓ' || c == 'ٟ' || // maddah, wavy hamza
            c == '۟' || c == '۠' || // silent-letter zeros
            c == 'ۥ' || c == 'ۦ' || // small waw / yeh
            c == 'ۢ' || c == 'ۭ' || // small meem
            c == 'ۜ' || c == 'ۣ' || c == 'ۨ' || // small seen / noon
            c == '۪' || c == '۫' || c == '۬' ||
            c == 'ـ' // tatweel
    }

    private fun vowelOf(c: Char): Char? = when (c) {
        FATHA, FATHATAN, 'ࣰ' -> FATHA
        DAMMA, DAMMATAN, 'ࣱ' -> DAMMA
        KASRA, KASRATAN, 'ࣲ' -> KASRA
        SUKUN, 'ۡ' -> SUKUN
        else -> null
    }

    private fun units(word: String): List<Unit> {
        val text = word.replace("ىٰ", "ا").replace("وٰ", "ا")
        val out = ArrayList<Unit>()
        for (c in text) {
            if (isDropped(c) || c == DAGGER_ALEF) continue
            if (c == HAMZA_ABOVE || c == HAMZA_BELOW) {
                val last = out.lastOrNull()
                if (last != null && last.letter in "اىيو") last.letter = 'ء' else out.add(Unit('ء', null, false))
                continue
            }
            val vowel = vowelOf(c)
            if (vowel != null) {
                out.lastOrNull()?.let { if (it.vowel == null) it.vowel = vowel }
                continue
            }
            if (c == SHADDA) {
                out.lastOrNull()?.shadda = true
                continue
            }
            when (c) {
                'ا', 'ٱ' -> out.add(Unit('A', null, false)) // long-vowel alef, usually dropped below
                'آ' -> out.add(Unit('ء', FATHA, false))
                'أ', 'إ', 'ؤ', 'ئ', 'ء' -> out.add(Unit('ء', null, false))
                'ى' -> out.add(Unit('Y', null, false))
                'ة' -> out.add(Unit('ه', null, false))
                else -> if (c in 'ء'..'ي') out.add(Unit(c, null, false))
            }
        }
        // Long-vowel alefs and a sounded-as-"a" alef maqsura carry no harakat of their own.
        val kept = out.filterIndexed { index, unit ->
            when (unit.letter) {
                'A' -> unit.vowel != null || unit.shadda
                'Y' -> unit.vowel != null || unit.shadda || (index > 0 && out[index - 1].vowel == KASRA)
                else -> true
            }
        }
        kept.forEach {
            if (it.letter == 'A') it.letter = 'ا'
            if (it.letter == 'Y') it.letter = 'ي'
        }
        return kept
    }

    private fun Char.toVowel(): Vowel = when (this) {
        FATHA -> Vowel.FATHA
        KASRA -> Vowel.KASRA
        else -> Vowel.DAMMA
    }

    /** The first harakat slip in [heard] against the mushaf's [written], or null. */
    fun slip(written: String, heard: String): HarakatSlip? {
        val ref = units(written)
        val hyp = units(heard)
        if (ref.size != hyp.size || ref.indices.any { ref[it].letter != hyp[it].letter }) return null
        for (i in 0 until ref.size - 1) { // never the last letter
            // A letter the recognizer wrote bare (plain spelling) says nothing about how it was read.
            if (hyp[i].vowel == null && !hyp[i].shadda) continue
            val expected = ref[i].vowel?.takeIf { it != SUKUN }
            val got = hyp[i].vowel?.takeIf { it != SUKUN }
            if (expected != null && got != null && expected != got) {
                return HarakatSlip(ref[i].letter, i + 1, expected.toVowel(), got.toVowel())
            }
            if (i > 0 && ref[i].shadda != hyp[i].shadda) {
                return HarakatSlip(
                    ref[i].letter, i + 1,
                    if (ref[i].shadda) Vowel.SHADDA else Vowel.NO_SHADDA,
                    if (hyp[i].shadda) Vowel.SHADDA else Vowel.NO_SHADDA
                )
            }
        }
        return null
    }
}
