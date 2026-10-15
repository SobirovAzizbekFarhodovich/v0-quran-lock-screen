"""
Pure helper functions for the RAG service.

Nothing here imports chromadb, sentence-transformers or touches the network,
so it can be unit-tested in isolation (see ai/rag/tests).
"""

from __future__ import annotations

import re
import unicodedata
from typing import Any

# ---------------------------------------------------------------------------
# Languages
# ---------------------------------------------------------------------------

SUPPORTED_LANGUAGES = ("uz_latin", "uz_cyrillic", "en", "ru")

# "uz" always means Uzbek LATIN — the same as QURAN_LANGUAGE_FILES["uz"].
LANGUAGE_ALIASES = {
    "uz": "uz_latin",
    "uz_lat": "uz_latin",
    "uz_latn": "uz_latin",
    "uz_cyrl": "uz_cyrillic",
}


def resolve_language(lang: str | None, *, strict: bool = False) -> str:
    """Normalize a client language code.

    With ``strict=False`` unknown codes fall back to English (the prompt
    directive default) instead of silently searching zero collections.
    With ``strict=True`` an unknown code is returned unchanged so the caller
    can reject it.
    """
    key = (lang or "").strip().lower().replace("-", "_")
    resolved = LANGUAGE_ALIASES.get(key, key)
    if resolved in SUPPORTED_LANGUAGES or strict:
        return resolved
    return "en"


# ---------------------------------------------------------------------------
# ChromaDB collection names
#
# Must match what scripts/build_chromadb.py creates:
#   type == "quran"  -> quran_verses_{lang} and quran_chunks_{lang}
#   any other type   -> f"{entry['type']}_{lang}"
# where type/lang are taken from the first JSONL entry.  Note that
# data/duas/*.jsonl has type "dua" (singular) -> "dua_uz_latin", while
# data/duas_quranic/*.jsonl has type "duas_quranic" -> "duas_quranic_en".
# ---------------------------------------------------------------------------

# Collection base name (the JSONL "type") -> languages it is built for.
GENERIC_COLLECTION_LANGS: dict[str, tuple[str, ...]] = {
    "bukhari": ("en", "ru", "uz_cyrillic", "uz_latin"),  # also "ar" (not searched)
    "muslim": ("en", "ru", "uz_cyrillic", "uz_latin"),  # also "ar" (not searched)
    "hisnul_muslim": ("en", "ru", "uz_cyrillic", "uz_latin"),
    "hadith": ("en", "ru", "uz_cyrillic", "uz_latin"),
    "duas_quranic": ("en", "ru", "uz_cyrillic", "uz_latin"),
    "ibodat": ("en", "ru", "uz_cyrillic", "uz_latin"),
    "sacred_months": ("en", "ru", "uz_cyrillic", "uz_latin"),
    "dua": ("uz_cyrillic", "uz_latin"),
}
QURAN_LANGS = ("en", "ru", "uz_cyrillic", "uz_latin")


def search_collections_for(lang: str) -> list[str]:
    names = [f"quran_chunks_{lang}"] if lang in QURAN_LANGS else []
    for base, langs in GENERIC_COLLECTION_LANGS.items():
        if lang in langs:
            names.append(f"{base}_{lang}")
    return names


LANG_TO_COLLECTIONS: dict[str, list[str]] = {
    lang: search_collections_for(lang) for lang in SUPPORTED_LANGUAGES
}
LANG_TO_COLLECTIONS["uz"] = LANG_TO_COLLECTIONS["uz_latin"]

LANG_TO_VERSE_COLLECTIONS: dict[str, list[str]] = {
    lang: [f"quran_verses_{lang}"] for lang in QURAN_LANGS
}
LANG_TO_VERSE_COLLECTIONS["uz"] = LANG_TO_VERSE_COLLECTIONS["uz_latin"]


def verse_doc_id(collection_name: str, surah: int, ayah: int) -> str:
    """Document id used by build_chromadb.create_verse_documents.

    Ids are ``{entry_lang}:{surah}:{ayah}``; the entry lang is the suffix of
    the ``quran_verses_{lang}`` collection, not the (possibly aliased)
    language code the client sent.
    """
    lang = collection_name.removeprefix("quran_verses_")
    return f"{lang}:{surah}:{ayah}"


