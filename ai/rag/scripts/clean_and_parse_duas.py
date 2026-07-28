#!/usr/bin/env python3
"""
"Muqaddas Oylar va Mustajob Duolar" kitobini mukammal tozalash va structured JSONL formatga o'tkazish skripti.

1. OCR da ц/Ц bo'lib qolgan qolgan barcha 'қ'/'Қ' harflarini tiklaydi
2. ii/i simvollarini to'g'ri Kirill harflariga o'tkazadi
3. O'zbek tili lug'atiga ko'ra so me'yorlashtiradi
4. Kitob sahifalari ustidagi takroriy sarlavhalarni to'liq olib tashlaydi
5. Uzbek Kirill va Uzbek Lotin JSONL fayllarini ideal sifatda yaratadi
"""

import json
import re
import sys
from pathlib import Path

# Skript papkasidagi cyrillic_to_latin modulni import qilish
sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin


KNOWN_CHAPTERS = [
    ("МУҚАДДИМА", "Muqaddima"),
    ("ДУО ҲАҚИДА КУР'кОМИ КАРИМДА КЕЛГАН ОЯТЛАР", "Duo haqida Qur'oni Karimda kelgan oyatlar"),
    ("ДУО ҲАҚИДА ҚУРЪОНИ КАРИМДА КЕЛГАН ОЯТЛАР", "Duo haqida Qur'oni Karimda kelgan oyatlar"),
    ("ҚУРЪОНИЙ ДУОЛАР", "Qur'oniy duolar"),
    ("ДУОНИНГ ФАЗИЛАТИ ҲАҚИДА ПАЙҒАМБАРИМИЗДАН РИВОЯТ КИЛИШЛИ ҲДДИСЛАР", "Duoning fazilati haqida Payg'ambarimizdan rivoyat qilingan hadislar"),
    ("ДУОНИНГ ФАЗИЛАТИ ҲАҚИДА ПАЙҒАМБАРИМИЗДАН РИВОЯТ ҚИЛИНГАН ҲАДИСЛАР", "Duoning fazilati haqida Payg'ambarimizdan rivoyat qilingan hadislar"),
    ("РАСУЛУЛЛОҲНИНГ ДУО ҚИЛИШ УСУЛЛАРИ", "Rasulullohning duo qilish usullari"),
    ("ДУОНИНГ ОДОБИ", "Duoning odobi"),
    ("ДУОСИ КАБУЛ БҲЛАДИГАН И И СО И Л А Р", "Duosi qabul bo'ladigan insonlar"),
    ("ДУОСИ ҚАБУЛ БЎЛАДИГАН ИНСОНЛАР", "Duosi qabul bo'ladigan insonlar"),
    ("ПАЙКАМ ВАРИМИЗГА (1АЛОВОТ", "Payg'ambarimizga salovot aytishning fazilati"),
    ("ПАЙҒАМБАРИМИЗГА САЛОВОТ АЙТИШНИНГ ФАЗИЛАТИ", "Payg'ambarimizga salovot aytishning fazilati"),
    ("САЛО КОТЛ А Р", "Salovotlar"),
    ("САЛОВОТЛАР", "Salovotlar"),
    ("ФАЗИЛАТИ ЖУДА БУЮК (^ Л О В О Т", "Fazilati juda buyuk salovot"),
    ("САЛОВОТИ ТАВАССУЛ", "Salovoti Tavassul"),
    ("САЛОВОТИ С И Р Р", "Salovoti Sirr"),
    ("САЛОВОТИ УММИЙЙА", "Salovoti Ummiyya"),
    ("СОЛ АТА И ТУН А Ж Ж И Н А", "Solatan Tunajjina"),
    ("КИСКА САЛОВОТЛАР", "Qisqa salovotlar"),
    ("ҚИСҚА САЛОВОТЛАР", "Qisqa salovotlar"),
    ("СОЛАТАИ МАРИЯ", "Solatan Nariya"),
    ("ҲАЖ ВА УМ РАДА САЛОТУ САЛОМ", "Haj va Umrada salotu salom"),
    ("САЛОВОТ ҲАКИДА Ҳ И КО ЯЛ А Р", "Salovot haqida hikoyalar"),
    ("САЛОВОТ ҲАҚИДА ҲИКОЯЛАР", "Salovot haqida hikoyalar"),
    ("ДУОЛАР САЛОВОТ БИЛАН КАБУЛ БЎ ЛА Д И", "Duolar salovot bilan qabul bo'ladi"),
    ("ДУОЛАР КАБУЛ БЎЛИШИГА ОИД Ҳ И КО Я Л А Р", "Duolar qabul bo'lishiga oid hikoyalar"),
    ("МУБОРАК ВАҚТЛАР", "Muborak vaqtlar"),
    ("РА ЖАБ О Й И", "Rajab oyi"),
    ("РАЖАБ ОЙИ", "Rajab oyi"),
    ("РАЖАБ ОЙИДА ЎКИЛАДИГАН ДУОЛАР", "Rajab oyida o'qiladigan duolar"),
    ("РАЖАБ ОЙИДА ЎҚИЛАДИГАН ДУОЛАР", "Rajab oyida o'qiladigan duolar"),
    ("РАЖА Б ОЙИ И А М О ЗИ", "Rajab oyi namozi"),
    ("РАЖАБ ОЙИ НАМОЗИ", "Rajab oyi namozi"),
    ("РАКОЙ И Б КЕЧ АСИ", "Rag'oyib kechasi"),
    ("РАҒОЙИБ КЕЧАСИ", "Rag'oyib kechasi"),
    ("ИСРО ВА М Е Ъ Р О Ж", "Isro va Me'roj"),
    ("ИСРО ВА МЕЪРОЖ", "Isro va Me'roj"),
    ("М КБРОЖ КЕЧАСИДА ЎКИЛАДИГАП Д У О", "Me'roj kechasida o'qiladigan duo"),
    ("МЕЪРОЖ КЕЧАСИДА ЎҚИЛАДИГАН ДУО", "Me'roj kechasida o'qiladigan duo"),
    ("ШАЪБОН ОЙИ", "Sha'bon oyi"),
    ("БАРОАТ КЕЧА СИ", "Baroat kechasi"),
    ("БАРОАТ КЕЧАСИ", "Baroat kechasi"),
    ("БАРОЛТ КЕЧАСИДА ЎҚИЛАДИГАИ ДУО", "Baroat kechasida o'qiladigan duo"),
    ("ТАСБЕҲ ИАМОЗИ", "Tasbeh namozi"),
    ("ТАСБЕҲ НАМОЗИ", "Tasbeh namozi"),
    ("РЛМЛЗОН ОЙИИИПГ ФАЗИЛАТИ", "Ramazon oyining fazilati"),
    ("РАМАЗОН ОЙИНИНГ ФАЗИЛАТИ", "Ramazon oyining fazilati"),
    ("И Ф ТО Р ДУОЛАРИ", "Iftor duolari"),
    ("ИФТОР ДУОЛАРИ", "Iftor duolari"),
    ("РЛМЛЗОН ДУОСИ", "Ramazon duosi"),
    ("РАМАЗОН ДУОСИ", "Ramazon duosi"),
    ("ҚАДР КЕЧ А С И", "Qadr kechasi"),
    ("ҚАДР КЕЧАСИ", "Qadr kechasi"),
    ("ШАВВОЛ О Й И", "Shavvol oyi"),
    ("ЗУЛКАЪДА ОЙИ, ЗУЛҲИЖЖА О Й И", "Zulqa'da oyi, Zulhijja oyi"),
    ("КАЙРАМ КУНЛАРИДА ҚАБР ЗИЁРАТИ", "Hayit kunlarida qabr ziyorati"),
    ("АРАФА КУНИ", "Arafa kuni"),
    ("САФАР ОЙИ ДУОСИ", "Safar oyi duosi"),
    ("РОБИЪУЛ АВВАЛИИНГ ЎН ИККИНЧИСИ", "Mavludun Nabiy"),
    ("МАВЛУДУН НАБИЙ", "Mavludun Nabiy"),
    ("МУХАРРАМ О Й И", "Muharram oyi"),
    ("АШУРО КУНИ", "Ashuro kuni"),
    ("А1ПУРО КУПИ ЎҚИЛАДИГЛИ ДУОЛАР", "Ashuro kuni o'qiladigan duolar"),
    ("КУМА КУИИПИНГ ФАЗИЛАТИ", "Juma kunining fazilati"),
    ("ЖУМА КУНИНИНГ ФАЗИЛАТИ", "Juma kunining fazilati"),
    ("ОЯТУЛ КУРСИЙ ВА УНИНГ ФАЗИЛАТИ", "Oyatul Kursiy va uning fazilati"),
    ("ЙАСИН ДУОСИ", "Yasin duosi"),
    ("ҲАШР СУРАСИНИНГ ФАЗИЛАТИ", "Hashr surasining fazilati"),
    ("МУЛК СУРАСИНИНГ ФАЗИЛАТИ", "Mulk surasining fazilati"),
    ("ВОКГА СУРАСИПИНГ ФАЗИЛАТИ", "Voqea surasining fazilati"),
    ("ВОҚЕА СУРАСИНИНГ ФАЗИЛАТИ", "Voqea surasining fazilati"),
    ("ИХЛОС СУРАСИНИПГ ФАЗИЛАТИ", "Ixlos surasining fazilati"),
    ("ИХЛОС СУРАСИНИНГ ФАЗИЛАТИ", "Ixlos surasining fazilati"),
    ("ФАЛАК , НОС СУРАЛАРИ ВА УЛАРМИМГ Ф Л З И Л А Т И", "Falaq, Nos suralari va ularning fazilati"),
    ("ФАЛАҚ, НОС СУРАЛАРИ ВА УЛАРНИНГ ФАЗИЛАТИ", "Falaq, Nos suralari va ularning fazilati"),
    ("РАСУЛ УЛ ЛОХИ И ИГ ДУНЁДАГИ ЭИ Г СЎ Ш ТИ Д У О Л А Р И", "Rasulullohning dunyodagi eng sevgan duolari"),
    ("РАСУЛУЛЛОҲНИНГ ДУНЁДАГИ ЭНГ СЕВГАН ДУОЛАРИ", "Rasulullohning dunyodagi eng sevgan duolari"),
    ("ИАВИЙ (c.ii.u.) ПИНГ КМ011ЛИКЛАРДАН И АИОҲ ТИ ЛЛШ Д У О Л А Р И", "Nabiyning ko'ngilsizliklardan panoh tilash duolari"),
    ("НАМОЗДАМ СЎПГ ЎҚИЛАДИГАИ Д У О", "Namozdan so'ng o'qiladigan duo"),
    ("НАМОЗДАН СЎНГ ЎҚИЛАДИГАН ДУО", "Namozdan so'ng o'qiladigan duo"),
    ("ИСМИ МУ я ; ИР Д У О С И", "Ismi Mujir duosi"),
    ("ИСМИ МУЖИР ДУОСИ", "Ismi Mujir duosi"),
    ("ИСМИ РОҲМАИ Д У О С И", "Ismi Rohman duosi"),
    ("ИСМИ РОҲМАН ДУОСИ", "Ismi Rohman duosi"),
    ("ИСМИ АЪЗАМ", "Ismi A'zam"),
    ("Ф ЛХРИДДИП РО З ИЙ ТАВСИЯ ҚИЛГЛИ Д У О Л А Р", "Faxriddin Roziy tavsiya qilgan duolar"),
    ("Е'ГГИ КУН МОКАЙ И ИДА ЎҚИЛАДИГЛИ Д У О Л А Р", "Etti kun muddatida o'qiladigan duolar"),
    ("ЮНУС АЛАЙҲИССАЛОМ ҚИССАСИ", "Yunus alayhissalom qissasi"),
    ("НАЗАР ДУОЛАР И", "Nazar duolari"),
    ("НАЗАР ДУОЛАРИ", "Nazar duolari"),
    ("ИСТИХОРА ДУОСИ ВА И А М О ЗИ", "Istixora duosi va namozi"),
    ("ИСТИХОРА ДУОСИ ВА НАМОЗИ", "Istixora duosi va namozi"),
    ("И И К О Ҳ ДУОЛАРИ", "Nikoh duolari"),
    ("НИКОҲ ДУОЛАРИ", "Nikoh duolari"),
    ("АЛЛОҲДАИ КЕНГ Р И З Қ ИСГАГАНЛАР ЎҚИЙДИГАН ДУОЛАР НА КИЛАДИГАН АМ АЛЛАР", "Allohdan keng rizq istaganlar o'qiyydigan duolar"),
    ("И СТИ ҒФ О Р Д У О Л А Р И", "Istighfor duolari"),
    ("ИСТИҒФОР ДУОЛАРИ", "Istighfor duolari"),
    ("ТАВБА Д У О СИ", "Tavba duosi"),
    ("ТАВБА ДУОСИ", "Tavba duosi"),
    ("Ж АН НАТРА КИРИ III ВА ЖЛҲЛНИАМДЛН ПЛНОҲ ТИЛЛШ ДУОСИ", "Jannatga kirish va jahannamdan panoh tilash duosi"),
    ("М ЎМ ИПЛАРНИПГ Д У О Л А Р И", "Mo'minlarning duolari"),
    ("МЎМИНЛАРНИНГ ДУОЛАРИ", "Mo'minlarning duolari"),
    ("ТОНГДА ЎК ИЛ АД И ГАМ Д У О Л А Р", "Tongda o'qiladigan duolar"),
    ("ТОНГДА ЎҚИЛАДИГАН ДУОЛАР", "Tongda o'qiladigan duolar"),
    ("БОМДОД ИАМОЗИ ДАН СЎПГ ЎҚИЛЛДИГА! I Д У О Л А Р", "Bomdod namozidan so'ng o'qiladigan duolar"),
    ("О ҚШ ОМ ЎҚИЙДИГАМ ДУО", "Oqshom o'qiladigan duo"),
    ("ОҚШОМ ЎҚИЛАДИГАН ДУО", "Oqshom o'qiladigan duo"),
    ("АЗОМ Д У О СИ", "Azon duosi"),
    ("О!) Д А РД Н И Н Г Ш И Ф О С И", "Og'riqlarning shifosi"),
    ("ОҒРИҚЛАРНИНГ ШИФОСИ", "Og'riqlarning shifosi"),
    ("ТАҲОРАТ ДУОЛАРИ", "Tahorat duolari"),
    ("IIА Й FA М БАРИ М И З 11И И Г ТА СБЕҲ Д У О СИ", "Payg'ambarimizning tasbeh duosi"),
    ("АЛЛОҲ СЕВГАИ ТЎРТ К А Л О М", "Alloh sevgan to'rt kalom"),
    ("А ЛЛО Ҳ ТАОЛО СЕВГАИ И К К И К А Л И М А", "Alloh taolo sevgan ikki kalom"),
    ("ЭН Г ХАЙРЛИ Д У О", "Eng xayrli duo"),
    ("ҲАЖ ВА УМ РА Д У О Л А Р И", "Haj va Umra duolari"),
    ("АСМАУЛ ҲУСИА (АЛЛОҲНИИГ 00 ГЎЗАЛ ИСМИ)", "Asmaul Husna (Allohning 99 go'zal ismi)"),
    ("АСМАУЛ ҲУСНА (АЛЛОҲНИНГ 99 ГЎЗАЛ ИСМИ)", "Asmaul Husna (Allohning 99 go'zal ismi)"),
    ("ПАЙРАМБАРИМИЗ! IИ11Г МУБОРАК ИСМ ЛАРИ", "Payg'ambarimizning muborak ismlari"),
    ("ОЛТИ ДИМИЙ КА ЛИ М А", "Olti diniy kalima"),
    ("ИМОИ И МУЖМАЛ, ИМОНИ М УФАССАЛ", "Imoni Mujmal, Imoni Mufassal"),
]


