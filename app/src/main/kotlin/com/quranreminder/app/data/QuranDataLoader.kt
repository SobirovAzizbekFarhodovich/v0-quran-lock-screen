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
    val transliteration: String = "",
    @SerializedName("footnotes")
    val footnotes: String? = null
) {
    fun toEntity(): QuranVerse {
        return QuranVerse(
            surahNumber = surahNumber,
            surahName = surahName,
            surahNameArabic = surahNameArabic,
            ayahNumber = ayahNumber,
            ayahText = ayahText.removePrefix("\uFEFF").ifBlank { ayahTextArabic.removePrefix("\uFEFF") },
            ayahTextArabic = ayahTextArabic.removePrefix("\uFEFF"),
            translation = translation,
            transliteration = transliteration,
            footnotes = footnotes.orEmpty()
        )
    }
}

/** Failure while loading Quran content; [code] is an AppStrings key for the UI. */
class ContentLoadException(val code: String, cause: Throwable? = null) : Exception(code, cause)

object QuranDataLoader {
    const val EXPECTED_VERSE_COUNT = 6236

    /**
     * Version of the bundled Uzbek content. Bump when assets/quran changes so installed apps
     * reload it once (2: QuranEnc text with footnotes kept apart from the translation).
     */
    const val BUNDLED_CONTENT_VERSION = 2

    /** Languages whose full Quran content ships inside the APK (assets/quran/<lang>.json). */
    private val bundledLanguages = setOf("uz_latin", "uz_cyrillic")

    fun isBundled(language: String): Boolean = language in bundledLanguages

