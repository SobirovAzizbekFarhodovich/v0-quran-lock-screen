#!/usr/bin/env python3
import json
import os
import re
import time
import requests
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
QURAN_FILE = ROOT / "app" / "src" / "main" / "assets" / "quran" / "verses.json"
HADITH_FILE = ROOT / "app" / "src" / "main" / "assets" / "hadith" / "hadith_index.json"
OUT_FILE = ROOT / "app" / "src" / "main" / "assets" / "tadabbur" / "source_highlights.json"

CYRILLIC_TO_LATIN = str.maketrans({
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "yo",
    "ж": "j", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "x", "ц": "s", "ч": "ch", "ш": "sh", "щ": "sh", "ъ": "",
    "ь": "", "э": "e", "ю": "yu", "я": "ya", "ў": "o", "қ": "q", "ғ": "g",
    "ҳ": "h",
})

STOP_WORDS = {
    "men", "meni", "menga", "sen", "siz", "bilan", "uchun", "ham", "edi",
    "ekan", "qilib", "qildi", "deb", "bir", "shu", "bu", "u", "va", "yoki",
    "lekin", "agar", "juda", "nima", "qanday", "bugun", "hozir", "ular",
    "uning", "alla", "olloh", "alloh", "taolo", "rasululloh", "sallallohu",
    "alayhi", "sallam", "dedilar", "rivoyat", "qiladilar", "qiladi",
    "aytdilar", "deganlar", "ibn", "abu", "raziyallohu", "anhu", "anho",
}

