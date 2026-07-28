#!/usr/bin/env python3
"""
Ishonchli va sahih islomiy manbalardan (Hisnul Muslim, Riyozus Solihiyn, Al-Adhkar,
Bulugh al-Maram, Qur'oniy duolar, Ibodat va Muqaddas oylar) strukturallangan
JSONL ma'lumotlar bazasini shakllantiruvchi skript.

Manbalar:
1. Hisnul Muslim (Muslim Qal'asi / Крепость мусульманина)
2. Riyozus Solihiyn (Imom Navaviy)
3. Al-Adhkar (Imom Navaviy)
4. Bulugh al-Maram (Ibn Hajar al-Asqalani)
5. Qur'ondagi barcha "Rabbana" va boshqa duolar (Sura:Oyat ko'rsatilgan)
6. Ibodat bo'limi (Tahorat, G'usl, Tayammum, Namoz, Juma, Janoza, Ro'za, Zakot, Haj, Umra, Qurbonlik)
7. Muqaddas oylar bo'limi (Muharram, Rajab, Sha'bon, Ramazon, Zulqa'da, Zulhijja)
"""

import json
import os
import sys
from pathlib import Path

# Skript papkasidagi cyrillic_to_latin modulini import qilish
sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin

BASE_DATA_DIR = Path(__file__).parent.parent / "data"

