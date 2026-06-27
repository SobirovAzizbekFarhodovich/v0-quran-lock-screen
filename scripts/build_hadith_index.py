#!/usr/bin/env python3
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "build" / "hadith_sources"
OUT_FILE = ROOT / "app" / "src" / "main" / "assets" / "hadith" / "hadith_index.json"

ZIS_MUSLIM_FILE = SOURCE_DIR / "sahih_muslim_zis.json"

BOOKS = [
    {
        "id": "bukhari_1",
        "book": "Sahihi Buxoriy",
        "volume": "1-jild",
        "pdf": SOURCE_DIR / "bukhari_jild_1.pdf",
        "txt": SOURCE_DIR / "bukhari_jild_1.txt",
        "source": "Imom al-Buxoriy. Al-Jome' as-sahih. 1-jild. Arabchadan Zokirjon Ismoil tarjimasi.",
    },
    {
        "id": "muslim_pdf",
        "book": "Sahihi Muslim",
        "volume": "",
        "pdf": SOURCE_DIR / "sahihi_muslim.pdf",
        "txt": SOURCE_DIR / "sahihi_muslim.txt",
        "source": "Imom Muslim. Sahihi Muslim. O'zbek tarjima PDF manbasi.",
    },
]

HADITH_MARKERS = re.compile(
    r"(rivoyat qil|rivoyat et|naql qil|aytganlar|aytdilar|dedilar|deganlar|Rasululloh|Nabiy)",
    re.IGNORECASE,
)
NEW_HADITH_RE = re.compile(
    r"^.{0,180}\b(rivoyat qil|rivoyat et|naql qil|aytadilar|aytdilar|aytganlar|dedilar|deydilar)\b",
    re.IGNORECASE,
)
CHAPTER_RE = re.compile(r"^\s*\d+\s*-\s*bob\.\s*(.+)$", re.IGNORECASE)


def extract_text(book):
    pdf = book["pdf"]
    txt = book["txt"]
    if txt.exists() and txt.stat().st_size > 1000:
        return txt.read_text(encoding="utf-8", errors="ignore")
    if not pdf.exists() or pdf.stat().st_size < 1000:
        return ""
    subprocess.run(
        ["pdftotext", "-layout", str(pdf), str(txt)],
        check=True,
        cwd=str(ROOT),
    )
    return txt.read_text(encoding="utf-8", errors="ignore")


def clean_lines(text):
    cleaned = []
    for raw in text.splitlines():
        line = re.sub(r"\s+", " ", raw).strip()
        if not line:
            cleaned.append("")
            continue
        if "www.ziyouz.com" in line.lower():
            continue
        if "kutubxonasi" in line.lower():
            continue
        if "sahihi buxoriy" in line.lower() and "jild" in line.lower():
            continue
        if re.fullmatch(r"\d+", line):
            continue
        cleaned.append(line)
    return cleaned


def split_paragraphs(lines):
    paragraphs = []
    current = []
    current_chapter = ""
    chapter_for_current = ""

    def flush():
        nonlocal current, chapter_for_current
        text = " ".join(current).strip()
        current = []
        if len(text) >= 80:
            paragraphs.append((chapter_for_current or current_chapter, text))

    for line in lines:
        if not line:
            flush()
            continue
        match = CHAPTER_RE.match(line)
        if match:
            flush()
            current_chapter = match.group(1).strip()
            continue
        if not current:
            chapter_for_current = current_chapter
        current.append(line)
        if len(" ".join(current)) > 1400:
            flush()
    flush()
    return paragraphs


def narrator_from(text):
    prefix = text[:180]
    patterns = [
        r"^(.{3,90}?)\s+rivoyat qil",
        r"^(.{3,90}?)\s+aytganlar",
        r"^(.{3,90}?)\s+aytdilar",
        r"^(.{3,90}?)\s+dedilar",
    ]
    for pattern in patterns:
        match = re.search(pattern, prefix, re.IGNORECASE)
        if match:
            return match.group(1).strip(" :,”\"")
    return ""


def starts_new_hadith(text):
    lowered = text.lower().strip()
    if lowered.startswith(("ushbu hadis", "shunda", "so'ng", "keyin", "tushmagan", "mazkur")):
        return False
    return NEW_HADITH_RE.search(text[:240]) is not None


def make_entries(book):
    text = extract_text(book)
    if not text:
        return []
    lines = clean_lines(text)
    paragraphs = split_paragraphs(lines)
    entries = []
    buffer = []
    chapter = ""

    def flush():
        nonlocal buffer, chapter
        text = " ".join(buffer).strip()
        buffer = []
        if len(text) < 120 or not HADITH_MARKERS.search(text):
            return
        number = len(entries) + 1
        entries.append(
            {
                "id": f"{book['id']}_{number}",
                "book": book["book"],
                "volume": book["volume"],
                "chapter": chapter,
                "narrator": narrator_from(text),
                "text": text,
                "source": book["source"],
                "reference": f"{book['book']} {book['volume']}, {chapter}".strip(" ,"),
            }
        )

    for para_chapter, paragraph in paragraphs:
        if starts_new_hadith(paragraph) and buffer:
            flush()
        if not buffer:
            chapter = para_chapter
        buffer.append(paragraph)
        if len(" ".join(buffer)) > 1800:
            flush()
    flush()
    return entries


def make_entries_from_zis() -> list[dict]:
    """Parse hadiths from the structured ZIS JSON (clean API data)."""
    if not ZIS_MUSLIM_FILE.exists():
        print("ZIS Muslim file not found, skipping structured import")
        return []
    raw = json.loads(ZIS_MUSLIM_FILE.read_text(encoding="utf-8"))
    hadiths = raw.get("hadiths", [])
    entries = []
    for h in hadiths:
        text_uz = re.sub(r"\s+", " ", h.get("text_uz", "")).strip()
        if not text_uz or len(text_uz) < 30:
            continue
        narrator = h.get("narrator_uz", "").strip()
        chapter = h.get("chapterTitle_uz", "").strip()
        sub_chapter = h.get("subChapterTitle_uz", "").strip()
        hadith_num = h.get("hadithNumber", 0)
        chapter_num = h.get("chapterNumber", 0)
        full_chapter = f"{chapter}, {sub_chapter}".strip(", ") if sub_chapter else chapter
        ref_parts = [f"Sahih Muslim"]
        if hadith_num:
            ref_parts.append(f"{hadith_num}-hadis")
        entries.append(
            {
                "id": f"muslim_{hadith_num or len(entries) + 1}",
                "book": "Sahihi Muslim",
                "volume": "",
                "chapter": full_chapter,
                "narrator": narrator,
                "text": text_uz,
                "source": "Imom Muslim. Sahihi Muslim. zis-hadis.com tarjimasi.",
                "reference": ", ".join(ref_parts),
            }
        )
    return entries


def main():
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    entries = []

    # 1. ZIS structured data (clean, high quality) — Muslim
    zis_entries = make_entries_from_zis()
    entries.extend(zis_entries)
    print(f"ZIS Muslim: {len(zis_entries)} entries")

    # 2. PDF-extracted data (Bukhari + fallback Muslim)
    for book in BOOKS:
        pdf_entries = make_entries(book)
        entries.extend(pdf_entries)
        print(f"PDF {book['id']}: {len(pdf_entries)} entries")

    with OUT_FILE.open("w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)
    print(f"Wrote {len(entries)} total hadith entries to {OUT_FILE}")


if __name__ == "__main__":
    main()

