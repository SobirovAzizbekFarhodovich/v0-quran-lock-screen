#!/usr/bin/env python3
"""
Shayx Muhammad Sodiq Muhammad Yusuf tarjimasini QuranEnc'dan (uzbek_sadiq) olib, ilova
va server ma'lumotlarini qayta yaratadi.

QuranEnc tarjima matnini va izohlarni alohida beradi (matnda faqat "[1]" belgisi qoladi).
Avvalgi Tanzil fayli (uz.sodik) izohlarni oyat matni ichiga qavs bilan qo'shib yuborgan edi.

Yangilanadigan fayllar (arabcha matn va sura nomlari o'zgarmaydi, faqat tarjima va izoh):
    app/src/main/assets/quran/uz_cyrillic.json, uz_latin.json   — "translation", "footnotes"
    ai/rag/data/quran/uz_cyrillic.jsonl, uz_latin.jsonl          — "text", "footnotes"

Foydalanish (repo ildizidan):
    python3 ai/rag/scripts/import_quranenc.py --cache /tmp/quranenc
    # --cache: sura JSON'lari saqlanadigan papka; bor bo'lsa qayta yuklanmaydi.
"""

import argparse
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin  # noqa: E402

API = "https://quranenc.com/api/v1/translation/sura/uzbek_sadiq/{}"
SOURCE = "Muhammad Sodiq Muhammad Yusuf"
ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "app/src/main/assets/quran"
SERVER = ROOT / "ai/rag/data/quran"
VERSE_COUNT = 6236


def fetch(cache: Path) -> dict[tuple[int, int], dict]:
    cache.mkdir(parents=True, exist_ok=True)
    verses = {}
    for sura in range(1, 115):
        path = cache / f"s{sura}.json"
        if not path.exists():
            with urllib.request.urlopen(API.format(sura), timeout=60) as response:
                path.write_bytes(response.read())
            time.sleep(0.3)
        for row in json.loads(path.read_text(encoding="utf-8"))["result"]:
            verses[(int(row["sura"]), int(row["aya"]))] = row
    if len(verses) != VERSE_COUNT:
        sys.exit(f"QuranEnc: {len(verses)} oyat, {VERSE_COUNT} kutilgan edi")
    return verses


def fix_quotes(text: str) -> str:
    """Only unambiguous typos in the source: a straight quote, or a «, that closes an open «."""
    out = []
    depth = 0
    for i, ch in enumerate(text):
        nxt = text[i + 1] if i + 1 < len(text) else " "
        prev = text[i - 1] if i else " "
        if ch == '"':
            ch = "»" if depth else "«"
        elif ch == "«" and depth and not prev.isspace() and (nxt.isspace() or nxt in ".,;:!?)–—"):
            ch = "»"
        if ch == "«":
            depth += 1
        elif ch == "»":
            depth = max(0, depth - 1)
        out.append(ch)
    return "".join(out)


def clean_translation(text: str) -> str:
    text = re.sub(r"\s*\[\d+\]", "", text)
    return fix_quotes(re.sub(r"\s+", " ", text).strip())


def clean_footnotes(text: str) -> str:
    """'[1] (Izoh matni.)' -> 'Izoh matni.'"""
    text = re.sub(r"\s+", " ", re.sub(r"^\s*\[\d+\]\s*", "", text)).strip()
    if text.startswith("(") and text.endswith(")") and text.count("(") == text.count(")"):
        # Drop the wrapping pair only when it really wraps the whole note.
        depth = 0
        for i, ch in enumerate(text):
            depth += ch == "("
            depth -= ch == ")"
            if depth == 0 and i < len(text) - 1:
                break
        else:
            text = text[1:-1].strip()
    return fix_quotes(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    source = fetch(args.cache)

    cyrillic = {
        key: (clean_translation(row["translation"]), clean_footnotes(row["footnotes"]))
        for key, row in source.items()
    }

    for lang, convert in (("uz_cyrillic", lambda s: s), ("uz_latin", cyrillic_to_latin)):
        asset = ASSETS / f"{lang}.json"
        data = json.loads(asset.read_text(encoding="utf-8"))
        for verse in data["verses"]:
            translation, footnotes = cyrillic[(verse["surahNumber"], verse["ayahNumber"])]
            verse["translation"] = convert(translation)
            verse["footnotes"] = convert(footnotes)
        asset.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

        server = SERVER / f"{lang}.jsonl"
        rows = [json.loads(line) for line in server.read_text(encoding="utf-8").splitlines() if line.strip()]
        for row in rows:
            translation, footnotes = cyrillic[(row["surah"], row["ayah"])]
            row["text"] = convert(translation)
            row["footnotes"] = convert(footnotes)
            row["source"] = SOURCE
        server.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
        print(f"{lang}: {len(data['verses'])} oyat (ilova), {len(rows)} oyat (server)")


if __name__ == "__main__":
    main()
