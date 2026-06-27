#!/usr/bin/env python3
"""
Download all Sahih Muslim hadiths from zis-hadis.com API.

API endpoints:
  - Chapters: GET /api/chapters?bookId=<bookId>
  - Hadiths:  GET /api/hadiths?chapterId=<chapterId>&limit=<n>&skip=<n>
"""
import json
import time
import urllib.request
from pathlib import Path

BOOK_ID = "66b71ff547717ce3ebc8aa7a"
BASE = "https://www.zis-hadis.com/api"
OUTPUT = Path(__file__).resolve().parents[1] / "build" / "hadith_sources" / "sahih_muslim_zis.json"

HEADERS = {"User-Agent": "QuranReminderAndroid/1.0"}


def api_get(url: str) -> dict:
    """Fetch JSON from the API with retry."""
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as exc:
            if attempt == 3:
                raise
            wait = 2 ** attempt
            print(f"  Retry {attempt + 1} after {wait}s: {exc}")
            time.sleep(wait)


def fetch_chapters() -> list[dict]:
    data = api_get(f"{BASE}/chapters?bookId={BOOK_ID}")
    chapters = data.get("chapters", [])
    chapters.sort(key=lambda c: c.get("chapterNumber", 0))
    print(f"Found {len(chapters)} chapters")
    return chapters


def fetch_hadiths(chapter_id: str, chapter_title: str) -> list[dict]:
    """Fetch all hadiths for a chapter (paginated with limit=500)."""
    all_hadiths = []
    skip = 0
    batch_size = 500

    while True:
        url = f"{BASE}/hadiths?chapterId={chapter_id}&limit={batch_size}&skip={skip}"
        data = api_get(url)
        hadiths = data.get("hadiths", [])
        total = data.get("total", 0)
        all_hadiths.extend(hadiths)

        if len(all_hadiths) >= total or not hadiths:
            break
        skip += batch_size
        time.sleep(0.15)

    return all_hadiths


def normalize_hadith(raw: dict, chapter: dict) -> dict:
    """Extract a clean hadith record from raw API response."""
    hadith_obj = raw.get("hadith", {})
    sub_chapter = raw.get("subChapterTitle", {})
    return {
        "hadithNumber": raw.get("hadithNumber", 0),
        "orderNumber": raw.get("orderNumber", 0),
        "chapterNumber": chapter.get("chapterNumber", 0),
        "chapterTitle_uz": chapter.get("title_uz", ""),
        "chapterTitle_ar": chapter.get("title_ar", ""),
        "subChapterNumber": raw.get("subChapterNumber", 0),
        "subChapterTitle_uz": sub_chapter.get("uz", ""),
        "subChapterTitle_ar": sub_chapter.get("ar", ""),
        "narrator_uz": raw.get("narratorName", ""),
        "narrator_kr": raw.get("narratorKr", ""),
        "narrator_en": raw.get("narratorEn", ""),
        "text_uz": hadith_obj.get("uzbek", ""),
        "text_ar": hadith_obj.get("arabic", ""),
        "text_en": hadith_obj.get("english", ""),
        "text_kr": hadith_obj.get("kril", ""),
        "reference": raw.get("reference", ""),
        "source": raw.get("source", ""),
        "accuracy": raw.get("accuracy", ""),
        "appendix": raw.get("appendix", ""),
        "zis_id": raw.get("_id", ""),
    }


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    chapters = fetch_chapters()
    all_hadiths = []

    for i, chapter in enumerate(chapters):
        cid = chapter["_id"]
        title = chapter.get("title_uz", "")
        num = chapter.get("chapterNumber", "?")
        print(f"[{i + 1}/{len(chapters)}] Chapter {num}: {title} ...", end=" ", flush=True)

        raw_hadiths = fetch_hadiths(cid, title)
        for raw in raw_hadiths:
            all_hadiths.append(normalize_hadith(raw, chapter))

        print(f"{len(raw_hadiths)} hadiths")
        time.sleep(0.25)

    # Sort by chapter number then hadith order
    all_hadiths.sort(key=lambda h: (h["chapterNumber"], h["orderNumber"]))

    result = {
        "source": "https://www.zis-hadis.com",
        "book": "Sahih Muslim",
        "bookId": BOOK_ID,
        "totalChapters": len(chapters),
        "totalHadiths": len(all_hadiths),
        "hadiths": all_hadiths,
    }

    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nWrote {len(all_hadiths)} hadiths to {OUTPUT}")


if __name__ == "__main__":
    main()