# ---------------------------------------------------------------------------
# Text normalization / keyword matching
# ---------------------------------------------------------------------------

_APOSTROPHES = str.maketrans({c: "'" for c in "ʻʼ‘’`´ʹ"})
_CONTROL_CATEGORIES = {"Cc", "Cf", "Cs", "Co", "Cn", "Zl", "Zp"}


def normalize_text(text: str) -> str:
    return " ".join(text.lower().translate(_APOSTROPHES).split())


def tokenize(text: str) -> list[str]:
    """Lowercased word tokens; apostrophes inside words are kept (qur'on)."""
    return [
        token.strip("'")
        for token in re.findall(r"[\w']+", text.lower().translate(_APOSTROPHES))
        if token.strip("'")
    ]


# Uzbek case/plural/possessive endings accepted after SHORT stems (<= 3
# letters) such as "haj", "duo", "gam".  Deliberately excludes endings like
# "m"/"mi" so that "hajmi" (volume) or "gamburger" never match.
_SHORT_STEM_SUFFIX = (
    r"(?:j|lar|lari|ni|ning|ga|ka|da|dan|im|ing|imiz|ingiz|li|gin"
    r"|лар|лари|ни|нинг|га|ка|да|дан|им|инг|имиз|ингиз|ли|гин)?"
)


def _term_pattern(term: str) -> str:
    escaped = re.escape(term)
    if len(term) <= 3:
        return rf"(?<![\w']){escaped}{_SHORT_STEM_SUFFIX}(?![\w'])"
    # Longer stems match at a word START (Uzbek is agglutinative:
    # namozni, hadislar, ro'zada...), never in the middle of a word.
    return rf"(?<![\w']){escaped}"


def compile_terms(terms: tuple[str, ...]) -> re.Pattern[str]:
    return re.compile("|".join(_term_pattern(t) for t in terms))


ISLAMIC_TERMS = (
    "qur'on", "quron", "oyat", "hadis", "hadith", "sunnat", "sunnah",
    "namoz", "salah", "ro'za", "roza", "zakot", "zakat", "haj", "umra",
    "duo", "zikr", "tavba", "halol", "harom", "fiqh", "fatvo", "mazhab",
    "islom", "alloh", "rasululloh", "payg'ambar", "paygambar",
    # values / character / belief topics the assistant also answers from sources
    "sabr", "shukr", "tavakkul", "iymon", "imon", "taqvo", "gunoh", "savob",
    "jannat", "do'zax", "dozax", "jahannam", "qiyomat", "nafs", "tavba",
    "ota-ona", "ota ona", "ota-onaga", "ota-onani", " risq", "rizq", "baraka",
    "sadaqa", "sadaqah", "qadar", "taqdir", "shukur", "niyat", "ibodat",
    "farz", "sunnat", "makruh", "mustahab", "gunohi", "halollik", "harомlik",
    # Cyrillic Uzbek / Russian
    "қуръон", "оят", "ҳадис", "намоз", "рўза", "закот", "ҳаж", "дуо",
    "аллоҳ", "ислом", "пайғамбар", "коран", "хадис", "намаз", "молитв",
    "аллах", "ислам", "пророк",
    # English
    "quran", "prayer", "fasting", "allah", "islam", "prophet",
)
EMOTIONAL_TERMS = (
    "charchadim", "tushkun", "qo'rqyap", "qorqyap", "xavotir", "g'am",
    "gam", "yolg'iz", "yolgiz", "siqilyap", "siqildim", "zerikdim",
    "ishlarim yurishmay", "kasal", "yo'qotdim", "yoqotdim", "stress",
    # Cyrillic Uzbek / Russian
    "чарчадим", "хавотир", "ғам", "ёлғиз", "сиқиляп", "касал", "стресс",
    "устал", "тревог", "страш", "грустн", "одинок",
    # English
    "tired", "anxious", "anxiety", "lonely", "depressed", "sad",
)
_ISLAMIC_RE = compile_terms(ISLAMIC_TERMS)
_EMOTIONAL_RE = compile_terms(EMOTIONAL_TERMS)


