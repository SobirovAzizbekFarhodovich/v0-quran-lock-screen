"""
Islamic AI RAG Service — FastAPI server.

ChromaDB + BM25 hybrid retrieval bilan Qur'on oyatlari va Islomiy
adabiyotdan savolga javob beradi.

Tillar: uz_cyrillic, uz_latin, en, ru
"""

import hashlib
import json
from contextlib import asynccontextmanager
import os
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
QURAN_DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "quran"
QURAN_LANGUAGE_FILES = {
    "uz": "uz_latin.jsonl",
    "uz_latin": "uz_latin.jsonl",
    "uz_cyrillic": "uz_cyrillic.jsonl",
    "en": "en.jsonl",
    "ru": "ru.jsonl",
}
# The translation files deliberately contain translations only.  Keep the
# canonical Uthmani text on the server as well, so a device receives Arabic and
# its chosen translation in one offline-download response.
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


app = FastAPI(title="Islamic AI RAG Service", version="2.0.0", lifespan=lifespan)


# Har bir til учун system prompt
SYSTEM_PROMPTS = {
    "uz_cyrillic": (
        "Сиз фақат ишончли ва саҳиҳ манбаларга (Қуръони Карим, Ҳиснул Муслим, Риёзус Солиҳийн, Ал-Азкор, Булуғ ал-Маром) "
        "асосланиб жавоб берувчи Исломий АИ ёрдамчисиз.\n"
        "1. Ҳар бир жавобда манбани аниқ кўрсатинг (Қуръон оятлари учун Сура ва Оят рақами, Ҳадислар учун Китоб номи, боби ва ҳадис рақами, Дуолар учун Ҳиснул Муслим ёки Қуръон манбаси).\n"
        "2. Заиф ёки тўқима ҳадисларни асло ишлатманг.\n"
        "3. Дуолар берилганда арабча матни, маъно таржимаси ва манбасини бирга беринг.\n"
        "4. Фатво берманг, фиқҳий ва фатво масалаларида ваколатли муфтийларга йўналтиринг.\n"
        "Жавобни мукаммал ва аниқ ўзбек кирилл ёзувида беринг."
    ),
    "uz_latin": (
        "Siz faqat ishonchli va sahih manbalarga (Qur'oni Karim, Hisnul Muslim, Riyozus Solihiyn, Al-Adhkar, Bulugh al-Maram) "
        "asoslanib javob beruvchi Islomiy AI yordamchisiz.\n"
        "1. Har bir javobda manbani aniq ko'rsating (Qur'on oyatlari uchun Sura va Oyat raqami, Hadislar uchun Kitob nomi, bobi va hadis raqami, Duolar uchun Hisnul Muslim yoki Qur'on manbasi).\n"
        "2. Zaif yoki to'qima hadislarni aslo ishlatmang.\n"
        "3. Duolar berilganda arabcha matni, ma'no tarjimasi va manbasini birga bering.\n"
        "4. Fatvo bermang, fiqhiy va fatvo masalalarida vakolatli muftiylarga yo'naltiring.\n"
        "Javobni mukammal va aniq o'zbek lotin yozuvida bering."
    ),
    "en": (
        "You are an Islamic AI assistant relying exclusively on authentic sources (Noble Quran, Hisnul Muslim, Riyadh as-Salihin, Al-Adhkar, Bulugh al-Maram).\n"
        "1. Always cite exact references for every claim (Surah and Ayah number for Quran, Book name, chapter and Hadith number for Ahadith, Hisnul Muslim or Quran citation for Duas).\n"
        "2. Never use weak or fabricated narrations.\n"
        "3. When providing Duas, include Arabic text, translation, and authentic reference.\n"
        "4. Do not issue fatwa; refer legal/jurisprudential rulings to qualified scholars.\n"
        "Respond clearly and accurately in English."
    ),
    "ru": (
        "Вы — Исламский ИИ-ассистент, опирающийся исключительно на достоверные источники (Священный Коран, Крепость мусульманина, Рияд ас-Салихин, Аль-Азкар, Булуг аль-Марам).\n"
        "1. Всегда приводите точные ссылки (Сура и Аят Корана, Название книги, глава и номер хадиса, либо Крепость мусульманина для дуа).\n"
        "2. Никогда не используйте слабые или вымышленные хадисы.\n"
        "3. Приводя дуа, указывайте арабский текст, перевод и источник.\n"
        "4. Не выносите фетвы, направляйте квалифицированным богословам.\n"
        "Отвечайте четко и грамотно на русском языке."
    ),
}

# "uz" ni "uz_cyrillic" ga yo'naltirish (backward compatibility)
LANGUAGE_ALIASES = {
    "uz": "uz_cyrillic",
}