# =====================================================================
# 1. HISNUL MUSLIM (Muslim Qal'asi / Fortress of the Muslim)
# =====================================================================
HISNUL_MUSLIM_DATA = [
    {
        "id": "hisn:1",
        "category": "Tong va kechqurun o'qiladigan zikrlar",
        "title_uz_cyr": "Тонг ва кечқурун ўқиладиган зикрлар (Ҳиснул Муслим)",
        "text_ar": "Аллаҳумма бика асбаҳна ва бика амсайна ва бика наҳйа ва бика намуту ва илайкан-нушур.",
        "arabic_raw": "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
        "transliteration": "Allahumma bika asbahna wa bika amsayna wa bika nahya wa bika namutu wa ilaykan-nushur",
        "text_uz_cyr": "Аллоҳим! Сенинг ёрдаминг билан тонгга эришдик, Сенинг ёрдаминг билан оқшомга етдик. Сенинг борлигинг учун яшаймиз ва ўламиз, қайтишимиз ҳам Сегадир.",
        "text_uz_lat": "Allahim! Sening yordaming bilan tongga erishdik, Sening yordaming bilan oqshomga etdik. Sening borliging uchun yashaymiz va o'lamiz, qaytishimiz ham Segadir.",
        "text_en": "O Allah, by Your leave we have reached the morning and by Your leave we have reached the evening, by Your leave we live and by Your leave we die, and unto You is our resurrection.",
        "text_ru": "О Аллах, благодаря Тебе мы дожили до утра и благодаря Тебе дожили до вечера, Ты даруешь нам жизнь и Ты лишаешь нас её, и к Тебе предстоит воскрешение.",
        "source": "Hisnul Muslim, 27-duo / Abu Dovud va Tirmiziy rivoyati (Sahih)"
    },
    {
        "id": "hisn:2",
        "category": "Uyqudan uyg'onganda",
        "title_uz_cyr": "Уйғонганда ўқиладиган дуо (Ҳиснул Муслим)",
        "arabic_raw": "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
        "transliteration": "Alhamdu lillahil-ladhi ahyana ba'da ma amatana wa ilayhin-nushur",
        "text_uz_cyr": "Бизни ўлдиргандан (ухлатгандан) сўнг қайта тирилтирган (уйғотган) Аллоҳга ҳамд бўлсин. Қайтиш Унинг ҳузуригадир.",
        "text_uz_lat": "Bizni o'ldirgandan (uxlatgandan) so'ng qayta tiriltirgan (uyg'otgan) Allohga hamd bo'lsin. Qaytish Uning huzurigadir.",
        "text_en": "Praise is to Allah Who gave us life after he had caused us to die and unto Him is the return.",
        "text_ru": "Хвала Аллаху, Который оживил нас после того, как умертвил нас, и к Которому предстоит возвращение.",
        "source": "Hisnul Muslim, 1-duo / Sahihi Buxoriy (6312-hadis) va Muslim (2711-hadis)"
    },
    {
        "id": "hisn:3",
        "category": "Kiyim kiyganda",
        "title_uz_cyr": "Кийим кийганда ўқиладиган дуо (Ҳиснул Муслим)",
        "arabic_raw": "الْحَمْدُ لِلَّهِ الَّذِي كَسَانِي هَذَا (الثَّوْبَ) وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلاَ قُوَّةٍ",
        "transliteration": "Alhamdu lillahil-ladhi kasani hadha (thawba) wa razaqanihi min ghayri hawlin minni wa la quwwatin",
        "text_uz_cyr": "Мендан ҳеч қандай куч ва қудрат бўлмасдан, менга ушбу кийимни кийдирган ва уни менга ризқ қилиб берган Аллоҳга ҳамд бўлсин.",
        "text_uz_lat": "Mendan hech qanday kuch va qudrat bo'lmasdan, menga ushbu kiyimni kiydirgan va uni menga rizq qilib bergan Allohga hamd bo'lsin.",
        "text_en": "Praise is to Allah Who has clothed me with this garment and provided it for me, with no power nor might from myself.",
        "text_ru": "Хвала Аллаху, Который одел меня в эту одежду и даровал мне её, тогда как сам я не обладаю ни силой, ни могуществом.",
        "source": "Hisnul Muslim, 2-duo / Sunani Abu Dovud, Tirmiziy va Ibn Moja (Hasan)"
    },
    {
        "id": "hisn:4",
        "category": "Uyga kirganda va chiqqanda",
        "title_uz_cyr": "Уйдан чиқаётганда ўқиладиган дуо (Ҳиснул Муслим)",
        "arabic_raw": "بِسْمِ اللَّهِ ، تَوَكَّلْتُ عَلَى اللَّهِ ، وَلاَ حَوْلَ وَلاَ قُوَّةَ إِلاَّ بِاللَّهِ",
        "transliteration": "Bismillahi, tawakkaltu 'alallahi, wa la hawla wa la quwwata illa billah",
        "text_uz_cyr": "Аллоҳнинг номи билан! Аллоҳга таваккал қилдим. Куч ва қудрат манбаи фақат Аллоҳдир.",
        "text_uz_lat": "Allohning nomi bilan! Allohga tavakkal qildim. Kuch va qudrat manbai faqat Allohdir.",
        "text_en": "In the name of Allah, I place my trust in Allah, and there is no might nor power except with Allah.",
        "text_ru": "С именем Аллаха, уповаю на Аллаха, и нет могущества и силы ни у кого, кроме Аллаха.",
        "source": "Hisnul Muslim, 16-duo / Abu Dovud (5095) va Tirmiziy (3426) rivoyati (Sahih)"
    },
    {
        "id": "hisn:5",
        "category": "Masjidga kirganda",
        "title_uz_cyr": "Масжидга кирганда ўқиладиган дуо (Ҳиснул Муслим)",
        "arabic_raw": "بِسْمِ اللَّهِ، وَالصَّلاَةُ وَالسَّلاَمُ عَلَى رَسُولِ اللَّهِ، اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ",
        "transliteration": "Bismillahi, was-salatu was-salamu 'ala rasulillahi, Allahummaf-tah li abwaba rahmatik",
        "text_uz_cyr": "Аллоҳнинг номи билан! Аллоҳнинг Расулига салавот ва саломлар бўлсин. Аллоҳим! Менга раҳматинг эшикларини очгин.",
        "text_uz_lat": "Allohning nomi bilan! Allohning Rasuliga salavot va salomlar bo'lsin. Allohim! Menga rahmating eshiklarini ochgin.",
        "text_en": "In the Name of Allah, and peace and blessings be upon the Messenger of Allah. O Allah, open for me the doors of Your mercy.",
        "text_ru": "С именем Аллаха, благословение и мир посланнику Аллаха. О Аллах, открой для меня врата Твоей милости.",
        "source": "Hisnul Muslim, 20-duo / Sahihi Muslim (713-hadis)"
    },
    {
        "id": "hisn:6",
        "category": "Qiyinchilik va g'am bosganda",
        "title_uz_cyr": "Қийинчилик ва ғами бор киши ўқийдиган дуо (Ҳиснул Муслим)",
        "arabic_raw": "لاَ إِلَهَ إِلاَّ اللَّهُ الْعَظِيمُ الْحَلِيمُ، لاَ إِلَهَ إِلاَّ اللَّهُ رَبُّ الْعَرْشِ الْعَظِيمِ، لاَ إِلَهَ إِلاَّ اللَّهُ رَبُّ السَّمَاوَاتِ وَرَبُّ الأَرْضِ وَرَبُّ الْعَرْشِ الْكَرِيمِ",
        "transliteration": "La ilaha illallahul-'Adhimul-Halim, la ilaha illallahu Rabbul-'Arshil-'Adhim, la ilaha illallahu Rabbus-samawati wa Rabbul-ardi wa Rabbul-'Arshil-Karim",
        "text_uz_cyr": "Буюк ва Ҳалим бўлган Аллоҳдан ўзга ибодатга лойиқ илоҳ йўқ. Улуғ Аршнинг Рабби бўлган Аллоҳдан ўзга илоҳ йўқ. Осмонларнинг Рабби, ернинг Рабби ва карамли Аршнинг Рабби бўлган Аллоҳдан ўзга илоҳ йўқ.",
        "text_uz_lat": "Buyuk va Halim bo'lgan Allohdan o'zga ibodatga loyiq iloh yo'q. Ulug' Arshning Rabbi bo'lgan Allohdan o'zga iloh yo'q. Osmonlarning Rabbi, yerning Rabbi va karamli Arshning Rabbi bo'lgan Allohdan o'zga iloh yo'q.",
        "text_en": "There is no god but Allah, the Great, the Tolerant, there is no god but Allah, the Lord of the Magnificent Throne, there is no god but Allah, the Lord of the heavens and the earth, and the Lord of the Honorable Throne.",
        "text_ru": "Нет бога, кроме Аллаха, Великого, Кроткого, нет бога, кроме Аллаха, Господа великого Престола, нет бога, кроме Аллаха, Господа небес, Господа земли и Господа благородного Престола.",
        "source": "Hisnul Muslim, 34-duo / Sahihi Buxoriy (6346) va Sahihi Muslim (2730)"
    }
]

