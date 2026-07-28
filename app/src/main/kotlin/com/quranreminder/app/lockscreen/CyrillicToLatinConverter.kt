package com.quranreminder.app.lockscreen

object CyrillicToLatinConverter {
    private val replacements = listOf(
        "Ўз" to "Oʻz",
        "Ё" to "Yo", "ё" to "yo",
        "Ю" to "Yu", "ю" to "yu",
        "Я" to "Ya", "я" to "ya",
        "Ч" to "Ch", "ч" to "ch",
        "Ш" to "Sh", "ш" to "sh",
        "Щ" to "Shch", "щ" to "shch",
        "Ц" to "Ts", "ц" to "ts",
        "Ғ" to "Gʻ", "ғ" to "gʻ",
        "Қ" to "Q", "қ" to "q",
        "Ҳ" to "H", "ҳ" to "h",
        "Ў" to "Oʻ", "ў" to "oʻ",
        "Ъ" to "ʼ", "ъ" to "ʼ",
        "А" to "A", "а" to "a",
        "Б" to "B", "б" to "b",
        "В" to "V", "в" to "v",
        "Г" to "G", "г" to "g",
        "Д" to "D", "д" to "d",
        "Е" to "E", "е" to "e",
        "Ж" to "J", "ж" to "j",
        "З" to "Z", "з" to "z",
        "И" to "I", "и" to "i",
        "Й" to "Y", "й" to "y",
        "К" to "K", "к" to "k",
        "Л" to "L", "л" to "l",
        "М" to "M", "м" to "m",
        "Н" to "N", "н" to "n",
        "О" to "O", "о" to "o",
        "П" to "P", "п" to "p",
        "Р" to "R", "р" to "r",
        "С" to "S", "с" to "s",
        "Т" to "T", "т" to "t",
        "У" to "U", "у" to "u",
        "Ф" to "F", "ф" to "f",
        "Х" to "X", "х" to "x",
        "Э" to "E", "э" to "e",
        "Ь" to "", "ь" to ""
    )

    fun convert(text: String): String {
        var result = text
        for ((cyr, lat) in replacements) {
            result = result.replace(cyr, lat)
        }
        return result
    }
}
