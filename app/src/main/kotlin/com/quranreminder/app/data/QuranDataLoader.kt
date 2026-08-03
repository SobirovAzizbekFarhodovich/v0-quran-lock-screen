package com.quranreminder.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.database.QuranVerse

data class QuranAssetData(
    @SerializedName("verses")
    val verses: List<QuranVerseDto> = emptyList(),
    @SerializedName("result")
    val quranEncResult: List<QuranEncVerseDto> = emptyList()
)

data class QuranVerseDto(
    @SerializedName("surahNumber")
    val surahNumber: Int,
    @SerializedName("surahName")
    val surahName: String,
    @SerializedName("surahNameArabic")
    val surahNameArabic: String,
    @SerializedName("ayahNumber")
    val ayahNumber: Int,
    @SerializedName("ayahText")
    val ayahText: String = "",
    @SerializedName("ayahTextArabic")
    val ayahTextArabic: String,
    @SerializedName("translation")
    val translation: String,
    @SerializedName("transliteration")
    val transliteration: String = ""
) {
    fun toEntity(): QuranVerse {
        return QuranVerse(
            surahNumber = surahNumber,
            surahName = surahName,
            surahNameArabic = surahNameArabic,
            ayahNumber = ayahNumber,
            ayahText = ayahText.ifBlank { ayahTextArabic },
            ayahTextArabic = ayahTextArabic,
            translation = translation,
            transliteration = transliteration
        )
    }
}

object QuranDataLoader {

    fun loadFromServer(language: String): List<QuranVerse> {
        return runCatching {
            val encodedLanguage = java.net.URLEncoder.encode(language, "UTF-8")
            val url = java.net.URL("${BuildConfig.SERVER_BASE_URL}/api/quran/verses?language=$encodedLanguage")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                android.util.Log.e("QuranDataLoader", "Failed to fetch verses, response code: ${connection.responseCode}")
                return@runCatching emptyList<QuranVerse>()
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val data = Gson().fromJson(json, QuranAssetData::class.java)
            val verses = if (data.verses.isNotEmpty()) {
                data.verses.map { it.toEntity() }
            } else {
                data.quranEncResult.map { it.toEntity() }
            }
            verses.filter { verse ->
                verse.surahNumber in 1..114 &&
                    verse.ayahNumber > 0 &&
                    verse.translation.isNotBlank()
            }
        }.onFailure { e ->
            android.util.Log.e("QuranDataLoader", "Exception while fetching verses", e)
        }.getOrElse { emptyList() }
    }
}

data class QuranEncVerseDto(
    @SerializedName("sura")
    val surahNumber: String,
    @SerializedName("aya")
    val ayahNumber: String,
    @SerializedName("arabic_text")
    val arabicText: String,
    @SerializedName("translation")
    val translation: String
) {
    fun toEntity(): QuranVerse {
        val surah = surahNumber.toIntOrNull() ?: 1
        return QuranVerse(
            surahNumber = surah,
            surahName = SurahNames.uzbekLatin[surah - 1],
            surahNameArabic = SurahNames.arabic[surah - 1],
            ayahNumber = ayahNumber.toIntOrNull() ?: 1,
            ayahText = arabicText,
            ayahTextArabic = arabicText,
            translation = translation,
            transliteration = ""
        )
    }
}

private object SurahNames {
    val uzbekLatin = listOf(
        "Fotiha", "Baqara", "Oli Imron", "Niso", "Moida", "An'om", "A'rof",
        "Anfol", "Tavba", "Yunus", "Hud", "Yusuf", "Ra'd", "Ibrohim", "Hijr",
        "Nahl", "Isro", "Kahf", "Maryam", "Toha", "Anbiyo", "Haj", "Mo'minun",
        "Nur", "Furqon", "Shuaro", "Naml", "Qasas", "Ankabut", "Rum", "Luqmon",
        "Sajda", "Ahzob", "Saba", "Fotir", "Yosin", "Soffot", "Sod", "Zumar",
        "G'ofir", "Fussilat", "Shuro", "Zuxruf", "Duxon", "Josiya", "Ahqof",
        "Muhammad", "Fath", "Hujurot", "Qof", "Zoriyot", "Tur", "Najm", "Qamar",
        "Rahmon", "Voqea", "Hadid", "Mujodala", "Hashr", "Mumtahana", "Saff",
        "Juma", "Munofiqun", "Tag'obun", "Taloq", "Tahrim", "Mulk", "Qalam",
        "Haqqa", "Maorij", "Nuh", "Jin", "Muzzammil", "Muddassir", "Qiyomat",
        "Inson", "Mursalot", "Naba", "Noziot", "Abasa", "Takvir", "Infitor",
        "Mutoffifin", "Inshiqoq", "Buruj", "Toriq", "A'lo", "G'oshiya", "Fajr",
        "Balad", "Shams", "Layl", "Zuho", "Sharh", "Tiyn", "Alaq", "Qadr",
        "Bayyina", "Zalzala", "Odiyot", "Qoria", "Takosur", "Asr", "Humaza",
        "Fil", "Quraysh", "Mo'un", "Kavsar", "Kofirun", "Nasr", "Masad", "Ixlos",
        "Falaq", "Nos"
    )

    val arabic = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف",
        "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد", "ابراهيم", "الحجر",
        "النحل", "الإسراء", "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون",
        "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم", "لقمان",
        "السجدة", "الأحزاب", "سبإ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح",
        "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة",
        "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون",
        "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج", "نوح",
        "الجن", "المزمل", "المدثر", "القيامة", "الانسان", "المرسلات", "النبإ",
        "النازعات", "عبس", "التكوير", "الإنفطار", "المطففين", "الإنشقاق", "البروج",
        "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى",
        "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون",
        "الكوثر", "الكافرون", "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
    )
}