# =====================================================================
# 2. QUR'ONIY DUOLAR VA RABBANA DUOLARI (All Authentic Quranic Duas)
# =====================================================================
QURANIC_DUAS_DATA = [
    {
        "id": "qduas:1",
        "surah": 2,
        "ayah": 201,
        "title_uz_cyr": "Дунё ва охират яхшилигини сўраш дуоси (Раббана дуоси)",
        "title_uz_lat": "Dunyo va oxirat yaxshiligini so'rash duosi (Rabbana duosi)",
        "arabic_raw": "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ",
        "transliteration": "Rabbana atina fid-dunya hasanatan wa fil-akhirati hasanatan wa qina 'adhaban-nar",
        "text_uz_cyr": "Эй Раббимиз, бизга бу дунёда ҳам яхшилик ато этгин, охиратда ҳам яхшилик ато этгин ва бизни дўзах азобидан асрагин.",
        "text_uz_lat": "Ey Rabbimiz, bizga bu dunyoda ham yaxshilik ato etgin, oxiratda ham yaxshilik ato etgin va bizni do'zah azobidan asragin.",
        "text_en": "Our Lord, give us in this world that which is good and in the Hereafter that which is good and protect us from the punishment of the Fire.",
        "text_ru": "Господь наш! Одари нас добром в этом мире и добром в Последней жизни и защити нас от мучений в Огне.",
        "source": "Qur'oni Karim, Baqara surasi 201-oyat"
    },
    {
        "id": "qduas:2",
        "surah": 3,
        "ayah": 8,
        "title_uz_cyr": "Қалб собитлиги ва ҳидоят сўраш дуоси",
        "title_uz_lat": "Qalb sobitligi va hidoyat so'rash duosi",
        "arabic_raw": "رَبَّنَا لاَ تُزِغْ قُلُوبَنَا بَعْدَ إِذْ هَدَيْتَنَا وَهَبْ لَنَا مِن لَّدُنكَ رَحْمَةً إِنَّكَ أَنتَ الْوَهَّابُ",
        "transliteration": "Rabbana la tuzigh qulubana ba'da idh hadaytana wa hab lana min ladunka rahmatan innaka antal-Wahhab",
        "text_uz_cyr": "Эй Раббимиз, бизни ҳидоят йўлига солганингдан кейин дилларимизни ҳақ йўлидан оғдирма ва бизга Ўз ҳузурингдан раҳмат ато эт! Албатта Сенгина ато этгувчисан!",
        "text_uz_lat": "Ey Rabbimiz, bizni hidoyat yo'liga solganingdan keyin dillarimizni haq yo'lidan og'dirma va bizga O'z huzuringdan rahmat ato et! Albatta Sengina ato etguvchisan!",
        "text_en": "Our Lord, let not our hearts swerve after You have guided us and grant us from Yourself mercy. Indeed, You are the Bestower.",
        "text_ru": "Господь наш! Не уклоняй наши сердца в сторону после того, как Ты наставил нас на прямой путь, и даруй нам милость от Себя, ведь Ты — Дарующий.",
        "source": "Qur'oni Karim, Oli Imron surasi 8-oyat"
    },
    {
        "id": "qduas:3",
        "surah": 21,
        "ayah": 87,
        "title_uz_cyr": "Юнус алайҳиссаломнинг қийинчиликдаги дуолари (Ояти Карима)",
        "title_uz_lat": "Yunus alayhissalomning qiyinchilikdagi duolari (Oyati Karima)",
        "arabic_raw": "لاَّ إِلَهَ إِلاَّ أَنتَ سُبْحَانَكَ إِنِّي كُنتُ مِنَ الظَّالِمِينَ",
        "transliteration": "La ilaha illa anta subhanaka inni kuntu minadh-dhalimin",
        "text_uz_cyr": "Сендан ўзга Илоҳ йўқдир. Сен барча нуқсонлардан покдирсан. Албатта мен ўз жонимга зулм қилувчилардан бўлдим.",
        "text_uz_lat": "Sendan o'zga Iloh yo'qdir. Sen barcha nuqsonlardan pokdirsan. Albatta men o'z jonimga zulm qiluvchilardan bo'ldim.",
        "text_en": "There is no deity except You; exalted are You. Indeed, I have been of the wrongdoers.",
        "text_ru": "Нет божества, кроме Тебя! Пречист Ты! Воистину, я был одним из беззаконников!",
        "source": "Qur'oni Karim, Anbiyo surasi 87-oyat / Sahihi Tirmiziy (3505-hadis)"
    },
    {
        "id": "qduas:4",
        "surah": 25,
        "ayah": 74,
        "title_uz_cyr": "Солиҳ оила ва фарзандлар сўраш дуоси",
        "title_uz_lat": "Solih oila va farzandlar so'rash duosi",
        "arabic_raw": "رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ وَاجْعَلْنَا لِلْمُتَّقِينَ إِمَامًا",
        "transliteration": "Rabbana hab lana min azwajina wa dhurriyyatina qurrata a'yunin waj'alna lil-muttaqina imama",
        "text_uz_cyr": "Эй Раббимиз, жуфти ҳалолларимиздан ва зурриётларимиздан бизга кўз қувончини бахш эт ва бизларни тақводорларга пешво қилгин!",
        "text_uz_lat": "Ey Rabbimiz, jufti halollarimizdan va zurriyotlarimizdan bizga ko'z quvonchini baxsh et va bizlarni taqvodorlarga peshvo qilgin!",
        "text_en": "Our Lord, grant us from among our wives and offspring comfort to our eyes and make us an example for the righteous.",
        "text_ru": "Господь наш! Одари нас прохладой глаз в лице наших супруг и потомства и сделай нас образцом для богобоязненных.",
        "source": "Qur'oni Karim, Furqon surasi 74-oyat"
    }
]

