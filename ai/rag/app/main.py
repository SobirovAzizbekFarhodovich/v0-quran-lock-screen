"""
Islamic AI RAG Service — FastAPI server (v3, intent-aware).

Oqim:
1. Foydalanuvchi xabari avval KLASSIFIKATSIYA qilinadi:
   - Oddiy suhbat / umumiy savol  -> bilimlar bazasi QIDIRILMAYDI, tabiiy javob.
   - Islomiy savol (oyat, hadis, duo, ibodat, hukm) -> ChromaDB + BM25 hybrid
     retrieval, javob FAQAT topilgan manbalardan generatsiya qilinadi.
2. Suhbat xotirasi Redis'da user_id bo'yicha saqlanadi (oxirgi 20 xabar).
3. Manba topilmasa — halol "topilmadi" javobi, hech narsa to'qilmaydi.

Tillar: uz_latin, uz_cyrillic, en, ru
API kontrakti eski versiya bilan to'liq mos: POST /query, POST /verse,
GET /quran/verses, GET /health.
"""

import hashlib
import json
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import httpx
import redis.asyncio as redis
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from .retriever import HybridRetriever


class QueryRequest(BaseModel):
    user_id: str | None = None
    message: str = Field(min_length=1)
    language: str = "uz_cyrillic"


class QueryResponse(BaseModel):
    answer: str
    sources: list[dict[str, Any]]
    cached: bool = False


class VerseRequest(BaseModel):
    surah: int = Field(ge=1, le=114)
    ayah: int = Field(ge=1)
    language: str = "uz_cyrillic"


class VerseResponse(BaseModel):
    text: str
    metadata: dict[str, Any]


redis_client: redis.Redis | None = None
retriever = HybridRetriever()

CACHE_HITS_KEY = "rag:cache:hits"
CACHE_MISSES_KEY = "rag:cache:misses"
CACHE_TTL_SECONDS = 86400

HISTORY_KEY_PREFIX = "rag:history:"
HISTORY_MAX_MESSAGES = 20  # oxirgi 10 ta savol-javob
HISTORY_TTL_SECONDS = 7 * 86400

QURAN_DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "quran"
QURAN_LANGUAGE_FILES = {
    "uz": "uz_latin.jsonl",
    "uz_latin": "uz_latin.jsonl",
    "uz_cyrillic": "uz_cyrillic.jsonl",
    "en": "en.jsonl",
    "ru": "ru.jsonl",
}
QURAN_ARABIC_URL = os.getenv(
    "QURAN_ARABIC_URL", "https://api.alquran.cloud/v1/quran/quran-uthmani"
)
arabic_verses: dict[tuple[int, int], str] | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global redis_client
    host, port = os.getenv("REDIS_ADDR", "localhost:6379").split(":")
    redis_client = redis.Redis(
        host=host,
        port=int(port),
        password=os.getenv("REDIS_PASSWORD") or None,
        db=int(os.getenv("REDIS_DB", "0")),
        decode_responses=True,
    )
    yield
    if redis_client is not None:
        await redis_client.close()