def resolve_language(lang: str) -> str:
    """Til aliasini resolve qiladi."""
    return LANGUAGE_ALIASES.get(lang, lang)



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

    # Keshni tekshiramiz
    cache_key = semantic_cache_key(req.message, lang)
    if redis_client is not None:
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                await redis_client.incr(CACHE_HITS_KEY)
                payload = json.loads(cached)
                return QueryResponse(**payload, cached=True)
            await redis_client.incr(CACHE_MISSES_KEY)
        except Exception:
            pass  # Redis xatoligi — keshlashni o'tkazamiz

    # Hybrid retrieval
    sources = retriever.search(req.message, language=lang)

    # LLM orqali javob generatsiya
    answer = await generate_answer(req.message, lang, sources)

    response = QueryResponse(answer=answer, sources=sources, cached=False)

    # Natijani keshlaymiz
    if redis_client is not None:
        try:
            await redis_client.set(
                cache_key,
                json.dumps({"answer": answer, "sources": sources}, ensure_ascii=False),
                ex=CACHE_TTL_SECONDS,
            )
        except Exception:
            pass

    return response


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
    """Return all bundled Quran translations in the legacy Android shape.

    The repository has complete translations, but deliberately does not claim
    to have a complete Arabic-text corpus.  ``ayahTextArabic`` is therefore
    empty instead of being fabricated; the Android client hides that line.
    """
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


async def generate_answer(
    message: str, language: str, sources: list[dict[str, Any]]
) -> str:
    """Gemini API yoki OpenRouter API orqali аниқ ва манбали жавоб генерация қилади."""
    gemini_key = os.getenv("GEMINI_API_KEY", "")
    openrouter_key = os.getenv("OPENROUTER_API_KEY", "")

    # Kontekstni олинган манбалардан тоза шаклда тайёрлаш
    context_parts = []
    for item in sources[:6]:
        meta = item.get("metadata", {})
        item_type = meta.get("type", "")
        source_title = meta.get("source") or meta.get("title") or ""
        
        surah = safe_int(meta.get("surah"))
        ayah = safe_int(meta.get("ayah"))
        hadith_number = safe_int(meta.get("hadith_number"))

        if item_type == "quran" or surah > 0:
            ref = f"[Қуръон, {surah}:{ayah}-оят]"
        elif item_type in ["bukhari", "muslim"] or hadith_number > 0:
            ref = f"[{source_title or 'Саҳиҳ Ҳадис'}]"
        elif source_title:
            ref = f"[{source_title}]"
        else:
            ref = "[Манба]"
            
        context_parts.append(f"{ref}\n{item['text']}")

    context = "\n\n---\n\n".join(context_parts) if context_parts else "Аниқ манба топилмади."
    system_prompt = SYSTEM_PROMPTS.get(language, SYSTEM_PROMPTS["en"])

    user_prompt = (
        f"Савол: {message}\n\n"
        f"Қуйидаги ишончли Исломий манбалардан (Қуръон оятлари, Саҳиҳ ҳадислар ва дуолар) фойдаланиб жавоб беринг:\n\n"
        f"{context}"
    )

    # 1. Gemini API (агар GEMINI_API_KEY созланган бўлса)
    if gemini_key:
        gemini_model = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{gemini_model}:generateContent?key={gemini_key}"
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(
                url,
                headers={"Content-Type": "application/json"},
                json={
                    "contents": [
                        {
                            "role": "user",
                            "parts": [{"text": f"{system_prompt}\n\n{user_prompt}"}]
                        }
                    ],
                    "generationConfig": {
                        "temperature": 0.2,
                        "maxOutputTokens": 1500
                    }
                }
            )
        if resp.status_code == 200:
            data = resp.json()
            candidates = data.get("candidates", [])
            if candidates and "content" in candidates[0]:
                parts = candidates[0]["content"].get("parts", [])
                if parts:
                    return parts[0].get("text", "")

    # 2. OpenRouter API (агар OPENROUTER_API_KEY созланган бўлса)
    if openrouter_key:
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(
                "https://openrouter.ai/api/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {openrouter_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": os.getenv("OPENROUTER_MODEL", "anthropic/claude-3.5-sonnet"),
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    "temperature": 0.2,
                    "max_tokens": 1500,
                },
            )
        if resp.status_code == 200:
            data = resp.json()
            return data["choices"][0]["message"]["content"]

    # 3. Fallback: API kalitlar созланмаган бўлса, RAG манбаларининг ўзини чиройли шаклда қайтаради
    if sources:
        res = "Ишончли манбалардан топилган маълумотлар:\n\n"
        for idx, s in enumerate(sources[:4], 1):
            meta = s.get("metadata", {})
            title = meta.get("source") or meta.get("title") or "Манба"
            res += f"{idx}. **{title}**:\n{s['text']}\n\n"
        return res

    return "Кечирасиз, берилган савол бўйича манбалардан маълумот топилмади."


def semantic_cache_key(message: str, language: str) -> str:
    """Semantik kesh kalitini yaratadi."""
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
