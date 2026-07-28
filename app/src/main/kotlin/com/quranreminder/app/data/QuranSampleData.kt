package com.quranreminder.app.data

import com.quranreminder.app.database.QuranVerse

/**
 * Offline sample Quran verses with accurate Uzbek Latin translation and transliteration.
 */
object QuranSampleData {
    fun getSampleVerses(): List<QuranVerse> = listOf(
        // Surah Al-Fatiha (1)
        QuranVerse(
            surahNumber = 1,
            surahName = "Al-Fatiha",
            surahNameArabic = "الفاتحة",
            ayahNumber = 1,
            ayahText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            ayahTextArabic = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            translation = "Mehribon va rahmli Allohning nomi bilan boshlayman.",
            transliteration = "Bismillahir Rohmanir Rohiym"
        ),
        QuranVerse(
            surahNumber = 1,
            surahName = "Al-Fatiha",
            surahNameArabic = "الفاتحة",
            ayahNumber = 2,
            ayahText = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            ayahTextArabic = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            translation = "Barcha olamlarning Robbi bo'lmish Allohga hamdlar bo'lsin.",
            transliteration = "Alhamdu lillahi robbil 'alamiyn"
        ),
        QuranVerse(
            surahNumber = 1,
            surahName = "Al-Fatiha",
            surahNameArabic = "الفاتحة",
            ayahNumber = 3,
            ayahText = "الرَّحْمَٰنِ الرَّحِيمِ",
            ayahTextArabic = "الرَّحْمَٰنِ الرَّحِيمِ",
            translation = "U Mehribon va Rahmlidir.",
            transliteration = "Ar-Rohmanir-Rohiym"
        ),

        // Surah Al-Baqarah (2)
        QuranVerse(
            surahNumber = 2,
            surahName = "Al-Baqarah",
            surahNameArabic = "البقرة",
            ayahNumber = 1,
            ayahText = "الم",
            ayahTextArabic = "الم",
            translation = "Alif, Lom, Mim.",
            transliteration = "Alif, Lom, Mim"
        ),
        QuranVerse(
            surahNumber = 2,
            surahName = "Al-Baqarah",
            surahNameArabic = "البقرة",
            ayahNumber = 2,
            ayahText = "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ",
            ayahTextArabic = "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ",
            translation = "Ushbu Kitobda hech qanday shubha yo'qdir; u taqvodorlar uchun hidoyatdir.",
            transliteration = "Zalikal kitabu la royba fih, hudal-lil-muttaqiyn"
        ),

        // Surah Al-Imran (3)
        QuranVerse(
            surahNumber = 3,
            surahName = "Al-Imran",
            surahNameArabic = "آل عمران",
            ayahNumber = 1,
            ayahText = "الم",
            ayahTextArabic = "الم",
            translation = "Alif, Lom, Mim.",
            transliteration = "Alif, Lom, Mim"
        ),
        QuranVerse(
            surahNumber = 3,
            surahName = "Al-Imran",
            surahNameArabic = "آل عمران",
            ayahNumber = 2,
            ayahText = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            ayahTextArabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            translation = "Alloh – Undan o'zga iloh yo'q. U Tirik va Qayyum (abadiy turuvchi)dir.",
            transliteration = "Allahu la ilaha illa huwal-Hayyul-Qayyum"
        ),

        // Surah An-Nisa (4)
        QuranVerse(
            surahNumber = 4,
            surahName = "An-Nisa",
            surahNameArabic = "النساء",
            ayahNumber = 1,
            ayahText = "يَا أَيُّهَا النَّاسُ اتَّقُوا رَبَّكُمُ الَّذِي خَلَقَكُم مِّن نَّفْسٍ وَاحِدَةٍ",
            ayahTextArabic = "يَا أَيُّهَا النَّاسُ اتَّقُوا رَبَّكُمُ الَّذِي خَلَقَكُم مِّن نَّفْسٍ وَاحِدَةٍ",
            translation = "Ey odamlar! Sizlarni bir nafsdan yaratgan Robbingizdan qo'rqingiz.",
            transliteration = "Ya ayyuhan-nasuttaqu robbakumullazi khalaqakum min nafsiw-wahidah"
        ),

        // Surah Al-Ma'idah (5)
        QuranVerse(
            surahNumber = 5,
            surahName = "Al-Ma'idah",
            surahNameArabic = "المائدة",
            ayahNumber = 1,
            ayahText = "يَا أَيُّهَا الَّذِينَ آمَنُوا أَوْفُوا بِالْعُقُودِ",
            ayahTextArabic = "يَا أَيُّهَا الَّذِينَ آمَنُوا أَوْفُوا بِالْعُقُودِ",
            translation = "Ey iymon keltirganlar! Ahd-paymonlarga vafo qilingiz.",
            transliteration = "Ya ayyuhallazina amanu awfu bil-'uqud"
        ),

        // Surah Al-An'am (6)
        QuranVerse(
            surahNumber = 6,
            surahName = "Al-An'am",
            surahNameArabic = "الأنعام",
            ayahNumber = 1,
            ayahText = "الْحَمْدُ لِلَّهِ الَّذِي خَلَقَ السَّمَاوَاتِ وَالْأَرْضَ وَجَعَلَ الظُّلُمَاتِ وَالنُّورَ",
            ayahTextArabic = "الْحَمْدُ لِلَّهِ الَّذِي خَلَقَ السَّمَاوَاتِ وَالْأَرْضَ وَجَعَلَ الظُّلُمَاتِ وَالنُّورَ",
            translation = "Osmonlar va yerni yaratgan, zulmatlar va nurni paydo qilgan Allohga hamd bo'lsin.",
            transliteration = "Alhamdu lillahillazi khalaqas-samawati wal-arda wa ja'alaz-zulumati wan-nur"
        ),

        // Surah Al-A'raf (7)
        QuranVerse(
            surahNumber = 7,
            surahName = "Al-A'raf",
            surahNameArabic = "الأعراف",
            ayahNumber = 1,
            ayahText = "كِتَابٌ أُنزِلَ إِلَيْكَ فَلَا يَكُن فِي صَدْرِكَ حَرَجٌ مِّنْهُ",
            ayahTextArabic = "كِتَابٌ أُنزِلَ إِلَيْكَ فَلَا يَكُن فِي صَدْرِكَ حَرَجٌ مِّنْهُ",
            translation = "Senga nozil qilingan bu Kitob sababli dilingda siqilish bo'lmasin.",
            transliteration = "Kitabun unzila ilayka fala yakun fi sadrika harajum-minh"
        ),

        // Surah Al-Anfal (8)
        QuranVerse(
            surahNumber = 8,
            surahName = "Al-Anfal",
            surahNameArabic = "الأنفال",
            ayahNumber = 1,
            ayahText = "يَسْأَلُونَكَ عَنِ الْأَنفَالِ قُلِ الْأَنفَالُ لِلَّهِ وَالرَّسُولِ",
            ayahTextArabic = "يَسْأَلُونَكَ عَنِ الْأَنفَالِ قُلِ الْأَنفَالُ لِلَّهِ وَالرَّسُولِ",
            translation = "Sendan g'animatlar haqida so'raydilar. Sen: «G'animatlar Alloh va Payg'ambarnikidir», degin.",
            transliteration = "Yas'alunaka 'anil-anfal, qulil-anfalu lillahi war-rasul"
        ),

        // Surah At-Tawbah (9)
        QuranVerse(
            surahNumber = 9,
            surahName = "At-Tawbah",
            surahNameArabic = "التوبة",
            ayahNumber = 1,
            ayahText = "بَرَاءَةٌ مِّنَ اللَّهِ وَرَسُولِهِ إِلَى الَّذِينَ عَاهَدتُم مِّنَ الْمُشْرِكِينَ",
            ayahTextArabic = "بَرَاءَةٌ مِّنَ اللَّهِ وَرَسُولِهِ إِلَى الَّذِينَ عَاهَدتُم مِّنَ الْمُشْرِكِينَ",
            translation = "Ushbu (oyatlar) Alloh va Uning Rasuli tomonidan ahdlashgan mushriklaringizga berilgan ogohlantirishdir.",
            transliteration = "Bara'atum-minallahi wa rasulihi ilallazina 'ahadtum-minal-mushrikiyn"
        ),

        // Surah Yunus (10)
        QuranVerse(
            surahNumber = 10,
            surahName = "Yunus",
            surahNameArabic = "يونس",
            ayahNumber = 1,
            ayahText = "الر تِلْكَ آيَاتُ الْكِتَابِ الْحَكِيمِ",
            ayahTextArabic = "الر تِلْكَ آيَاتُ الْكِتَابِ الْحَكِيمِ",
            translation = "Alif, Lom, Ro. Bular hikmatli Kitobning oyatlaridir.",
            transliteration = "Alif, Lom, Ro. Tilka ayatul-kitabil-hakiym"
        )
    )
}