app = FastAPI(title="Islamic AI RAG Service", version="3.0.0", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Til sozlamalari
# ---------------------------------------------------------------------------

LANGUAGE_ALIASES = {
    "uz": "uz_cyrillic",
    "uz_cyrl": "uz_cyrillic",
    "uz_lat": "uz_latin",
}


def resolve_language(lang: str) -> str:
    return LANGUAGE_ALIASES.get(lang, lang)


LANGUAGE_DIRECTIVES = {
    "uz_latin": (
        "RESPONSE LANGUAGE: Uzbek in LATIN script only, in clean literary Uzbek "
        "without grammar mistakes. The Arabic text of Quran verses and duas stays "
        "in Arabic; every translation and explanation is in Uzbek Latin script."
    ),
    "uz_cyrillic": (
        "RESPONSE LANGUAGE: Uzbek in CYRILLIC script only, in clean literary Uzbek "
        "without grammar mistakes. The Arabic text of Quran verses and duas stays "
        "in Arabic; every translation and explanation is in Uzbek Cyrillic script."
    ),
    "en": (
        "RESPONSE LANGUAGE: English only. The Arabic text of Quran verses and duas "
        "stays in Arabic; translations and explanations are in English."
    ),
    "ru": (
        "RESPONSE LANGUAGE: Russian only. The Arabic text of Quran verses and duas "
        "stays in Arabic; translations and explanations are in Russian."
    ),
}

NOT_FOUND_MESSAGES = {
    "uz_latin": (
        "Kechirasiz, bu savol bo'yicha mavjud islomiy manbalar bazasidan ishonchli "
        "va sahih manba topa olmadim. Bu masalada bilimli ulamolarga murojaat "
        "qilishingizni tavsiya qilaman."
    ),
    "uz_cyrillic": (
        "Кечирасиз, бу савол бўйича мавжуд исломий манбалар базасидан ишончли "
        "ва саҳиҳ манба топа олмадим. Бу масалада билимли уламоларга мурожаат "
        "қилишингизни тавсия қиламан."
    ),
    "en": (
        "I could not find an authentic source for this question in the available "
        "Islamic Knowledge Base. I recommend consulting knowledgeable scholars on "
        "this matter."
    ),
    "ru": (
        "Извините, я не нашёл достоверного источника по этому вопросу в доступной "
        "базе исламских знаний. Рекомендую обратиться к компетентным учёным."
    ),
}

ERROR_MESSAGES = {
    "uz_latin": "Kechirasiz, hozir javob berishda texnik xatolik yuz berdi. Birozdan so'ng qayta urinib ko'ring.",
    "uz_cyrillic": "Кечирасиз, ҳозир жавоб беришда техник хатолик юз берди. Бироздан сўнг қайта уриниб кўринг.",
    "en": "Sorry, a technical error occurred. Please try again in a moment.",
    "ru": "Извините, произошла техническая ошибка. Пожалуйста, попробуйте ещё раз чуть позже.",
}


# ---------------------------------------------------------------------------
# System promptlar
# ---------------------------------------------------------------------------

BASE_RULES = """You are the Islamic AI assistant inside the "Quran Reminder" mobile app.
Personality: warm, respectful, humble, patient, easy to understand. You speak like
a knowledgeable, kind human companion — never robotic, never preachy.

Core principles:
1. Understand the user's real intention first. Natural conversation comes first;
   do not force religious content where it is not asked for or needed.
2. Never fabricate Quran verses, hadiths, duas, references or rulings. Accuracy
   is more important than completeness: if you do not know, say so honestly.
3. Do not issue fatwas. For complex fiqh, legal or personal religious matters,
   kindly direct the user to qualified scholars and muftis.
4. When scholars hold multiple valid opinions, say so and present the major views
   fairly, without sectarian argument.
5. For emotional messages (sadness, anxiety, fear, loneliness), show sincere
   empathy FIRST. Add religious comfort only when it genuinely helps or the user
   asks for it.
6. Keep answers concise unless the user asks for detail. Use simple language.
7. Never promote hatred, violence or extremism, and never insult any religion or
   person."""

CASUAL_RULES = """The user's latest message is ordinary conversation or a general
(non-religious) question — a greeting, small talk, thanks, or a question about
everyday life, technology, science, language or similar.

Answer naturally, warmly and concisely, like a helpful human assistant.
Do NOT quote Quran verses or hadiths. Do NOT insert religious evidence.
A respectful, Muslim-friendly tone is welcome, but the answer itself stays
natural and to the point."""

GROUNDED_RULES = """The user's question requires Islamic evidence. Below you are
given passages retrieved from the app's authenticated Islamic Knowledge Base
(Quran, Sahih al-Bukhari, Sahih Muslim, Hisnul Muslim, Riyadh as-Salihin,
Al-Adhkar, Bulugh al-Maram and other trusted collections).

STRICT SOURCE RULES:
1. Use ONLY the passages below as religious evidence. NEVER quote Quran, hadith
   or dua texts from your own memory, and never "complete" a partial text from
   memory.
2. Not every retrieved passage is relevant — silently ignore passages that do
   not answer the question.
3. Cite every piece of evidence you use:
   - Quran: surah name/number and verse number;
   - Hadith: collection name, book or chapter, and hadith number when available;
   - Dua: the source collection and its Quran/hadith origin when given.
4. When giving a dua, present the Arabic text (if present in the passage), its
   translation and the source together.
5. If the passages do NOT actually contain an answer to the user's question,
   reply with exactly this sentence and nothing else:
   "{not_found}"
6. Never invent references and never present weak or fabricated narrations as
   authentic."""

CLASSIFIER_PROMPT = """You are an intent classifier for an Islamic AI assistant.
Classify the user's LATEST message, using the conversation for context.

Reply ONLY with JSON, nothing else:
{"needs_sources": true or false, "search_query": "..."}

needs_sources = true — answering requires evidence from the Islamic Knowledge
Base: questions about Quran verses, hadith, duas, prayer, fasting, zakat, hajj,
faith/aqidah, Islamic rulings, manners, worship or Islamic history; requests to
quote or explain religious texts; follow-up questions to such topics ("yana
bitta ayting", "давай ещё"); or emotional messages where the user explicitly
asks for religious comfort (a dua, a verse).

needs_sources = false — greetings, small talk, thanks, jokes, general knowledge,
technology, science, translation help, everyday questions, or emotional messages
where the user has NOT asked for religious content.

search_query — when needs_sources is true: a short standalone keyword query in
the user's language, best suited for searching Quran/hadith/dua collections
(resolve pronouns from context, e.g. "yana bitta" -> the actual topic).
When needs_sources is false: an empty string."""


# ---------------------------------------------------------------------------
# LLM chaqiruvi (Gemini -> OpenRouter zanjiri)
# ---------------------------------------------------------------------------

async def call_llm(
    system: str,
    messages: list[dict[str, str]],
    *,
    max_tokens: int = 1500,
    temperature: float = 0.3,
    json_mode: bool = False,
) -> str | None:
    """Gemini API, ishlamasa OpenRouter orqali javob generatsiya qiladi."""
    gemini_key = os.getenv("GEMINI_API_KEY", "")
    if gemini_key:
        try:
            model = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
            url = (
                "https://generativelanguage.googleapis.com/v1beta/models/"
                f"{model}:generateContent?key={gemini_key}"
            )
            contents = [
                {
                    "role": "model" if m["role"] == "assistant" else "user",
                    "parts": [{"text": m["content"]}],
                }
                for m in messages
            ]
            config: dict[str, Any] = {
                "temperature": temperature,
                "maxOutputTokens": max_tokens,
            }
            if json_mode:
                config["responseMimeType"] = "application/json"
            async with httpx.AsyncClient(timeout=45) as client:
                resp = await client.post(
                    url,
                    headers={"Content-Type": "application/json"},
                    json={
                        "systemInstruction": {"parts": [{"text": system}]},
                        "contents": contents,
                        "generationConfig": config,
                    },
                )
            if resp.status_code == 200:
                candidates = resp.json().get("candidates", [])
                if candidates and "content" in candidates[0]:
                    parts = candidates[0]["content"].get("parts", [])
                    if parts and parts[0].get("text"):
                        return parts[0]["text"]
        except Exception:
            pass

    openrouter_key = os.getenv("OPENROUTER_API_KEY", "")
    if openrouter_key:
        try:
            payload: dict[str, Any] = {
                "model": os.getenv("OPENROUTER_MODEL", "anthropic/claude-3.5-sonnet"),
                "messages": [{"role": "system", "content": system}, *messages],
                "temperature": temperature,
                "max_tokens": max_tokens,
            }
            if json_mode:
                payload["response_format"] = {"type": "json_object"}
            async with httpx.AsyncClient(timeout=45) as client:
                resp = await client.post(
                    "https://openrouter.ai/api/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {openrouter_key}",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                )
            if resp.status_code == 200:
                content = resp.json()["choices"][0]["message"]["content"]
                if content and "</think>" in content:
                    content = content.split("</think>")[-1].strip()
                return content
        except Exception:
            pass

    return None


def extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end > start:
        return text[start : end + 1]
    return text


async def classify_intent(message: str, history: list[dict[str, str]]) -> dict[str, Any]:
    """Xabar islomiy dalil talab qiladimi-yo'qligini aniqlaydi."""
    convo = history[-6:] + [{"role": "user", "content": message}]
    raw = await call_llm(
        CLASSIFIER_PROMPT, convo, max_tokens=200, temperature=0.0, json_mode=True
    )
    if raw:
        try:
            data = json.loads(extract_json(raw))
            return {
                "needs_sources": bool(data.get("needs_sources", True)),
                "search_query": str(data.get("search_query") or "").strip(),
            }
        except Exception:
            pass
    # Klassifikatsiya ishlamasa — xavfsiz tomonga: manba qidiramiz
    return {"needs_sources": True, "search_query": ""}


# ---------------------------------------------------------------------------
# Suhbat xotirasi (Redis)
# ---------------------------------------------------------------------------

def history_key(user_id: str) -> str:
    return f"{HISTORY_KEY_PREFIX}{user_id}"


async def load_history(user_id: str | None) -> list[dict[str, str]]:
    if not user_id or redis_client is None:
        return []
    try:
        raw = await redis_client.lrange(history_key(user_id), -HISTORY_MAX_MESSAGES, -1)
        return [json.loads(item) for item in raw]
    except Exception:
        return []


async def save_turn(user_id: str | None, user_msg: str, answer: str) -> None:
    if not user_id or redis_client is None:
        return
    try:
        key = history_key(user_id)
        await redis_client.rpush(
            key,
            json.dumps({"role": "user", "content": user_msg}, ensure_ascii=False),
            json.dumps({"role": "assistant", "content": answer}, ensure_ascii=False),
        )
        await redis_client.ltrim(key, -HISTORY_MAX_MESSAGES, -1)
        await redis_client.expire(key, HISTORY_TTL_SECONDS)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Javob generatsiyasi
# ---------------------------------------------------------------------------

def build_context(sources: list[dict[str, Any]]) -> str:
    parts = []
    for i, item in enumerate(sources[:6], 1):
        meta = item.get("metadata", {})
        item_type = meta.get("type", "")
        source_title = meta.get("source") or meta.get("title") or ""
        surah = safe_int(meta.get("surah"))
        ayah = safe_int(meta.get("ayah"))
        hadith_number = safe_int(meta.get("hadith_number"))

        if item_type == "quran" or surah > 0:
            ref = f"Quran, surah {surah}, ayah {ayah}"
            if source_title:
                ref = f"{ref} ({source_title})"
        elif hadith_number > 0:
            ref = f"{source_title or 'Hadith'}, No. {hadith_number}"
        elif source_title:
            ref = source_title
        else:
            ref = "Source"

        parts.append(f"[{i}] {ref}\n{item['text']}")
    return "\n\n---\n\n".join(parts)


async def generate_casual_answer(
    message: str, language: str, history: list[dict[str, str]]
) -> str:
    system = "\n\n".join([
        BASE_RULES,
        CASUAL_RULES,
        LANGUAGE_DIRECTIVES.get(language, LANGUAGE_DIRECTIVES["en"]),
    ])
    convo = history[-8:] + [{"role": "user", "content": message}]
    answer = await call_llm(system, convo, max_tokens=800, temperature=0.6)
    if answer:
        return answer.strip()
    return ERROR_MESSAGES.get(language, ERROR_MESSAGES["en"])


async def generate_grounded_answer(
    message: str,
    language: str,
    sources: list[dict[str, Any]],
    history: list[dict[str, str]],
) -> tuple[str, list[dict[str, Any]]]:
    not_found = NOT_FOUND_MESSAGES.get(language, NOT_FOUND_MESSAGES["en"])
    if not sources:
        return not_found, []

    context = build_context(sources)
    system = "\n\n".join([
        BASE_RULES,
        GROUNDED_RULES.format(not_found=not_found),
        LANGUAGE_DIRECTIVES.get(language, LANGUAGE_DIRECTIVES["en"]),
    ])
    user_prompt = (
        f"KNOWLEDGE BASE PASSAGES:\n\n{context}\n\nUSER QUESTION: {message}"
    )
    convo = history[-8:] + [{"role": "user", "content": user_prompt}]
    answer = await call_llm(system, convo, max_tokens=1500, temperature=0.2)
    if answer:
        return answer.strip(), sources[:6]

    # LLM mavjud bo'lmasa — topilgan manbalarning o'zini qaytaramiz
    lines = []
    for idx, s in enumerate(sources[:4], 1):
        meta = s.get("metadata", {})
        title = meta.get("source") or meta.get("title") or "Manba"
        lines.append(f"{idx}. **{title}**:\n{s['text']}")
    return "\n\n".join(lines), sources[:4]


# ---------------------------------------------------------------------------
# Endpointlar
# ---------------------------------------------------------------------------

@app.get("/health")
@app.get("/healthz")
async def healthz() -> dict[str, Any]:
    stats: dict[str, Any]
    status = "ok"
    try:
        stats = retriever.collection_stats()
        if stats["total_documents"] == 0:
            status = "degraded"
    except Exception as exc:
        status = "degraded"
        stats = {
            "total_documents": 0,
            "available_collections": 0,
            "missing_collections": [],
            "collections": {},
            "error": str(exc),
        }

    cache_stats = await get_cache_stats()
    return {
        "status": status,
        "documents_in_db": stats["total_documents"],
        "available_collections": stats["available_collections"],
        "missing_collections": stats["missing_collections"],
        "collections": stats["collections"],
        "cache": cache_stats,
    }


@app.post("/query", response_model=QueryResponse)
async def query(req: QueryRequest) -> QueryResponse:
    lang = resolve_language(req.language)
    history = await load_history(req.user_id)

    # 1. Niyatni aniqlash: islomiy dalil kerakmi?
    intent = await classify_intent(req.message, history)

    # 2a. Oddiy suhbat — bazasiz, tabiiy javob
    if not intent["needs_sources"]:
        answer = await generate_casual_answer(req.message, lang, history)
        await save_turn(req.user_id, req.message, answer)
        return QueryResponse(answer=answer, sources=[], cached=False)

    # 2b. Islomiy savol — kesh faqat kontekstsiz (birinchi) savollar uchun
    use_cache = redis_client is not None and not history
    cache_key = semantic_cache_key(req.message, lang)
    if use_cache:
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                await redis_client.incr(CACHE_HITS_KEY)
                payload = json.loads(cached)
                await save_turn(req.user_id, req.message, payload["answer"])
                return QueryResponse(**payload, cached=True)
            await redis_client.incr(CACHE_MISSES_KEY)
        except Exception:
            pass

    # 3. Hybrid retrieval (klassifikator bergan qidiruv so'rovi bilan)
    search_query = intent["search_query"] or req.message
    sources = retriever.search(search_query, language=lang)

    # 4. Faqat topilgan manbalardan javob
    answer, used_sources = await generate_grounded_answer(
        req.message, lang, sources, history
    )

    # 5. Keshlash — faqat manbali, muvaffaqiyatli javoblar
    not_found = NOT_FOUND_MESSAGES.get(lang, NOT_FOUND_MESSAGES["en"])
    if use_cache and used_sources and answer != not_found:
        try:
            await redis_client.set(
                cache_key,
                json.dumps(
                    {"answer": answer, "sources": used_sources}, ensure_ascii=False
                ),
                ex=CACHE_TTL_SECONDS,
            )
        except Exception:
            pass

    await save_turn(req.user_id, req.message, answer)
    return QueryResponse(answer=answer, sources=used_sources, cached=False)


@app.delete("/history/{user_id}")
async def clear_history(user_id: str) -> dict[str, str]:
    """Foydalanuvchi "yangi suhbat" boshlaganda chaqiriladi."""
    if redis_client is not None:
        try:
            await redis_client.delete(history_key(user_id))
        except Exception:
            pass
    return {"status": "cleared"}


@app.post("/verse", response_model=VerseResponse)
async def get_verse(req: VerseRequest) -> VerseResponse:
    """Aniq oyatni qaytaradi (surah + ayah bo'yicha)."""
    lang = resolve_language(req.language)
    result = retriever.search_verse(req.surah, req.ayah, language=lang)
    if result is None:
        raise HTTPException(status_code=404, detail="Oyat topilmadi")
    return VerseResponse(text=result["text"], metadata=result["metadata"])


@app.get("/quran/verses")
async def quran_verses(language: str = "uz_latin") -> dict[str, list[dict[str, Any]]]:
    """Return all bundled Quran translations in the legacy Android shape."""
    lang = resolve_language(language)
    filename = QURAN_LANGUAGE_FILES.get(lang)
    if filename is None:
        raise HTTPException(status_code=400, detail="unsupported Quran language")

    path = QURAN_DATA_DIR / filename
    if not path.exists():
        raise HTTPException(status_code=503, detail="Quran translation data is unavailable")

    arabic = await load_arabic_verses()
    if not arabic:
        raise HTTPException(status_code=503, detail="Arabic Quran text is unavailable")

    verses: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as data_file:
        for line in data_file:
            row = json.loads(line)
            verses.append({
                "surahNumber": row["surah"],
                "surahName": row.get("surah_name") or row.get("surah_name_en") or "",
                "surahNameArabic": row.get("surah_name_ar") or "",
                "ayahNumber": row["ayah"],
                "ayahText": arabic.get((row["surah"], row["ayah"]), ""),
                "ayahTextArabic": arabic.get((row["surah"], row["ayah"]), ""),
                "translation": row["text"],
                "transliteration": "",
            })
    return {"verses": verses}


async def load_arabic_verses() -> dict[tuple[int, int], str]:
    """Load canonical Uthmani verses once and retain them for this process."""
    global arabic_verses
    if arabic_verses is not None:
        return arabic_verses
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.get(QURAN_ARABIC_URL)
            response.raise_for_status()
        payload = response.json().get("data", {})
        result: dict[tuple[int, int], str] = {}
        for surah in payload.get("surahs", []):
            number = safe_int(surah.get("number"))
            for ayah in surah.get("ayahs", []):
                ayah_number = safe_int(ayah.get("numberInSurah"))
                text = str(ayah.get("text") or "").strip()
                if number and ayah_number and text:
                    result[(number, ayah_number)] = text
        if len(result) != 6236:
            raise ValueError(f"expected 6236 Arabic verses, received {len(result)}")
        arabic_verses = result
    except Exception:
        # A partial corpus must never be passed to users as a complete Quran.
        return {}
    return arabic_verses


# ---------------------------------------------------------------------------
# Yordamchi funksiyalar
# ---------------------------------------------------------------------------

def semantic_cache_key(message: str, language: str) -> str:
    normalized = " ".join(message.lower().split())
    digest = hashlib.sha256(f"{language}:{normalized}".encode()).hexdigest()
    return f"rag:cache:{digest}"


async def get_cache_stats() -> dict[str, float | int]:
    if redis_client is None:
        return {"hits": 0, "misses": 0, "hit_rate": 0.0}
    try:
        hits_raw, misses_raw = await redis_client.mget(CACHE_HITS_KEY, CACHE_MISSES_KEY)
        hits = int(hits_raw or 0)
        misses = int(misses_raw or 0)
    except Exception:
        return {"hits": 0, "misses": 0, "hit_rate": 0.0}
    total = hits + misses
    hit_rate = hits / total if total else 0.0
    return {"hits": hits, "misses": misses, "hit_rate": round(hit_rate, 4)}


def safe_int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0