# --- Concept synonym map: maps common Uzbek words/stems to profile labels ---
# (Existing rules kept as fallback for speed or lack of API access)
CONCEPT_MAP: dict[str, list[str]] = {
    # sabr va taskin
    "sinov": ["sabr va taskin"],
    "sinamoq": ["sabr va taskin"],
    "balo": ["sabr va taskin"],
    "chiday": ["sabr va taskin"],
    "chida": ["sabr va taskin"],
    "bardosh": ["sabr va taskin"],
    "toqat": ["sabr va taskin"],
    "ozor": ["sabr va taskin"],
    "azob": ["sabr va taskin"],
    "kulfat": ["sabr va taskin"],
    "yiglash": ["sabr va taskin", "olim va qaygu"],
    "yigla": ["sabr va taskin", "olim va qaygu"],
    "kozyoshi": ["sabr va taskin", "olim va qaygu"],
    "tasalli": ["sabr va taskin"],
    "taskin": ["sabr va taskin"],
    "ogirlik": ["sabr va taskin"],
    "sinovga": ["sabr va taskin"],
    "achchiq": ["sabr va taskin"],
    "gussaga": ["sabr va taskin"],
    "gussa": ["sabr va taskin"],
    "alamzada": ["sabr va taskin"],
    # tavba va magfirat
    "kechirim": ["tavba va magfirat"],
    "afv": ["tavba va magfirat", "gazab va kelishmovchilik"],
    "istigfor": ["tavba va magfirat"],
    "yomonlik": ["tavba va magfirat"],
    "xato": ["tavba va magfirat"],
    "aybdor": ["tavba va magfirat"],
    "poklan": ["tavba va magfirat"],
    "poklik": ["tavba va magfirat", "harom istak va nafs"],
    "tiyilish": ["tavba va magfirat", "harom istak va nafs"],
    # umid va xavotir
    "jannat": ["umid va xavotir"],
    "dozax": ["umid va xavotir"],
    "jahannam": ["umid va xavotir"],
    "oxirat": ["umid va xavotir", "olim va qaygu"],
    "hidoyat": ["umid va xavotir", "qaror va maslahat"],
    "najot": ["umid va xavotir"],
    "faloh": ["umid va xavotir"],
    "yordam": ["umid va xavotir"],
    "panoh": ["umid va xavotir", "yomon tush va qorquv"],
    "himoya": ["umid va xavotir"],
    # rizq va ish
    "tijorat": ["rizq va ish"],
    "infaq": ["rizq va ish"],
    "ehson": ["rizq va ish", "ota-ona va oila"],
    "boylik": ["rizq va ish", "shukr va qanoat"],
    "faqir": ["rizq va ish"],
    "miskin": ["rizq va ish"],
    "berish": ["rizq va ish"],
    "olish": ["rizq va ish"],
    "moddiy": ["rizq va ish"],
    # ota-ona va oila
    "bolalar": ["ota-ona va oila"],
    "bola": ["ota-ona va oila"],
    "farzand": ["ota-ona va oila"],
    "valida": ["ota-ona va oila"],
    "hurmat": ["ota-ona va oila"],
    "tarbiya": ["ota-ona va oila"],
    "urugchilik": ["ota-ona va oila"],
    # namoz va ibodat
    "tahorat": ["namoz va ibodat"],
    "vuzu": ["namoz va ibodat"],
    "qiyom": ["namoz va ibodat"],
    "tahajjud": ["namoz va ibodat"],
    "sunnat": ["namoz va ibodat"],
    "faroyiz": ["namoz va ibodat"],
    "azon": ["namoz va ibodat"],
    "takbir": ["namoz va ibodat"],
    "tilovat": ["namoz va ibodat"],
    "quron": ["namoz va ibodat"],
    # gazab va kelishmovchilik
    "xushunat": ["gazab va kelishmovchilik"],
    "urush": ["gazab va kelishmovchilik"],
    "dushmanlik": ["gazab va kelishmovchilik"],
    "adovat": ["gazab va kelishmovchilik"],
    "muloyim": ["gazab va kelishmovchilik"],
    "yumshoq": ["gazab va kelishmovchilik"],
    "rozilik": ["gazab va kelishmovchilik"],
    # kasallik va shifo
    "dorixona": ["kasallik va shifo"],
    "jarohat": ["kasallik va shifo"],
    "iloj": ["kasallik va shifo"],
    "mariz": ["kasallik va shifo"],
    "bexos": ["kasallik va shifo"],
    "sogayish": ["kasallik va shifo"],
    "ruqya": ["kasallik va shifo"],
    "tibbiy": ["kasallik va shifo"],
    # olim va qaygu
    "motam": ["olim va qaygu"],
    "azadorlik": ["olim va qaygu"],
    "kafan": ["olim va qaygu"],
    "dafn": ["olim va qaygu"],
    "marsum": ["olim va qaygu"],
    "hayot": ["olim va qaygu"],
    "tirik": ["olim va qaygu"],
    # nikoh va munosabat
    "turmush": ["nikoh va munosabat"],
    "uylanish": ["nikoh va munosabat"],
    "mahr": ["nikoh va munosabat"],
    "toyin": ["nikoh va munosabat"],
    "mehr": ["nikoh va munosabat"],
    "muhabbat": ["nikoh va munosabat"],
    "sevgi": ["nikoh va munosabat"],
    "juftlik": ["nikoh va munosabat"],
    # harom istak va nafs
    "vasvasa": ["harom istak va nafs"],
    "hirs": ["harom istak va nafs"],
    "nafsoni": ["harom istak va nafs"],
    "qoniqish": ["harom istak va nafs"],
    "gunohkor": ["harom istak va nafs", "tavba va magfirat"],
    # zulm va adolatsizlik
    "jabr": ["zulm va adolatsizlik"],
    "tazyiq": ["zulm va adolatsizlik"],
    "huquqbuzarlik": ["zulm va adolatsizlik"],
    "jazo": ["zulm va adolatsizlik"],
    # hasad va solishtirish
    "havas": ["hasad va solishtirish"],
    "orzu": ["hasad va solishtirish"],
    "tamag": ["hasad va solishtirish"],
    # ilm va oqish
    "hikmati": ["ilm va oqish"],
    "tadabbur": ["ilm va oqish"],
    "tafakkur": ["ilm va oqish"],
    "fahm": ["ilm va oqish"],
    "aqlli": ["ilm va oqish"],
    # qaror va maslahat
    "mushavara": ["qaror va maslahat"],
    "istixora": ["qaror va maslahat"],
    "istishor": ["qaror va maslahat"],
    "tanlash": ["qaror va maslahat"],
    # shukr va qanoat
    "minnatdor": ["shukr va qanoat"],
    "rozi": ["shukr va qanoat"],
    "rozilik": ["shukr va qanoat"],
    "xurram": ["shukr va qanoat"],
    # til va giybat
    "fitna": ["til va giybat"],
    "igvo": ["til va giybat"],
    "bohton": ["til va giybat"],
    "gummon": ["til va giybat"],
    # niyat va ixlos
    "samimiy": ["niyat va ixlos"],
    "riyokorlik": ["niyat va ixlos"],
    "riyo": ["niyat va ixlos"],
    "amal": ["niyat va ixlos"],
    # yomon tush va qorquv
    "tush": ["yomon tush va qorquv"],
    "karohat": ["yomon tush va qorquv"],
    # charchoq va yengillik
    "holsizlik": ["charchoq va yengillik"],
    "toliqish": ["charchoq va yengillik"],
    "dam olish": ["charchoq va yengillik"],
}