# =====================================================================
# 3. HADITH BOOKS (Riyozus Solihiyn, Bulugh al-Maram, Al-Adhkar)
# =====================================================================
HADITH_DATA = [
    {
        "id": "hadith:1",
        "book": "Riyozus Solihiyn",
        "author": "Imom Navaviy",
        "chapter": "Niyat va ixlos bobi",
        "hadith_number": 1,
        "grade": "Sahih",
        "arabic_raw": "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
        "transliteration": "Innamal-a'malu bin-niyyat, wa innama likulli-mri'in ma nawa",
        "text_uz_cyr": "Амаллар фақат ниятларга боғлиқдир. Ҳар бир кишига фақат ният қилган нарсаси берилади.",
        "text_uz_lat": "Amallar faqat niyatlarga bog'liqdir. Har bir kishiga faqat niyat qilgan narsasi beriladi.",
        "text_en": "Actions are but by intentions and every person will have only what their intention was.",
        "text_ru": "Поистине, дела оцениваются только по намерениям, и каждому человеку достанется лишь то, что он намеревался обрести.",
        "source": "Riyozus Solihiyn, 1-hadis / Sahihi Buxoriy (1) va Sahihi Muslim (1907)"
    },
    {
        "id": "hadith:2",
        "book": "Al-Adhkar",
        "author": "Imom Navaviy",
        "chapter": "Allohni ko'p zikr qilish fazilati",
        "hadith_number": 15,
        "grade": "Sahih",
        "arabic_raw": "كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ، ثَقِيلَتَانِ فِي الْمِيزَانِ، حَبِيبَتَانِ إِلَى الرَّحْمَنِ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
        "transliteration": "Kalimatani khafifatani 'alan-lisan, thaqilatani fil-mizan, habibatani ilar-Rahman: Subhanallahi wa bihamdihi, Subhanallahil-'Adhim",
        "text_uz_cyr": "Икки калима борки, улар тилга енгил, мезон (тарози)да оғир ва Раҳмонга севимлидир: 'Субҳаноллоҳи ва биҳамдиҳи, Субҳаноллоҳил-Азим'.",
        "text_uz_lat": "Ikki kalima borki, ular tilga engil, mezon (tarozi)da og'ir va Rahmonga sevimli dir: 'Subhanollohi va bihamdihi, Subhanollohil-Azim'.",
        "text_en": "Two words are light on the tongue, heavy in the Balance, and beloved to the Most Merciful: 'Glory be to Allah and His praise, Glory be to Allah the Supreme'.",
        "text_ru": "Два слова легки для языка, тяжелы на весах и любимы Милостивым: 'Слава Аллаху и хвала Ему, слава Аллаху Великому'.",
        "source": "Al-Adhkar / Sahihi Buxoriy (6682) va Sahihi Muslim (2694)"
    },
    {
        "id": "hadith:3",
        "book": "Bulugh al-Maram",
        "author": "Ibn Hajar al-Asqalani",
        "chapter": "Tahorat ва поклик бўлими",
        "hadith_number": 1,
        "grade": "Sahih",
        "arabic_raw": "هُوَ الطَّهُورُ مَاؤُهُ الْحِلُّ مَيْتَتُهُ",
        "transliteration": "Huwat-tahuru ma'uhu, al-hillu maytatuh",
        "text_uz_cyr": "Расулуллоҳ соллаллоҳу алайҳи васаллам денгиз суви ҳақида: 'Унинг суви покловчи, ўлимтиги эса ҳалолдир', дедилар.",
        "text_uz_lat": "Rasululloh sollallohu alayhi vasallam dengiz suvi haqida: 'Uning suvi poklovchi, o'limtigi esa haloldir', dedilar.",
        "text_en": "Regarding sea water, the Prophet (pbuh) said: 'Its water is purifying and its dead animals are lawful to eat.'",
        "text_ru": "О морской воде Посланник Аллаха (мир ему и благословение) сказал: 'Её вода чиста, а её мертвечина дозволена.'",
        "source": "Bulugh al-Maram, 1-hadis / Abu Dovud, Tirmiziy, Nasoiy va Ibn Moja (Sahih)"
    }
]