def clean_cyrillic_raw(text: str) -> str:
    """OCR da buzilgan Kirillicha matnni tizimli to'g'rilaydi."""
    # 1. ц/Ц -> қ/Қ (O'zbek tili duolar kitobida ц harfi o'rnida deyarlik har doim қ keladi)
    text = text.replace("ц", "қ").replace("Ц", "Қ")

    # 2. ii / i simvollari va OCR xatolarini to'g'rilash
    text = re.sub(r"\biiисои\b", "инсон", text)
    text = re.sub(r"\biiмои\b", "имон", text)
    text = re.sub(r"\biiжобат\b", "ижобат", text)
    text = re.sub(r"\biiкки\b", "икки", text)
    text = re.sub(r"\biiш\b", "ish", text)
    text = re.sub(r"\bсттинчнсн\b", "yettinchisi", text)
    text = re.sub(r"\bистиғ\^юр\b", "истиғфор", text)
    text = re.sub(r"\bистиғ\^фор\b", "истиғфор", text)
    text = re.sub(r"\bАллоҳшшг\b", "Аллоҳнинг", text)
    text = re.sub(r"\bРасулуллоҳнннг\b", "Расулуллоҳнинг", text)
    text = re.sub(r"\bПайғамбаримизнннг\b", "Пайғамбаримизнинг", text)
    text = re.sub(r"\bбизиинг\b", "бизнинг", text)
    text = re.sub(r"\bуиинг\b", "унинг", text)
    text = re.sub(r"\bх'чун\b", "учун", text)
    text = re.sub(r"\byijTisiaraii\b", "унутмаган", text)

    # 3. Sahifa sarlavhalari (running header) patterns
    headers = [
        r"M\s*i\s*q\s*a\s*d\s*d\s*a\s*s\s*o\s*y\s*l\s*a\s*r\s*v\s*a\s*m\s*u\s*s\s*t\s*a\s*j\s*o\s*b\s*d\s*u\s*o\s*l\s*a\s*r",
        r"MuKn<\)<>\(ic\s*oi/iai\)\s*va\s*m\s*i\s*ст\s*а\s*ж\s*о\s*б\s*ди\s*о\s*ла\s*р",
        r"М\s*у\s*қ\s*а\s*д\s*д\s*а\s*с\s*о\s*й\s*л\s*а\s*р\s*в\s*а\s*м\s*у\s*с\s*т\s*а\s*ж\s*о\s*б\s*д\s*у\s*о\s*л\s*а\s*р",
        r"М\s*и\s*қ\s*а\s*д\s*д\s*а\s*с\s*о\s*й\s*л\s*а\s*р\s*в\s*а\s*м\s*и\s*с\s*т\s*а\s*ж\s*о\s*б\s*д\s*и\s*о\s*л\s*а\s*р",
        r"М\s*и\s*к\s*п\s*Л\s*дас\s*о\s*й\s*л\s*а\s*р\s*па\s*м\s*и\s*с\s*т\s*а\s*ж\s*о\s*б\s*д\s*и\s*о\s*л\s*а\s*р",
        r"©\s*Дилфуза\s*Сарварова",
        r"\"Toshkent\s*islom\s*universiteti\"\s*nashriyot-matbaa",
        r"Тошкент\s*ислом\s*университети",
    ]
    for h in headers:
        text = re.sub(h, " ", text, flags=re.IGNORECASE)

    # 4. Keraksiz belgilar
    text = re.sub(r"S1>\\", "", text)
    text = re.sub(r"«Z\^", "", text)
    text = re.sub(r"[\x00-\x1f\x7f-\x9f]", "", text)
    text = re.sub(r"[{}[\]|\\]", "", text)

    text = re.sub(r"\s+", " ", text).strip()
    return text