    /** Loads a bundled translation from assets — no network involved. */
    fun loadFromAssets(context: Context, language: String): List<QuranVerse> {
        require(isBundled(language)) { "$language is not bundled" }
        val data = try {
            context.assets.open("quran/$language.json").bufferedReader(Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, QuranAssetData::class.java)
            }
        } catch (error: Exception) {
            android.util.Log.e("QuranDataLoader", "Failed to read bundled $language", error)
            throw ContentLoadException("loading.error.bundled", error)
        }
        return data.toVerses(language)
    }

    /** Bump when the server payload changes shape or content, so stale caches are ignored. */
    private const val DISK_CACHE_VERSION = 1

    private fun diskCacheFile(context: Context, language: String): java.io.File =
        java.io.File(java.io.File(context.filesDir, "quran"), "$language.v$DISK_CACHE_VERSION.json.gz")

    /**
     * A translation downloaded earlier, kept gzipped on disk (~1 MB each) so switching back to
     * it is instant and works offline. Returns null when absent or unreadable (and drops a
     * corrupt file so the next attempt re-downloads).
     */
    fun loadFromDiskCache(context: Context, language: String): List<QuranVerse>? {
        val file = diskCacheFile(context, language)
        if (!file.exists()) return null
        val verses = runCatching {
            java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, QuranAssetData::class.java)
            }.toVerses(language)
        }.getOrNull()
        if (verses == null || verses.size < EXPECTED_VERSE_COUNT) {
            file.delete()
            return null
        }
        return verses
    }

    /**
     * Downloads a translation from the server and keeps a gzipped copy on disk (see
     * [loadFromDiskCache]). Throws [ContentLoadException] with `loading.error.offline` for
     * connectivity failures and `loading.error.server` otherwise.
     */
    fun loadFromServer(context: Context, language: String): List<QuranVerse> {
        val target = diskCacheFile(context, language)
        val partial = java.io.File(target.parentFile, "${target.name}.part")
        target.parentFile?.mkdirs()
        val encodedLanguage = java.net.URLEncoder.encode(language, "UTF-8")
        val url = java.net.URL("${BuildConfig.SERVER_BASE_URL}/api/quran/verses?language=$encodedLanguage")
        val connection = try {
            url.openConnection() as java.net.HttpURLConnection
        } catch (io: java.io.IOException) {
            throw ContentLoadException("loading.error.offline", io)
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            val code = try {
                connection.responseCode
            } catch (io: java.io.IOException) {
                throw ContentLoadException("loading.error.offline", io)
            }
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                android.util.Log.e("QuranDataLoader", "Failed to fetch verses, response code: $code")
                throw ContentLoadException("loading.error.server")
            }
            // Stream the body straight into a gzipped file, then parse from disk: the payload
            // is ~5 MB of JSON and never needs to sit in memory as one string.
            try {
                connection.inputStream.use { body ->
                    java.util.zip.GZIPOutputStream(partial.outputStream()).use { out -> body.copyTo(out) }
                }
            } catch (io: java.io.IOException) {
                partial.delete()
                throw ContentLoadException("loading.error.offline", io)
            }
            val data = try {
                java.util.zip.GZIPInputStream(partial.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
                    Gson().fromJson(reader, QuranAssetData::class.java)
                }
            } catch (error: Exception) {
                partial.delete()
                throw ContentLoadException("loading.error.server", error)
            }
            val verses = data.toVerses(language)
            if (verses.size >= EXPECTED_VERSE_COUNT) partial.renameTo(target) else partial.delete()
            return verses
        } finally {
            connection.disconnect()
        }
    }

    private fun QuranAssetData?.toVerses(language: String): List<QuranVerse> {
        if (this == null) return emptyList()
        val verses = if (verses.isNotEmpty()) {
            val entities = verses.map { it.toEntity() }
            // The server's Russian payload carries Latin transliterations ("Al-Baqarah");
            // show the Russian surah names instead.
            if (language == "ru") {
                entities.map { verse ->
                    SurahNames.russian.getOrNull(verse.surahNumber - 1)
                        ?.let { verse.copy(surahName = it) } ?: verse
                }
            } else {
                entities
            }
        } else {
            quranEncResult.map { it.toEntity(language) }
        }
        return verses.filter { verse ->
            verse.surahNumber in 1..114 &&
                verse.ayahNumber > 0 &&
                verse.translation.isNotBlank()
        }
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
    fun toEntity(language: String): QuranVerse {
        val surah = surahNumber.toIntOrNull() ?: 1
        return QuranVerse(
            surahNumber = surah,
            surahName = SurahNames.forLanguage(language)[surah - 1],
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
    fun forLanguage(language: String): List<String> = when (language) {
        "uz_cyrillic" -> uzbekCyrillic
        "ru" -> russian
        "en" -> english
        else -> uzbekLatin
    }


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

    val uzbekCyrillic = listOf(
        "Фотиҳа", "Бақара", "Оли Имрон", "Нисо", "Моида", "Анъом", "Аъроф",
        "Анфол", "Тавба", "Юнус", "Ҳуд", "Юсуф", "Раъд", "Иброҳим", "Ҳижр",
        "Наҳл", "Исро", "Каҳф", "Марям", "Тоҳа", "Анбиё", "Ҳаж", "Мўминун",
        "Нур", "Фурқон", "Шуаро", "Намл", "Қасас", "Анкабут", "Рум", "Луқмон",
        "Сажда", "Аҳзоб", "Саба", "Фотир", "Ёсин", "Соффот", "Сод", "Зумар",
        "Ғофир", "Фуссилат", "Шуро", "Зухруф", "Духон", "Жосия", "Аҳқоф",
        "Муҳаммад", "Фатҳ", "Ҳужурот", "Қоф", "Зориёт", "Тур", "Нажм", "Қамар",
        "Раҳмон", "Воқеа", "Ҳадид", "Мужодала", "Ҳашр", "Мумтаҳана", "Сафф",
        "Жума", "Мунофиқун", "Тағобун", "Талоқ", "Таҳрим", "Мулк", "Қалам",
        "Ҳаққа", "Маориж", "Нуҳ", "Жин", "Муззаммил", "Муддассир", "Қиёмат",
        "Инсон", "Мурсалот", "Наба", "Нозиот", "Абаса", "Таквир", "Инфитор",
        "Мутоффифин", "Иншиқоқ", "Буруж", "Ториқ", "Аъло", "Ғошия", "Фажр",
        "Балад", "Шамс", "Лайл", "Зуҳо", "Шарҳ", "Тийн", "Алақ", "Қадр",
        "Баййина", "Залзала", "Одиёт", "Қориа", "Такосур", "Аср", "Ҳумаза",
        "Фил", "Қурайш", "Моъун", "Кавсар", "Кофирун", "Наср", "Масад", "Ихлос",
        "Фалақ", "Нос"
    )

    val russian = listOf(
        "Открывающая", "Корова", "Семейство Имрана", "Женщины", "Трапеза", "Скот", "Ограды",
        "Трофеи", "Покаяние", "Юнус", "Худ", "Юсуф", "Гром", "Ибрахим", "Хиджр",
        "Пчёлы", "Ночной перенос", "Пещера", "Марьям", "Та Ха", "Пророки", "Паломничество", "Верующие",
        "Свет", "Различение", "Поэты", "Муравьи", "Рассказ", "Паук", "Румы", "Лукман",
        "Земной поклон", "Союзники", "Саба", "Творец", "Йа Син", "Стоящие в ряд", "Сад", "Толпы",
        "Прощающий", "Разъяснены", "Совет", "Украшения", "Дым", "Коленопреклонённые", "Пески",
        "Мухаммад", "Победа", "Комнаты", "Каф", "Рассеивающие", "Гора", "Звезда", "Луна",
        "Милостивый", "Событие", "Железо", "Препирающаяся", "Собрание", "Испытуемая", "Ряды",
        "Пятница", "Лицемеры", "Взаимное обделение", "Развод", "Запрещение", "Власть", "Письменная трость",
        "Неминуемое", "Ступени", "Нух", "Джинны", "Закутавшийся", "Завернувшийся", "Воскресение",
        "Человек", "Посылаемые", "Весть", "Исторгающие", "Нахмурился", "Скручивание", "Раскалывание",
        "Обвешивающие", "Разверзнется", "Созвездия", "Ночной путник", "Всевышний", "Покрывающее", "Заря",
        "Город", "Солнце", "Ночь", "Утро", "Раскрытие", "Смоковница", "Сгусток крови", "Предопределение",
        "Ясное знамение", "Землетрясение", "Скачущие", "Великое бедствие", "Приумножение", "Время", "Хулитель",
        "Слон", "Курайшиты", "Мелочь", "Изобилие", "Неверующие", "Помощь", "Пальмовые волокна", "Искренность",
        "Рассвет", "Люди"
    )

    val english = listOf(
        "Al-Fatihah", "Al-Baqarah", "Aal-E-Imran", "An-Nisa", "Al-Ma'idah", "Al-An'am", "Al-A'raf",
        "Al-Anfal", "At-Tawbah", "Yunus", "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr",
        "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha", "Al-Anbiya", "Al-Hajj", "Al-Mu'minun",
        "An-Nur", "Al-Furqan", "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum", "Luqman",
        "As-Sajdah", "Al-Ahzab", "Saba", "Fatir", "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar",
        "Ghafir", "Fussilat", "Ash-Shuraa", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah", "Al-Ahqaf",
        "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf", "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar",
        "Ar-Rahman", "Al-Waqi'ah", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahanah", "As-Saff",
        "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam",
        "Al-Haqqah", "Al-Ma'arij", "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah",
        "Al-Insan", "Al-Mursalat", "An-Naba", "An-Nazi'at", "Abasa", "At-Takwir", "Al-Infitar",
        "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr",
        "Al-Balad", "Ash-Shams", "Al-Layl", "Ad-Duhaa", "Ash-Sharh", "At-Tin", "Al-Alaq", "Al-Qadr",
        "Al-Bayyinah", "Az-Zalzalah", "Al-Adiyat", "Al-Qari'ah", "At-Takathur", "Al-Asr", "Al-Humazah",
        "Al-Fil", "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad", "Al-Ikhlas",
        "Al-Falaq", "An-Nas"
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