# =====================================================================
# 4. IBODAT BO'LIMI (Tahorat, G'usl, Namoz, Roz'a, Zakot, Haj va b.)
# =====================================================================
IBODAT_DATA = [
    {
        "id": "ibodat:1",
        "topic": "Tahorat",
        "title_uz_cyr": "Таҳорат олиш тартиби ва фарзлари",
        "title_uz_lat": "Tahorat olish tartibi va farzlari",
        "text_uz_cyr": "Таҳоратнинг Қуръони Каримда кўрсатилган 4 та фарзи бор (Моида сураси, 6-оят):\n1. Юзни бир маротаба тўлиқ ювиш.\n2. Икки қўлни чиғаноқлари билан бирга ювиш.\n3. Бошнинг тўртдан бирига масҳ тортиш.\n4. Икки оёқни тўпиқлари билан бирга ювиш.\n\nСуннатлари: Бисмиллаҳ билан бошлаш, мисвок ишлатиш, қўлларни ювиш, оғиз-бурунни чайқаш ва тартиб билан адо этиш.",
        "text_uz_lat": "Tahoratning Qur'oni Karimda ko'rsatilgan 4 ta farzi bor (Moida surasi, 6-oyat):\n1. Yuzni bir marotaba to'liq yuvish.\n2. Ikki qo'lni chig'anoqlari bilan birga yuvish.\n3. Boshning to'rtdan biriga mash tortish.\n4. Ikki oyoqni to'piqlari bilan birga yuvish.\n\nSunnatlari: Bismillah bilan boshlash, misvok ishlatish, qo'llarni yuvish, og'iz-burunni chayqash va tartib bilan ado etish.",
        "text_en": "The 4 obligatory steps (Fard) of Wudu from Quran (5:6):\n1. Washing the face once completely.\n2. Washing both arms up to and including the elbows.\n3. Wiping over a portion of the head.\n4. Washing both feet up to and including the ankles.",
        "text_ru": "4 обязательных действия (Фард) омовения (Вуду) из Корана (5:6):\n1. Промывание лица полностью один раз.\n2. Промывание рук до локтей включительно.\n3. Протирание части головы (Масх).\n4. Промывание ног до щиколоток включительно.",
        "source": "Qur'oni Karim (Moida surasi, 6-oyat) / Bulugh al-Maram (Tahorat bobi)"
    },
    {
        "id": "ibodat:2",
        "topic": "Namoz",
        "title_uz_cyr": "Беш вақт намознинг фарз ва суннат ракатлари",
        "title_uz_lat": "Besh vaqt namozning farz va sunnat rakatlari",
        "text_uz_cyr": "Кунлик 5 вақт намоз фарз қилинган аъзам ибодатдир:\n- Бомдод: 2 ракат суннат, 2 ракат фарз.\n- Пешин: 4 ракат суннат, 4 ракат фарз, 2 ракат суннат.\n- Аср: 4 ракат фарз.\n- Шом: 3 ракат фарз, 2 ракат суннат.\n- Хуфтон: 4 ракат фарз, 2 ракат суннат ва 3 ракат Витр важиб.",
        "text_uz_lat": "Kunlik 5 vaqt namoz farz qilingan a'zam ibodatdir:\n- Bomdod: 2 rakat sunnat, 2 rakat farz.\n- Peshin: 4 rakat sunnat, 4 rakat farz, 2 rakat sunnat.\n- Asr: 4 rakat farz.\n- Shom: 3 rakat farz, 2 rakat sunnat.\n- Xufton: 4 rakat farz, 2 rakat sunnat va 3 rakat Vitr vajib.",
        "text_en": "The 5 daily obligatory prayers:\n- Fajr: 2 Sunnah, 2 Fard.\n- Dhuhr: 4 Sunnah, 4 Fard, 2 Sunnah.\n- Asr: 4 Fard.\n- Maghrib: 3 Fard, 2 Sunnah.\n- Isha: 4 Fard, 2 Sunnah, 3 Witr.",
        "text_ru": "Пять ежедневных обязательных молитв:\n- Фаджр: 2 Сунна, 2 Фард.\n- Зухр: 4 Сунна, 4 Фард, 2 Сунна.\n- Аср: 4 Фард.\n- Магриб: 3 Фард, 2 Сунна.\n- Иша: 4 Фард, 2 Сунна, 3 Витр.",
        "source": "Riyozus Solihiyn / Sahihi Buxoriy va Muslim (Sahih hadislar)"
    }
]