def profile(
    label,
    moods,
    actions,
    phrases,
    strong,
    weak,
    refs=None,
    min_weak=2,
):
    return {
        "label": label,
        "moods": moods,
        "actions": actions,
        "phrases": phrases,
        "strong": strong,
        "weak": weak,
        "refs": set(refs or []),
        "min_weak": min_weak,
    }


PROFILES = [
    profile(
        "sabr va taskin",
        ["siqilish", "qaygu", "tushkunlik", "musibat"],
        ["sabr qilish", "Allohni zikr qilish", "duo qilish"],
        ["konglim siqil", "ichim siqil", "hayotim ogir", "dilim ogri", "musibat yet", "boshiga musibat"],
        ["musibat", "sabr toqat", "sabr-toqat", "qaygu", "gam", "dard", "orom", "xotirjam", "qiyinchilik", "osonlik"],
        ["sabr", "rahmat", "ajr", "qalb", "zikr", "xafa"],
        {"2:45", "2:153", "2:155", "2:156", "2:157", "13:28", "39:53", "94:5", "94:6"},
    ),
    profile(
        "yomon tush va qorquv",
        ["qorquv", "vahima", "yomon tush", "uyqudagi bezovtalik"],
        ["Allohdan panoh sorash", "tushni hammaga aytmaslik", "namoz va duo qilish"],
        ["yomon tush", "qorqinchli tush", "tushimda yomon", "tushida yomon", "shaytondan panoh", "tushini aytmasin", "chap tomoniga tuf", "chap tomonga tuf", "uch marta tuf"],
        ["yomon tush", "qorqinchli tush", "shaytondan panoh", "tushini aytmasin", "uyqusida qorq", "tushida qorq", "vahimali tush", "chap tomoniga tuf", "chap tomonga tuf", "uch marta tuf"],
        ["tush", "uyqu", "shayton", "panoh", "qorq", "vahima", "ruyo"],
        {"113:1", "113:2", "113:3", "113:4", "113:5", "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"},
        min_weak=99,
    ),
    profile(
        "charchoq va yengillik",
        ["charchoq", "malol kelish", "haddan oshish", "holsizlik"],
        ["qurb yetadigan amal", "tanaga haqqini berish", "dam olish"],
        ["juda charchadim", "qurbingiz yet", "tanangning haqqi", "kozning haqqi", "uxlagil", "yotingiz"],
        ["qurbingiz yetadigan", "malol kelmasa", "tanangning haqqi", "kozingning haqqi", "uyquga ham yot", "yotingiz", "haddan oshirib"],
        ["charch", "uyqu", "dam", "qiyin", "roza", "ibodat"],
    ),
    profile(
        "tavba va magfirat",
        ["afsus", "uyalish", "pushaymonlik"],
        ["tavba qilish", "magfirat sorash"],
        ["gunoh qilib", "alloh kechir", "tavba qil", "pushaymon bol"],
        ["tavba", "gunoh", "magfirat", "kechir", "pushaymon", "istigfor"],
        ["rahmat", "zulm", "afsus"],
        {"3:135", "25:70", "39:53", "66:8"},
    ),
    profile(
        "umid va xavotir",
        ["xavotir", "qorquv", "yolgizlik", "noumidlik"],
        ["Allohga tavakkal qilish", "umidni saqlash"],
        ["umidim qolmadi", "kelajagimdan qorq", "hech kimim yoq", "xavotir bos"],
        ["noumid", "xavotir", "xavf yoq", "mahzun", "qorq", "yolgiz", "rahmatidan noumid"],
        ["umid", "rahmat", "qalb", "xotirjam"],
        {"2:112", "2:277", "3:139", "13:28", "39:53"},
    ),
    profile(
        "rizq va ish",
        ["moliyaviy tashvish", "ishsizlik", "qarz"],
        ["halol kasb", "tavakkal", "sadaqa"],
        ["pulim yoq", "qarzim bor", "ishim yurishmay", "rizqim kamay"],
        ["rizq", "halol", "kasb", "qarz", "sadaqa", "taqvo", "tavakkal", "kambagal", "boylik"],
        ["mol", "ish", "savdo", "faqir"],
        {"11:6", "29:60", "65:2", "65:3"},
    ),
    profile(
        "ota-ona va oila",
        ["oilaviy kelishmovchilik", "ota-ona bilan muammo"],
        ["ehson qilish", "qarindoshlikni saqlash", "muloyim gapirish"],
        ["ota onam", "onam bilan", "otam bilan", "qarindosh urug"],
        ["ota", "ona", "farzand", "qarindosh", "ehson", "silai rahm", "oila"],
        ["yaxshilik", "rahm"],
        {"17:23", "17:24", "31:14", "46:15"},
    ),
    profile(
        "namoz va ibodat",
        ["ibodatda sustlik", "dangasalikka berilish"],
        ["namozni qoim qilish", "zikr qilish"],
        ["namoz oqiy olmay", "bomdodga turolmay", "ibodatda sust", "namozga dangasa"],
        ["namoz", "sajda", "ruku", "masjid", "jamoat", "bomdod", "tahorat"],
        ["ibodat", "iymon", "zikr", "fahsh"],
        {"2:45", "2:153", "20:14", "29:45"},
    ),
    profile(
        "gazab va kelishmovchilik",
        ["jahl", "gazab", "alam", "kechirolmaslik"],
        ["afv qilish", "tilni tiyish", "sabr qilish"],
        ["jahlim chiq", "kechira olmay", "asabim buz", "urishib qold"],
        ["gazab", "jahl", "afv", "kechir", "haqorat", "urish", "tortish"],
        ["til", "sabr", "yaxshilik"],
        {"3:134", "7:199", "41:34", "42:40"},
    ),
    profile(
        "kasallik va shifo",
        ["kasallik", "dard", "bemorlik"],
        ["shifo sorash", "sabr qilish", "bemorni ziyorat qilish"],
        ["kasal bol", "sogligim yomon", "dardimga shifo", "bemor yot"],
        ["kasal", "bemor", "shifo", "dard", "ziyorat", "ogri", "davo"],
        ["zarar", "rahmat"],
        {"17:82", "21:83", "21:84", "26:80", "41:44"},
    ),
    profile(
        "olim va qaygu",
        ["judolik", "motam", "qaygu", "olimdan qorqish"],
        ["sabr qilish", "oxiratni eslash", "duo qilish"],
        ["yaqinim vafot", "kimnidir yoqot", "janoza", "qabr", "inna lillah"],
        ["vafot", "janoza", "qabr", "musibat", "judolik", "motam", "qaytuvchimiz"],
        ["olim", "yig", "qaygu", "rahmat"],
        {"2:156", "2:157", "3:185", "21:35", "29:57"},
    ),
    profile(
        "nikoh va munosabat",
        ["turmushdagi muammo", "rashk", "ajrashish xavotiri"],
        ["yaxshilik bilan muomala", "sabr", "adolat"],
        ["xotinim bilan", "erim bilan", "turmushim ogir", "ajrashmoq"],
        ["nikoh", "xotin", "ayol", "er", "juft", "mahr", "taloq", "oila"],
        ["rahmat", "yaxshilik", "adolat"],
        {"4:19", "30:21", "65:2"},
    ),
    profile(
        "harom istak va nafs",
        ["vasvasa", "shahvat", "nafs bilan kurash"],
        ["kozni tiyish", "poklanish", "namozga yopishish"],
        ["nafsimni yenga", "zinodan qorq", "harom narsaga", "shahvat kuch"],
        ["nafs", "shahvat", "zino", "harom", "fahsh", "fitna", "kozni tiy", "pok"],
        ["nikoh", "taqvo"],
        {"17:32", "23:5", "23:6", "24:30", "24:31", "29:45"},
    ),
    profile(
        "zulm va adolatsizlik",
        ["haqsizlik", "mazlumlik", "xiyonat", "tuhmat"],
        ["adolatni tutish", "sabr", "haqni talab qilish"],
        ["menga zulm", "haqimni ye", "meni aldash", "tuhmat qilish"],
        ["zulm", "zolim", "mazlum", "adolat", "tuhmat", "xiyonat", "haqsizlik"],
        ["haq", "sabr", "yordam"],
        {"3:160", "4:135", "14:42", "16:90", "42:40"},
    ),
    profile(
        "hasad va solishtirish",
        ["hasad", "rashk", "ozini solishtirish"],
        ["shukr qilish", "nematni tan olish"],
        ["hasad qil", "boshqalar bilan solishtir", "havas qil"],
        ["hasad", "rashk", "nemat", "fazl", "shukr"],
        ["rizq", "qalb"],
        {"4:32", "14:7", "113:5"},
    ),
    profile(
        "ilm va oqish",
        ["imtihon xavotiri", "oqishdagi qiyinchilik"],
        ["ilm talab qilish", "duo qilish", "sabr"],
        ["imtihondan qorq", "dars qilolmay", "ilm olish", "oqishim qiyin"],
        ["ilm", "bilim", "ustoz", "talaba", "organ", "hikmat", "imtihon"],
        ["oqish", "ziyoda", "foyda"],
        {"20:114", "39:9", "58:11", "96:1"},
    ),
    profile(
        "qaror va maslahat",
        ["ikkilanish", "tanlov oldida qolish"],
        ["maslahat qilish", "istixora", "tavakkal"],
        ["qanday qaror", "qaysi yol", "ikkilanib qold", "maslahat kerak"],
        ["qaror", "tanlov", "maslahat", "istixora", "istikhor", "tavakkal", "hidoyat"],
        ["duo", "ish", "yol"],
        {"3:159", "2:216", "65:3", "1:6"},
    ),
    profile(
        "shukr va qanoat",
        ["norozi bolish", "qanoatsizlik", "baxtsizlik"],
        ["shukr qilish", "qanoat qilish"],
        ["shukr qilolmay", "hech narsadan rozi", "baxtsiz his"],
        ["shukr", "qanoat", "nemat", "ziyoda", "fazl"],
        ["rizq", "boylik"],
        {"14:7", "16:18", "55:13", "93:11"},
    ),
    profile(
        "til va giybat",
        ["giybatdan pushaymonlik", "tilni tiya olmaslik"],
        ["tilni tiyish", "rost gapirish", "tavba"],
        ["giybat qilib", "yolgon gapir", "haqorat qild", "tilimni tiya"],
        ["giybat", "yolgon", "haqorat", "tuhmat", "gumon", "rost gapir"],
        ["til", "haq", "musulmon"],
        {"49:11", "49:12", "33:70", "33:71"},
    ),
    profile(
        "niyat va ixlos",
        ["maqsadni tekshirish", "ixlos"],
        ["niyatni togirlash", "amalni Alloh uchun qilish"],
        ["niyatim qanday", "ixlosim yoq", "amalim qabul"],
        ["niyat", "ixlos", "amallar niyatga", "savob umid"],
        ["amal", "hijrat", "sadaqa"],
    ),
]