def clean_latin_post(text: str) -> str:
    """Lotin transliteratsiyasidan keyingi so'z xatolarini to'g'rilaydi."""
    replacements = [
        (r"\btsabul\b", "qabul"),
        (r"\btsabulini\b", "qabulini"),
        (r"\btsabulni\b", "qabulni"),
        (r"\btsabuldan\b", "qabuldan"),
        (r"\btsil\b", "qil"),
        (r"\btsiling\b", "qiling"),
        (r"\btsilinglar\b", "qilinglar"),
        (r"\btsilindi\b", "qilindi"),
        (r"\btsilinsa\b", "qilinsa"),
        (r"\btsilish\b", "qilish"),
        (r"\btsilishni\b", "qilishni"),
        (r"\btsilib\b", "qilib"),
        (r"\btsilishi\b", "qilishi"),
        (r"\btsilayotgan\b", "qilayotgan"),
        (r"\btsilur\b", "qilur"),
        (r"\btsiladi\b", "qiladi"),
        (r"\btsiladigan\b", "qiladigan"),
        (r"\btsilsin\b", "qilsin"),
        (r"\btsilsa\b", "qilsa"),
        (r"\btsilding\b", "qilding"),
        (r"\btsildim\b", "qildim"),
        (r"\btsildik\b", "qildik"),
        (r"\btsadar\b", "qadar"),
        (r"\btsalb\b", "qalb"),
        (r"\btsalbi\b", "qalbi"),
        (r"\btsattiq\b", "qattiq"),
        (r"\btsayta\b", "qayta"),
        (r"\btsavm\b", "qavm"),
        (r"\btsavat\b", "qavat"),
        (r"\btsudrat\b", "qudrat"),
        (r"\btsuvonch\b", "quvonch"),
        (r"\btsul\b", "qul"),
        (r"\btsulida\b", "qo'lida"),
        (r"\btsuy\b", "qo'y"),
        (r"\btsuyida\b", "quyida"),
        (r"\btsushib\b", "tushib"),
        (r"\btsabr\b", "qabr"),
        (r"\btsabrida\b", "qabrida"),
        (r"\bno-\s*loyits\b", "noloyiq"),
        (r"\bnoloyits\b", "noloyiq"),
        (r"\b5'ta\b", "o'z"),
        (r"\bUrtaashchagi\b", "o'rtasidagi"),
        (r"\bD\^\s*onn\b", "Duoni"),
        (r"\bbxlnsh\b", "bo'lish"),
        (r"\bkxnayishn\b", "ko'payishi"),
        (r"\bb\^’lishi\b", "bo'lishi"),
        (r"\bb\^’\b", "bo'"),
        (r"\bk\^’\b", "ko'"),
        (r"\buch\)i\b", "uchun"),
        (r"\b\}iing\b", "Uning"),
        (r"\bs\^rasining\b", "surasining"),
        (r"\bvatst\b", "vaqt"),
        (r"\bealovot\b", "salovot"),
        (r"\bealovotni\b", "salovotni"),
        (r"\bealovotlar\b", "salovotlar"),
        (r"\bealovotiga\b", "salovotiga"),
    ]
    for pat, rep in replacements:
        text = re.sub(pat, rep, text, flags=re.IGNORECASE)

    text = re.sub(r"\s+", " ", text).strip()
    return text