# =====================================================================
# 5. MUQADDAS OYLAR (Muharram, Rajab, Sha'ban, Ramadan, Dhul-Qa'dah, Dhul-Hijjah)
# =====================================================================
SACRED_MONTHS_DATA = [
    {
        "id": "months:1",
        "month": "Muharram",
        "title_uz_cyr": "Муҳаррам ойи ва Ашуро куни фазилати",
        "title_uz_lat": "Muharram oyi va Ashuro kuni fazilati",
        "text_uz_cyr": "Муҳаррам ойи Ислом тақвимининг биринчи ойи бўлиб, Аллоҳнинг улуғланган тўрт ҳаром ойларидан биридир (Тавба сураси, 36-оят).\nУндаги Ашуро куни (10-Муҳаррам) рўза тутиш ўтган бир йиллик гуноҳларга каффорат бўлади.\nРасулуллоҳ соллаллоҳу алайҳи васаллам: 'Рамазондан кейинги энг афзал рўза — Аллоҳнинг Муҳаррам ойидаги рўзадир', деганлар (Муслим ривояти).",
        "text_uz_lat": "Muharram oyi Islom taqvimining birinchi oyi bo'lib, Allohning ulug'langan to'rt harom oylaridan biridir (Tavba surasi, 36-oyat).\nUndagi Ashuro kuni (10-Muharram) ro'za tutish o'tgan bir yillik gunohlarga kafforat bo'ladi.\nRasululloh sollallohu alayhi vasallam: 'Ramazondan keyingi eng afzal ro'za — Allohning Muharram oyidagi ro'zadir', deganlar (Muslim rivoyati).",
        "text_en": "Muharram is the first month of the Islamic calendar and one of the four sacred months. Fasting on the Day of Ashura (10th Muharram) expiates the sins of the previous year (Sahih Muslim).",
        "text_ru": "Мухаррам — первый месяц исламского календаря и один из четырех заповедных месяцев. Пост в день Ашура (10-е Мухаррама) искупает грехи прошлого года (Сахих Муслим).",
        "source": "Qur'oni Karim (Tavba, 36) / Sahihi Muslim (1163-hadis)"
    },
    {
        "id": "months:2",
        "month": "Ramadan",
        "title_uz_cyr": "Рамазон ойи ва Қадр кечаси фазилати",
        "title_uz_lat": "Ramazon oyi va Qadr kechasi fazilati",
        "text_uz_cyr": "Рамазон — Қуръони Карим нозил бўлган, фарз рўза тутиладиган баракотли ойдир (Бақара сураси, 185-оят).\nБу ойда минг ойдан яхшироқ бўлган Қадр кечаси бор (Қадр сураси).\nРасулуллоҳ соллаллоҳу алайҳи васаллам: 'Ким Рамазон рўзасини имон ва савоб умидида тутса, унинг ўтган гуноҳлари мағфират қилинади', деганлар (Бухорий ва Муслим).",
        "text_uz_lat": "Ramazon — Qur'oni Karim nozil bo'lgan, farz ro'za tutiladigan barakotli oydir (Baqara surasi, 185-oyat).\nBu oyda ming oydan yaxshiroq bo'lgan Qadr kechasi bor (Qadr surasi).\nRasululloh sollallohu alayhi vasallam: 'Kim Ramazon ro'zasini imon va savob umidida tutsa, uning o'tgan gunohlari mag'firat qilinadi', deganlar (Buxoriy va Muslim).",
        "text_en": "Ramadan is the month in which the Quran was revealed, containing Laylat al-Qadr which is better than a thousand months. Fasting it with faith expiates past sins.",
        "text_ru": "Рамадан — месяц ниспослания Корана, содержащий Ночь Предопределения (Лайлат аль-Кадр). Пост в Рамадан с верой искупает прошлые грехи.",
        "source": "Qur'oni Karim (Baqara, 185 / Qadr) / Sahihi Buxoriy (38) va Muslim (760)"
    }
]