def has_islamic_terms(message: str) -> bool:
    """True if the message mentions an Islamic/faith term (safety net so a
    clearly-Islamic question is never dismissed as casual/worldly)."""
    return bool(_ISLAMIC_RE.search(normalize_text(message)))


def heuristic_intent(message: str) -> dict[str, Any]:
    """Keyword fallback used when the LLM classifier is unavailable."""
    text = normalize_text(message)
    if _ISLAMIC_RE.search(text):
        return {"intent": "islamic", "search_query": message}
    if _EMOTIONAL_RE.search(text):
        return {"intent": "emotional", "search_query": message}
    return {"intent": "casual", "search_query": ""}


# ---------------------------------------------------------------------------
# Instant canned replies (only for messages that are JUST a greeting/thanks)
# ---------------------------------------------------------------------------

QUICK_CASUAL_MAX_CHARS = 60

GREETING_ANCHORS = frozenset({
    "salom", "assalom", "assalomu", "assalamu", "assalamualaykum", "salam",
    "hi", "hello", "hey", "xayrli", "hayrli", "privet", "selam",
    "салом", "ассалом", "ассалому", "хайрли", "привет", "здравствуйте",
})
THANKS_ANCHORS = frozenset({
    "rahmat", "raxmat", "tashakkur", "jazakallah", "jazakalloh",
    "jazakallohu", "barakalloh", "barakallah", "spasibo", "minnatdorman",
    "minnatdor", "thanks", "thank", "thx",
    "раҳмат", "рахмат", "ташаккур", "жазакаллоҳ", "жазакаллоҳу", "бараколлоҳ",
    "спасибо", "благодарю",
})
# Words allowed around an anchor without turning the message into a question.
FILLER_WORDS = frozenset({
    "alaykum", "alaikum", "aleykum", "alaykum", "va", "rahmatulloh",
    "rahmatullohi", "barakatuh", "barakotuh", "tong", "kun", "kech", "kechingiz",
    "kuningiz", "tongingiz", "katta", "juda", "ko'p", "kop", "sizga", "sizdan",
    "xayran", "xayr", "do'stim", "aka", "opa", "ustoz", "bot",
    "there", "you", "so", "much", "very", "a", "lot", "dear", "brother",
    "sister", "good", "morning", "evening", "afternoon", "ok", "okay",
    "алайкум", "ва", "раҳматуллоҳи", "баракатуҳ", "тонг", "кун", "кеч",
    "катта", "жуда", "кўп", "сизга", "хайран", "большое", "огромное", "вам",
    "тебе", "доброе", "утро", "добрый", "день", "вечер",
    # greeting small-talk so "salom qalaysiz" / "assalomu alaykum qalaysiz" is
    # still just a greeting, not a question that fetches a verse.
    "qalaysiz", "qalaysan", "qandaysiz", "qandaysan", "qalay", "yaxshimisiz",
    "yaxshimisan", "yaxshimi", "yaxshi", "ahvolingiz", "ahvol", "tinchmisiz",
    "ishlaringiz", "how", "are", "doing", "u", "r",
    "қалайсиз", "қалайсан", "қандайсиз", "яхшимисиз", "яхшимисан", "аҳволингиз",
    "как", "дела", "поживаете", "ты",
})

QUICK_CASUAL_REPLIES: dict[str, dict[str, str]] = {
    "greeting": {
        "uz_latin": "Va alaykum assalom va rahmatullohi va barokatuh! 😊",
        "uz_cyrillic": "Ва алайкум ассалом ва раҳматуллоҳи ва барокатуҳ! 😊",
        "en": "Wa alaykum assalam wa rahmatullahi wa barakatuh! 😊",
        "ru": "Ва алейкум ассалам ва рахматуллахи ва баракатух! 😊",
    },
    "thanks": {
        "uz_latin": (
            "Arzimaydi! Jazakallohu xayran. Yana savollaringiz bo'lsa, bemalol yozavering."
        ),
        "uz_cyrillic": (
            "Арзимайди! Жазакаллоҳу хайран. Яна саволларингиз бўлса, бемалол ёзаверинг."
        ),
        "en": "You're welcome! Feel free to ask anytime.",
        "ru": "Пожалуйста! Обращайтесь, если будут ещё вопросы.",
    },
}


