class VerseModel {
  final int id;
  final String arabicText;
  final String transliteration;
  final String translation;

  const VerseModel({
    required this.id,
    required this.arabicText,
    required this.transliteration,
    required this.translation,
  });
}

class SurahModel {
  final int id;
  final String name;
  final String arabicName;
  final List<VerseModel> verses;

  const SurahModel({
    required this.id,
    required this.name,
    required this.arabicName,
    required this.verses,
  });
}

abstract class QuranData {
  static const List<SurahModel> surahs = [
    SurahModel(
      id: 1,
      name: 'Al-Fatihah',
      arabicName: 'الفاتحة',
      verses: [
        VerseModel(
          id: 1,
          arabicText: 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ',
          transliteration: 'Bismillahir rahmanir rahim',
          translation: 'Mehribon va rahmli Alloh nomi bilan boshlayman.',
        ),
        VerseModel(
          id: 2,
          arabicText: 'الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ',
          transliteration: 'Alhamdu lillahi rabbil \'alamin',
          translation: 'Barcha maqtovlar olamlar parvardigori Allohgadir.',
        ),
        VerseModel(
          id: 3,
          arabicText: 'الرَّحْمَٰنِ الرَّحِيمِ',
          transliteration: 'Ar-rahmanir rahim',
          translation: 'U mehribon va rahmlidir.',
        ),
        VerseModel(
          id: 4,
          arabicText: 'مَالِكِ يَوْمِ الدِّينِ',
          transliteration: 'Maliki yawmid din',
          translation: 'U qiyomat kunining podshohidir.',
        ),
        VerseModel(
          id: 5,
          arabicText: 'إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ',
          transliteration: 'Iyyaka na\'budu wa iyyaka nasta\'in',
          translation: 'Faqat Sengagina ibodat qilamiz va faqat Sendangina yordam so\'raymiz.',
        ),
        VerseModel(
          id: 6,
          arabicText: 'اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ',
          transliteration: 'Ihdinas siratal mustaqim',
          translation: 'Bizni to\'g\'ri yo\'lga boshla.',
        ),
        VerseModel(
          id: 7,
          arabicText: 'صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ',
          transliteration: 'Siratal lazina an\'amta \'alayhim ghayril maghdubi \'alayhim walad dallin',
          translation: 'G\'azabga uchramagan va adashmagan o\'zing ne\'mat bergan zotlarning yo\'liga.',
        ),
      ],
    ),
    SurahModel(
      id: 112,
      name: 'Al-Ikhlas',
      arabicName: 'الإخلاص',
      verses: [
        VerseModel(
          id: 1,
          arabicText: 'قُلْ هُوَ اللَّهُ أَحَدٌ',
          transliteration: 'Qul huwal lahu ahad',
          translation: 'Ayt: «U Alloh yagonadir.',
        ),
        VerseModel(
          id: 2,
          arabicText: 'اللَّهُ الصَّمَدُ',
          transliteration: 'Allahus samad',
          translation: 'Alloh behojatdir (barcha hojatlar Undan so\'raladi).',
        ),
        VerseModel(
          id: 3,
          arabicText: 'لَمْ يَلِدْ وَلَمْ يُولَدْ',
          transliteration: 'Lam yalid wa lam yulad',
          translation: 'U tug\'magan va tug\'ilmagan.',
        ),
        VerseModel(
          id: 4,
          arabicText: 'وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ',
          transliteration: 'Walam yakul lahu kufuwan ahad',
          translation: 'Va unga hech kim teng bo\'lmagan.',
        ),
      ],
    ),
    SurahModel(
      id: 113,
      name: 'Al-Falaq',
      arabicName: 'الفلق',
      verses: [
        VerseModel(
          id: 1,
          arabicText: 'قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ',
          transliteration: 'Qul a\'uzu birabbil falaq',
          translation: 'Ayt: «Tong parvardigoridan panoh so\'rayman,',
        ),
        VerseModel(
          id: 2,
          arabicText: 'مِن شَرِّ مَا خَلَقَ',
          transliteration: 'Min sharri ma khalaq',
          translation: 'Yaratgan narsalarining yomonligidan,',
        ),
        VerseModel(
          id: 3,
          arabicText: 'وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ',
          transliteration: 'Wamin sharri ghasiqin iza waqab',
          translation: 'Zulmatga cho\'mgan tun yomonligidan,',
        ),
        VerseModel(
          id: 4,
          arabicText: 'وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ',
          transliteration: 'Wamin sharrin naffasati fil \'uqad',
          translation: 'Tugunlarga dam soluvchi ayollar yomonligidan,',
        ),
        VerseModel(
          id: 5,
          arabicText: 'وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ',
          transliteration: 'Wamin sharri hasidin iza hasad',
          translation: 'Va hasadgo\'yning hasad qilgan paytdagi yomonligidan».',
        ),
      ],
    ),
    SurahModel(
      id: 114,
      name: 'An-Nas',
      arabicName: 'الناس',
      verses: [
        VerseModel(
          id: 1,
          arabicText: 'قُلْ أَعُوذُ بِرَبِّ النَّاسِ',
          transliteration: 'Qul a\'uzu birabbin nas',
          translation: 'Ayt: «Insonlar parvardigoridan panoh so\'rayman,',
        ),
        VerseModel(
          id: 2,
          arabicText: 'مَلِكِ النَّاسِ',
          transliteration: 'Malikin nas',
          translation: 'Insonlar podshohidan,',
        ),
        VerseModel(
          id: 3,
          arabicText: 'إِلَٰهِ النَّاسِ',
          transliteration: 'Ilahin nas',
          translation: 'Insonlar ilohidan,',
        ),
        VerseModel(
          id: 4,
          arabicText: 'مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ',
          transliteration: 'Min sharril waswasil khannas',
          translation: 'Yashirinib, vasvasa qiluvchi (shayton) yomonligidan,',
        ),
        VerseModel(
          id: 5,
          arabicText: 'الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ',
          transliteration: 'Allazi yuwaswisu fi sudurin nas',
          translation: 'U insonlarning dillariga vasvasa solur.',
        ),
        VerseModel(
          id: 6,
          arabicText: 'مِنَ الْجِنَّةِ وَالنَّاسِ',
          transliteration: 'Minal jinnati wannas',
          translation: 'U jinlardan va insonlardandir (deb ayt)».',
        ),
      ],
    ),
  ];
}
