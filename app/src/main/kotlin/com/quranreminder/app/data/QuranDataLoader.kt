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
                data.quranEncResult.map { it.toEntity(language) }
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
        "Инсон", "Мурсалот", "Наба", "Нозиот", "Абаса", "Такв", "Инфитор",
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