def quick_casual_category(message: str) -> str | None:
    """'greeting' / 'thanks' when the message is ONLY that, else None.

    "rahmat" -> thanks; "rahmat, namoz qanday o'qiladi?" -> None (a real
    question that must go through the full flow).
    """
    text = normalize_text(message)
    if not text or len(text) > QUICK_CASUAL_MAX_CHARS or "?" in text:
        return None
    tokens = tokenize(text)
    if not tokens:
        return None
    has_greeting = has_thanks = False
    for token in tokens:
        if token in THANKS_ANCHORS:
            has_thanks = True
        elif token in GREETING_ANCHORS:
            has_greeting = True
        elif token not in FILLER_WORDS:
            return None
    if has_thanks:
        return "thanks"
    if has_greeting:
        return "greeting"
    return None


def quick_casual_reply(message: str, language: str) -> str | None:
    category = quick_casual_category(message)
    if category is None:
        return None
    replies = QUICK_CASUAL_REPLIES[category]
    return replies.get(language, replies["en"])


# ---------------------------------------------------------------------------
# Prompt input hygiene
# ---------------------------------------------------------------------------

USER_NAME_MAX_CHARS = 64
MESSAGE_MAX_CHARS = 2000


def sanitize_user_name(name: str | None) -> str | None:
    """Strip control/format characters and newlines before prompt use."""
    if name is None:
        return None
    cleaned = "".join(
        " " if unicodedata.category(ch) in _CONTROL_CATEGORIES or ch.isspace() else ch
        for ch in name
    )
    cleaned = " ".join(cleaned.split())[:USER_NAME_MAX_CHARS].strip()
    return cleaned or None


# ---------------------------------------------------------------------------
# Quran references
# ---------------------------------------------------------------------------

_VERSE_MARKER_RE = re.compile(r"\[(\d{1,3}):(\d{1,3})\]")
_CHUNK_ID_RE = re.compile(r":(\d{1,3}):(\d{1,3})-(\d{1,3})$")


def safe_int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def ayah_range(meta: dict[str, Any], text: str = "", doc_id: str = "") -> tuple[int, int]:
    """(first, last) ayah for a Quran passage; (0, 0) when unknown.

    Verse docs and duas_quranic store ``ayah``; contextual chunks
    (``quran_chunk``) store ``ayah_start``/``ayah_end`` only and carry
    ``[surah:ayah]`` markers in their text and ``...:{s}:{a}-{b}`` ids.
    """
    ayah = safe_int(meta.get("ayah"))
    if ayah > 0:
        return ayah, ayah
    start, end = safe_int(meta.get("ayah_start")), safe_int(meta.get("ayah_end"))
    if start > 0:
        return start, max(start, end)
    if doc_id:
        match = _CHUNK_ID_RE.search(doc_id)
        if match:
            return int(match.group(2)), int(match.group(3))
    surah = safe_int(meta.get("surah"))
    ayahs = [
        int(a) for s, a in _VERSE_MARKER_RE.findall(text or "")
        if not surah or int(s) == surah
    ]
    if ayahs:
        return min(ayahs), max(ayahs)
    return 0, 0


def enrich_metadata(meta: dict[str, Any], text: str = "", doc_id: str = "") -> dict[str, Any]:
    """Copy of the metadata with ``ayah`` filled in for Quran chunks."""
    enriched = dict(meta or {})
    if safe_int(enriched.get("surah")) > 0 and safe_int(enriched.get("ayah")) <= 0:
        start, end = ayah_range(enriched, text, doc_id)
        if start:
            enriched["ayah"] = start
            enriched.setdefault("ayah_start", start)
            enriched.setdefault("ayah_end", end)
    return enriched


def format_reference(meta: dict[str, Any], text: str = "") -> str:
    item_type = meta.get("type", "")
    source_title = meta.get("source") or meta.get("title") or ""
    surah = safe_int(meta.get("surah"))
    surah_name = meta.get("surah_name") or ""
    hadith_number = safe_int(meta.get("hadith_number"))

    if item_type in ("quran", "quran_chunk") or surah > 0:
        start, end = ayah_range(meta, text)
        surah_label = surah_name or surah
        if start and end > start:
            return f"Quran, surah {surah_label}, ayahs {start}-{end}"
        if start:
            return f"Quran, surah {surah_label}, ayah {start}"
        return f"Quran, surah {surah_label}"
    if hadith_number > 0:
        return f"{source_title or 'Hadith'}, No. {hadith_number}"
    if source_title:
        return source_title
    return "Source"


