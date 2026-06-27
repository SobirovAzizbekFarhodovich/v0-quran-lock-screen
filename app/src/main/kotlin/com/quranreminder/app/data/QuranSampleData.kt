package com.quranreminder.app.data

import com.quranreminder.app.database.QuranVerse

/**
 * Small offline sample used only when app/src/main/assets/quran/verses.json is missing.
 * Replace it with a licensed complete Uzbek translation JSON before publishing.
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
            translation = "Rahmani, rahimiy Allohning nomida boshlayman"
        ),
        QuranVerse(
            surahNumber = 1,
            surahName = "Al-Fatiha",
            surahNameArabic = "الفاتحة",
            ayahNumber = 2,
            ayahText = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            ayahTextArabic = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            translation = "Barcha olam-jahonlarning Rabi Allohga barcha hamdlar bo'lsin"
        ),
        QuranVerse(
            surahNumber = 1,
            surahName = "Al-Fatiha",
            surahNameArabic = "الفاتحة",
            ayahNumber = 3,
            ayahText = "الرَّحْمَٰنِ الرَّحِيمِ",
            ayahTextArabic = "الرَّحْمَٰنِ الرَّحِيمِ",
            translation = "Rahmani, rahimiy (bo'lgan Allohga)"
        ),

        // Surah Al-Baqarah (2)
        QuranVerse(
            surahNumber = 2,
            surahName = "Al-Baqarah",
            surahNameArabic = "البقرة",
            ayahNumber = 1,
            ayahText = "الم",
            ayahTextArabic = "الم",
            translation = "Alif, Lom, Mim"
        ),
        QuranVerse(
            surahNumber = 2,
            surahName = "Al-Baqarah",
            surahNameArabic = "البقرة",
            ayahNumber = 2,
            ayahText = "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ",
            ayahTextArabic = "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ",
            translation = "Vot o'sha Kitob - undagi shubha yo'q. U taqvo qiluvchilarni to'g'ri yo'lga olib boradigan hidoya"
        ),

        // Surah Al-Imran (3)
        QuranVerse(
            surahNumber = 3,
            surahName = "Al-Imran",
            surahNameArabic = "آل عمران",
            ayahNumber = 1,
            ayahText = "الم",
            ayahTextArabic = "الم",
            translation = "Alif, Lom, Mim"
        ),
        QuranVerse(
            surahNumber = 3,
            surahName = "Al-Imran",
            surahNameArabic = "آل عمران",
            ayahNumber = 2,
            ayahText = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            ayahTextArabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
            translation = "Alloh - Uning tashqarida boshqa iloh yo'q. U - Hayy (Doimiy tirik), Qayyum (O'ziga va boshqalarning o'ziga ishonib turadigan)"
        ),

        // Surah An-Nisa (4)
        QuranVerse(
            surahNumber = 4,
            surahName = "An-Nisa",
            surahNameArabic = "النساء",
            ayahNumber = 1,
            ayahText = "يَا أَيُّهَا النَّاسُ اتَّقُوا رَبَّكُمُ الَّذِي خَلَقَكُم مِّن نَّفْسٍ وَاحِدَةٍ",
            ayahTextArabic = "يَا أَيُّهَا النَّاسُ اتَّقُوا رَبَّكُمُ الَّذِي خَلَقَكُم مِّن نَّفْسٍ وَاحِدَةٍ",
            translation = "Ey odamlar! O'zlaringizning Rabbingizdan qo'rqing. U sizlarni bitta nafs (Odam)dan yaratdi"
        ),

        // Surah Al-Ma'idah (5)
        QuranVerse(
            surahNumber = 5,
            surahName = "Al-Ma'idah",
            surahNameArabic = "المائدة",
            ayahNumber = 1,
            ayahText = "يَا أَيُّهَا الَّذِينَ آمَنُوا أَوْفُوا بِالْعُقُودِ",
            ayahTextArabic = "يَا أَيُّهَا الَّذِينَ آمَنُوا أَوْفُوا بِالْعُقُودِ",
            translation = "Ey imon keltirganlar! Shartlarni to'ldirib bering"
        ),

        // Surah Al-An'am (6)
        QuranVerse(
            surahNumber = 6,
            surahName = "Al-An'am",
            surahNameArabic = "الأنعام",
            ayahNumber = 1,
            ayahText = "الْحَمْدُ لِلَّهِ الَّذِي خَلَقَ السَّمَاوَاتِ وَالْأَرْضَ وَجَعَلَ الظُّلُمَاتِ وَالنُّورَ",
            ayahTextArabic = "الْحَمْدُ لِلَّهِ الَّذِي خَلَقَ السَّمَاوَاتِ وَالْأَرْضَ وَجَعَلَ الظُّلُمَاتِ وَالنُّورَ",
            translation = "Samovotlar va yerni yaratgan Allohga barcha hamdlar bo'lsin. U zulmatlar va nurni yaratdi"
        ),

        // Surah Al-A'raf (7)
        QuranVerse(
            surahNumber = 7,
            surahName = "Al-A'raf",
            surahNameArabic = "الأعراف",
            ayahNumber = 1,
            ayahText = "كِتَابٌ أُنزِلَ إِلَيْكَ فَلَا يَكُن فِي صَدْرِكَ حَرَجٌ مِّنْهُ",
            ayahTextArabic = "كِتَابٌ أُنزِلَ إِلَيْكَ فَلَا يَكُن فِي صَدْرِكَ حَرَجٌ مِّنْهُ",
            translation = "Bu Kitob saga nozil bo'ldi. Uni sabab bilgan xavflanmang"
        ),

        // Surah Al-Anfal (8)
        QuranVerse(
            surahNumber = 8,
            surahName = "Al-Anfal",
            surahNameArabic = "الأنفال",
            ayahNumber = 1,
            ayahText = "يَسْأَلُونَكَ عَنِ الْأَنفَالِ قُلِ الْأَنفَالُ لِلَّهِ وَالرَّسُولِ",
            ayahTextArabic = "يَسْأَلُونَكَ عَنِ الْأَنفَالِ قُلِ الْأَنفَالُ لِلَّهِ وَالرَّسُولِ",
            translation = "Seni ganima haqida so'rashadi. Sen: \"Ganima Allohning va Rasulning.\" deb ey."
        ),

        // Surah At-Tawbah (9)
        QuranVerse(
            surahNumber = 9,
            surahName = "At-Tawbah",
            surahNameArabic = "التوبة",
            ayahNumber = 1,
            ayahText = "بَرَاءَةٌ مِّنَ اللَّهِ وَرَسُولِهِ إِلَى الَّذِينَ عَاهَدتُم مِّنَ الْمُشْرِكِينَ",
            ayahTextArabic = "بَرَاءَةٌ مِّنَ اللَّهِ وَرَسُولِهِ إِلَى الَّذِينَ عَاهَدتُم مِّنَ الْمُشْرِكِينَ",
            translation = "Bu Alloh va Uning Rasulining ozodlik e'loni bo'lib, u shunday mushriklar tomoniga yo'naltiriladiki"
        ),

        // Surah Yunus (10)
        QuranVerse(
            surahNumber = 10,
            surahName = "Yunus",
            surahNameArabic = "يونس",
            ayahNumber = 1,
            ayahText = "الر تِلْكَ آيَاتُ الْكِتَابِ الْحَكِيمِ",
            ayahTextArabic = "الر تِلْكَ آيَاتُ الْكِتَابِ الْحَكِيمِ",
            translation = "Alif, Lom, Rod. Bu - danishmand Kitobning oyatlari"
        )
    )
}