def normalize(value: str) -> str:
    value = value.lower()
    value = value.replace("ʼ", "'").replace("’", "'").replace("‘", "'").replace("`", "'")
    value = value.replace("g'", "g").replace("o'", "o").replace("ʻ", "")
    value = value.translate(CYRILLIC_TO_LATIN)
    value = re.sub(r"[^a-z0-9' ]", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def tokens(value: str) -> list[str]:
    return [
        token
        for token in normalize(value).split()
        if len(token) >= 4 and token not in STOP_WORDS
    ]


def fallback_keywords(text: str, limit: int = 8) -> list[str]:
    counts = Counter(tokens(text))
    return [word for word, _ in counts.most_common(limit)]


def contains_phrase(normalized: str, phrase: str) -> bool:
    term = normalize(phrase)
    return bool(term) and term in normalized


def token_hit(normalized_tokens: set[str], term: str) -> bool:
    normalized = normalize(term)
    if not normalized:
        return False
    if " " in normalized:
        return False
    return normalized in normalized_tokens or any(
        token.startswith(normalized) or normalized.startswith(token)
        for token in normalized_tokens
        if len(token) >= 4
    )


def term_hit(normalized: str, normalized_tokens: set[str], term: str) -> bool:
    prepared = normalize(term)
    if not prepared:
        return False
    if " " in prepared:
        return prepared in normalized
    return token_hit(normalized_tokens, prepared)


def concept_map_hits(normalized_tokens: set[str], label: str) -> int:
    """Count how many concept-map words in the text point to the given profile label."""
    count = 0
    for token in normalized_tokens:
        if token in CONCEPT_MAP and label in CONCEPT_MAP[token]:
            count += 1
        # Also check prefix matches for stemmed forms
        for concept_word, labels in CONCEPT_MAP.items():
            if label in labels and len(concept_word) >= 4 and len(token) >= 4:
                if token.startswith(concept_word) or concept_word.startswith(token):
                    count += 1
                    break
    return count


def score_profile(profile_data: dict, normalized: str, normalized_tokens: set[str], ref: str | None):
    phrase_hits = [item for item in profile_data["phrases"] if contains_phrase(normalized, item)]
    strong_hits = [item for item in profile_data["strong"] if term_hit(normalized, normalized_tokens, item)]
    weak_hits = [item for item in profile_data["weak"] if token_hit(normalized_tokens, item)]
    ref_match = ref in profile_data["refs"] if ref else False

    # Concept map: boost score based on synonym/concept word hits
    concept_hits = concept_map_hits(normalized_tokens, profile_data["label"])

    score = 0
    if ref_match:
        score += 100
    score += len(phrase_hits) * 18
    score += len(strong_hits) * 10
    score += len(weak_hits) * 3
    score += concept_hits * 6  # concept synonyms contribute meaningfully

    matched = (
        ref_match
        or phrase_hits
        or strong_hits
        or len(weak_hits) >= profile_data["min_weak"]
        or concept_hits >= 2  # 2+ concept hits = matched
    )
    if not matched:
        return 0, []
    return score, unique(phrase_hits + strong_hits + weak_hits)


# --- AI / LLM Integration (Qwen 3 4B) ---

API_KEY = os.getenv("OPENAI_API_KEY", "sk-your-key-here")
API_BASE = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")  # Can be local Ollama
MODEL = os.getenv("OPENAI_MODEL", "qwen3-4b") # Updated to user requested Qwen 3 4B

def get_ai_semantic_metadata(text: str) -> dict:
    """Use Qwen to analyze the text and return structured semantic metadata."""
    if API_KEY == "sk-your-key-here" or not API_KEY:
        return {}

    prompt = f"""
Siz islomiy matnlar (Qur'on va hadis) bo'yicha mutaxaxissiz. Quyidagi matnni ma'nosini chuqur tahlil qiling va undagi asosiy mavzularni, insonning ruhiy va jismoniy holatlarini (moods), amaliy tavsiyalarni (actions) va kalit so'zlarni ajratib bering.

Matn:
{text[:1500]}

Javobni FAQAT quyidagi JSON formatida bering (boshqa matn qo'shmang):
{{
  "topics": ["mavzu1", "mavzu2"],
  "moods": ["xursandchilik", "tushkunlik", "xavotir" va h.k.],
  "actions": ["nima qilish kerakligi haqida tavsiya"],
  "highlights": ["muhim kalit so'zlar", "ismlar", "joylar"],
  "summary": "matnning qisqacha mazmuni va undagi asosiy dars (o'zbek tilida)"
}}

Mavzular ro'yxati (shu ro'yxatdan albatta kamida bittasini tanlang, agar mos kelsa): 
sabr va taskin, yomon tush va qorquv, charchoq va yengillik, tavba va mag'firat, umid va xavotir, rizq va ish, ota-ona va oila, namoz va ibodat, g'azab va kelishmovchilik, kasallik va shifo, o'lim va qayg'u, nikoh va munosabat, harom istak va nafs, zulm va adolatsizlik, hasad va solishtirish, ilm va o'qish, qaror va maslahat, shukr va qanoat, til va g'iybat.

Eslatma: 'moods' qismida foydalanuvchi qanday holatda bo'lsa (masalan: 'ichim siqilyapti', 'yomon tush ko'rdim', 'ishim yurishmayapti') shu matn unga foydali bo'lishini ko'rsating.
"""
    try:
        response = requests.post(
            f"{API_BASE}/chat/completions",
            headers={"Authorization": f"Bearer {API_KEY}"},
            json={
                "model": MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.1,
                "response_format": {"type": "json_object"}
            },
            timeout=30
        )
        res_json = response.json()
        content = res_json['choices'][0]['message']['content']
        return json.loads(content)
    except Exception as e:
        print(f"  AI metadata extraction error: {e}")
        return {}


def semantic_hits(text: str, ref: str | None = None):
    # 1. Get AI metadata if possible
    ai_meta = get_ai_semantic_metadata(text)
    
    # 2. Traditional rule-based scoring as baseline/augmentation
    normalized = normalize(text)
    normalized_tokens = set(normalized.split())
    scored = []
    for item in PROFILES:
        score, hits = score_profile(item, normalized, normalized_tokens, ref)
        if score > 0:
            scored.append((score, item, hits))
    scored.sort(key=lambda item: item[0], reverse=True)

    selected = scored[:4]
    topics = ai_meta.get("topics", [])
    moods = ai_meta.get("moods", [])
    actions = ai_meta.get("actions", [])
    highlights = ai_meta.get("highlights", [])
    
    # Merge rules with AI
    topics.extend([item["label"] for _, item, _ in selected])
    
    semantic_parts = []
    for _, item, hits in selected:
        moods.extend(item["moods"])
        actions.extend(item["actions"])
        highlights.extend(hits)
        semantic_parts.append(f"{item['label']} {' '.join(item['moods'])} {' '.join(item['actions'])} {' '.join(hits)}")

    if "summary" in ai_meta:
        semantic_parts.append(ai_meta["summary"])

    fallback = fallback_keywords(text)
    highlights.extend(fallback)
    semantic_parts.extend(fallback)

    return {
        "topics": unique(topics)[:6],
        "moods": unique(moods)[:10],
        "actions": unique(actions)[:10],
        "highlights": unique(highlights)[:15],
        "semanticText": normalize(" ".join(semantic_parts + [text[:1400]])),
    }


def unique(values: list[str]) -> list[str]:
    seen = set()
    result = []
    for value in values:
        value = value.strip()
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def build_quran_entries() -> list[dict]:
    raw = json.loads(QURAN_FILE.read_text(encoding="utf-8"))
    result = []
    for verse in raw.get("result", []):
        surah = int(verse["sura"])
        ayah = int(verse["aya"])
        ref = f"{surah}:{ayah}"
        text = " ".join([
            verse.get("translation", ""),
            verse.get("footnotes", ""),
        ])
        hit = semantic_hits(text, ref)
        result.append({
            "id": f"quran_{surah}_{ayah}",
            "type": "QURAN",
            **hit,
        })
    return result


def build_hadith_entries() -> list[dict]:
    if not HADITH_FILE.exists():
        return []
    raw = json.loads(HADITH_FILE.read_text(encoding="utf-8"))
    result = []
    for entry in raw:
        # Include chapter title for richer context matching.
        # The chapter title helps identify the topic of the hadith,
        # but the main text content drives the semantic scoring now.
        text = " ".join([
            entry.get("chapter", ""),
            entry.get("narrator", ""),
            entry.get("text", ""),
        ])
        hit = semantic_hits(text)
        result.append({
            "id": f"hadith_{entry['id']}",
            "type": "HADITH",
            **hit,
        })
    return result


def main():
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    entries = build_quran_entries() + build_hadith_entries()
    OUT_FILE.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")

    hadith_count = sum(1 for entry in entries if entry["type"] == "HADITH")
    tagged_hadith_count = sum(
        1 for entry in entries
        if entry["type"] == "HADITH" and entry.get("topics")
    )
    print(f"Wrote {len(entries)} source highlights to {OUT_FILE}")
    print(f"Hadith entries: {hadith_count}; semantically tagged: {tagged_hadith_count}")


if __name__ == "__main__":
    main()