# ---------------------------------------------------------------------------
# Retrieval merging
# ---------------------------------------------------------------------------

def normalize_and_rank(
    per_collection: list[list[dict[str, Any]]], limit: int
) -> list[dict[str, Any]]:
    """Merge per-collection keyword hits into one globally ranked list.

    Raw BM25 scores are not comparable across collections (IDF and document
    lengths differ), so each collection's scores are scaled to [0, 1] by its
    best hit before the global sort.
    """
    merged: list[dict[str, Any]] = []
    for results in per_collection:
        if not results:
            continue
        top = max(item["score"] for item in results)
        if top <= 0:
            continue
        for item in results:
            if item["score"] <= 0:
                continue
            merged.append({**item, "raw_score": item["score"], "score": item["score"] / top})
    merged.sort(key=lambda item: (item["score"], item["raw_score"]), reverse=True)
    return merged[:limit]


# ---------------------------------------------------------------------------
# Gemini
# ---------------------------------------------------------------------------

def gemini_generation_config(
    model: str,
    *,
    max_tokens: int,
    temperature: float,
    json_mode: bool = False,
    thinking_budget: int = 0,
) -> dict[str, Any]:
    """generationConfig with an explicit thinking budget.

    Gemini 2.5 models count "thinking" tokens against maxOutputTokens, so a
    200-token classifier call could spend everything on thinking and return
    no text.  Flash / Flash-Lite accept thinkingBudget=0 (thinking off); Pro
    cannot disable thinking, so its (minimum) budget is added on top of the
    visible-answer limit.  Older models (1.5 / 2.0) reject thinkingConfig.
    """
    config: dict[str, Any] = {"temperature": temperature, "maxOutputTokens": max_tokens}
    if json_mode:
        config["responseMimeType"] = "application/json"
    name = model.lower()
    budget = max(0, int(thinking_budget))
    if "2.5" in name:
        if "pro" in name:
            budget = max(budget, 128)
        config["thinkingConfig"] = {"thinkingBudget": budget}
        config["maxOutputTokens"] = max_tokens + budget
    elif re.search(r"gemini-[3-9]", name):
        # Newer models think by default and cannot always turn it off: leave
        # headroom so thinking does not eat the visible answer.
        config["maxOutputTokens"] = max_tokens + max(budget, 2048)
    return config


def gemini_response_text(payload: dict[str, Any]) -> tuple[str | None, str]:
    """(text, reason) from a generateContent response; text None if empty."""
    candidates = payload.get("candidates") or []
    if not candidates:
        block = (payload.get("promptFeedback") or {}).get("blockReason")
        return None, f"no candidates (blockReason={block})"
    candidate = candidates[0]
    parts = (candidate.get("content") or {}).get("parts") or []
    text = "".join(
        str(part.get("text") or "") for part in parts if not part.get("thought")
    ).strip()
    if text:
        return text, "ok"
    return None, f"empty candidate (finishReason={candidate.get('finishReason')})"


# The Tanzil/alquran.cloud Uthmani text prefixes ayah 1 of every surah except
# al-Fatiha (where it *is* ayah 1) and at-Tawba with the Basmala. Readers show it
# as a header, so it is stripped from the ayah itself.
_ARABIC_MARKS_RE = re.compile(r"[ؐ-ًؚ-ٰٟۖ-ۭ]")
_BASMALA_LETTERS = "بسم ٱلله ٱلرحمن ٱلرحيم"


def strip_basmala(surah: int, ayah: int, text: str) -> str:
    text = text.replace("﻿", "")
    if ayah != 1 or surah in (1, 9):
        return text
    words = text.split(" ")
    if len(words) > 4 and _ARABIC_MARKS_RE.sub("", " ".join(words[:4])) == _BASMALA_LETTERS:
        return " ".join(words[4:])
    return text