def parse_and_clean_dua_book(file_path: Path) -> tuple[list[dict], list[dict]]:
    """Kitobni o'qiydi, tozalaydi va mantiqiy bo'limlarga ajratib Kirill/Lotin JSONL beradi."""
    raw_lines = file_path.read_text(encoding="utf-8", errors="ignore").splitlines()

    cleaned_lines = []
    for line in raw_lines:
        line_clean = line.strip()
        if not line_clean:
            continue
        if any(h in line_clean.lower() for h in [
            "муқаддас ойлар ва мустажоб дуолар",
            "муҳтарам китобхон",
            "дилфуза сарварова",
            "тошкент ислом университета",
            "нашриёт-матбаа",
            "isbn",
            "кбк",
            "бичими",
            "нашр т.",
        ]):
            continue
        cleaned_lines.append(line_clean)

    sections = []
    current_title_cyr = "МУҚАДДИМА"
    current_title_lat = "Muqaddima"
    current_lines = []

    for line in cleaned_lines:
        matched_chapter = None
        for cyr_pat, lat_title in KNOWN_CHAPTERS:
            if cyr_pat in line or line == cyr_pat:
                matched_chapter = (cyr_pat, lat_title)
                break

        if matched_chapter:
            if current_lines:
                block_raw = " ".join(current_lines)
                clean_cyr = clean_cyrillic_raw(block_raw)
                if len(clean_cyr) > 40:
                    sections.append({
                        "title_cyr": current_title_cyr,
                        "title_lat": current_title_lat,
                        "text_cyr": clean_cyr
                    })
                current_lines = []
            current_title_cyr = matched_chapter[0]
            current_title_lat = matched_chapter[1]
        else:
            current_lines.append(line)

        if len(" ".join(current_lines)) > 1000:
            block_raw = " ".join(current_lines)
            clean_cyr = clean_cyrillic_raw(block_raw)
            if len(clean_cyr) > 40:
                sections.append({
                    "title_cyr": current_title_cyr,
                    "title_lat": current_title_lat,
                    "text_cyr": clean_cyr
                })
            current_lines = []

    if current_lines:
        block_raw = " ".join(current_lines)
        clean_cyr = clean_cyrillic_raw(block_raw)
        if len(clean_cyr) > 40:
            sections.append({
                "title_cyr": current_title_cyr,
                "title_lat": current_title_lat,
                "text_cyr": clean_cyr
            })

    cyrillic_entries = []
    latin_entries = []

    for idx, sec in enumerate(sections):
        dua_id = f"dua:{idx+1}"

        cyrillic_entries.append({
            "id": dua_id,
            "title": clean_cyrillic_raw(sec["title_cyr"]),
            "text": sec["text_cyr"],
            "type": "dua",
            "source": "Muqaddas Oylar va Mustajob Duolar",
            "lang": "uz_cyrillic"
        })

        lat_text = cyrillic_to_latin(sec["text_cyr"])
        lat_text = clean_latin_post(lat_text)

        latin_entries.append({
            "id": dua_id,
            "title": sec["title_lat"],
            "text": lat_text,
            "type": "dua",
            "source": "Muqaddas Oylar va Mustajob Duolar",
            "lang": "uz_latin"
        })

    return cyrillic_entries, latin_entries


def main():
    input_file = Path("/home/sobirov/Downloads/868692365-Muqaddas-Oylar-Va-Mustajob-Duolar-Uzsmart-uz.txt")
    out_dir = Path("/home/sobirov/quran_reminder/v0-quran-lock-screen/ai/rag/data/duas")

    if not input_file.exists():
        print(f"❌ Fayl topilmadi: {input_file}")
        sys.exit(1)

    cyr_entries, lat_entries = parse_and_clean_dua_book(input_file)

    out_dir.mkdir(parents=True, exist_ok=True)

    cyr_out = out_dir / "uz_cyrillic.jsonl"
    with cyr_out.open("w", encoding="utf-8") as f:
        for entry in cyr_entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    print(f"✅ {len(cyr_entries)} ta tozalangan duolar/bo'limlar → {cyr_out}")

    lat_out = out_dir / "uz_latin.jsonl"
    with lat_out.open("w", encoding="utf-8") as f:
        for entry in lat_entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    print(f"✅ {len(lat_entries)} ta tozalangan duolar/bo'limlar → {lat_out}")


if __name__ == "__main__":
    main()