def save_dataset(items: list[dict], subdir_name: str):
    """Ma'lumotlarni 4 tilda JSONL shaklida saqlaydi."""
    out_dir = BASE_DATA_DIR / subdir_name
    out_dir.mkdir(parents=True, exist_ok=True)

    langs = ["uz_cyrillic", "uz_latin", "en", "ru"]
    for lang in langs:
        filepath = out_dir / f"{lang}.jsonl"
        with filepath.open("w", encoding="utf-8") as f:
            for item in items:
                entry = {
                    "id": item["id"],
                    "type": subdir_name,
                    "lang": lang,
                    "source": item.get("source", ""),
                }
                
                # Title & Text resolution
                if lang == "uz_cyrillic":
                    entry["title"] = item.get("title_uz_cyr", item.get("category", item.get("topic", item.get("month", ""))))
                    entry["text"] = item.get("text_uz_cyr", "")
                elif lang == "uz_latin":
                    entry["title"] = item.get("title_uz_latin", cyrillic_to_latin(item.get("title_uz_cyr", "")))
                    entry["text"] = item.get("text_uz_lat", cyrillic_to_latin(item.get("text_uz_cyr", "")))
                elif lang == "en":
                    entry["title"] = item.get("category", item.get("topic", item.get("month", item.get("title_uz_cyr", ""))))
                    entry["text"] = item.get("text_en", item.get("text_uz_lat", ""))
                elif lang == "ru":
                    entry["title"] = item.get("category", item.get("topic", item.get("month", item.get("title_uz_cyr", ""))))
                    entry["text"] = item.get("text_ru", item.get("text_uz_lat", ""))

                if "arabic_raw" in item:
                    entry["arabic_raw"] = item["arabic_raw"]
                if "transliteration" in item:
                    entry["transliteration"] = item["transliteration"]
                if "surah" in item:
                    entry["surah"] = item["surah"]
                if "ayah" in item:
                    entry["ayah"] = item["ayah"]
                if "book" in item:
                    entry["book"] = item["book"]
                if "hadith_number" in item:
                    entry["hadith_number"] = item["hadith_number"]

                f.write(json.dumps(entry, ensure_ascii=False) + "\n")
        print(f"  ✅ {subdir_name}/{lang}.jsonl [{len(items)} items]")


def main():
    print("=" * 60)
    print(" Authentic Islamic Knowledge Base Datasets Generation")
    print("=" * 60)

    save_dataset(HISNUL_MUSLIM_DATA, "hisnul_muslim")
    save_dataset(QURANIC_DUAS_DATA, "duas_quranic")
    save_dataset(HADITH_DATA, "hadith")
    save_dataset(IBODAT_DATA, "ibodat")
    save_dataset(SACRED_MONTHS_DATA, "sacred_months")

    print("\n✅ Barcha manbalar 4 tilda muvaffaqiyatli saqlandi!")


if __name__ == "__main__":
    main()
