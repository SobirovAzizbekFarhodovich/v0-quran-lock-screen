#!/usr/bin/env python3
"""
Kitoblarni (PDF, TXT, JSON) parse qilib JSONL formatda chiqaradi.

Qur'on fayllar uchun → parse_quran_txt.py dan foydalaning.
Bu skript umumiy kitoblar uchun (duolar, hadislar, tafsir va h.k.)

Foydalanish:
    python ingest_books.py --input path/to/book.pdf --type dua --lang uz_cyrillic --out ../data/duas/duolar.jsonl
    python ingest_books.py --input path/to/books_dir/ --type hadith --lang uz_cyrillic --out ../data/hadith/hadis.jsonl
"""

import argparse
import json
import re
import sys
from pathlib import Path

from pypdf import PdfReader


def read_file(path: Path) -> str:
    """Faylni o'qiydi (PDF, TXT, JSON)."""
    if path.suffix.lower() == ".pdf":
        reader = PdfReader(str(path))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    if path.suffix.lower() == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return "\n\n".join(
                json.dumps(item, ensure_ascii=False) if isinstance(item, dict) else str(item)
                for item in data
            )
        return json.dumps(data, ensure_ascii=False)
    return path.read_text(encoding="utf-8")


def clean_text(text: str) -> str:
    """Matnni tozalaydi — ortiqcha bo'sh joylar, keraksiz belgilar."""
    # Ko'p bo'sh satrlarni ikkitaga qisqartirish
    text = re.sub(r"\n{3,}", "\n\n", text)
    # Har bir satr boshidagi ortiqcha bo'sh joylar
    lines = [line.strip() for line in text.split("\n")]
    text = "\n".join(lines)
    # Juda qisqa satrlarni (1-2 belgi) o'chirish (PDF noise)
    lines = text.split("\n")
    cleaned_lines = []
    for line in lines:
        if len(line) <= 2 and not line.strip().isdigit():
            continue
        cleaned_lines.append(line)
    return "\n".join(cleaned_lines).strip()


def paragraph_chunks(text: str, max_chars: int = 1500, overlap_chars: int = 200) -> list[str]:
    """Matnni paragraflar bo'yicha bo'ladi (overlap bilan).

    Args:
        text: To'liq matn
        max_chars: Chunk uchun maksimal belgilar soni
        overlap_chars: Chunklar orasidagi overlap

    Returns:
        Chunks ro'yxati
    """
    paragraphs = text.split("\n\n")
    chunks = []
    current_chunk = ""

    for para in paragraphs:
        para = para.strip()
        if not para:
            continue

        # Agar paragraf o'zi juda uzun bo'lsa — bo'lamiz
        if len(para) > max_chars:
            if current_chunk:
                chunks.append(current_chunk.strip())
                current_chunk = ""

            # Uzun paragrafni jumlalar bo'yicha bo'lish
            sentences = re.split(r"(?<=[.!?।।])\s+", para)
            for sent in sentences:
                if len(current_chunk) + len(sent) + 1 > max_chars:
                    if current_chunk:
                        chunks.append(current_chunk.strip())
                        # Overlap: oxirgi bir necha jumla
                        overlap_text = current_chunk[-overlap_chars:] if len(current_chunk) > overlap_chars else ""
                        current_chunk = overlap_text + " " + sent
                    else:
                        current_chunk = sent
                else:
                    current_chunk += " " + sent if current_chunk else sent
        else:
            if len(current_chunk) + len(para) + 2 > max_chars:
                chunks.append(current_chunk.strip())
                overlap_text = current_chunk[-overlap_chars:] if len(current_chunk) > overlap_chars else ""
                current_chunk = overlap_text + "\n\n" + para
            else:
                current_chunk += "\n\n" + para if current_chunk else para

    if current_chunk.strip():
        chunks.append(current_chunk.strip())

    return chunks


def main() -> None:
    parser = argparse.ArgumentParser(description="Kitoblarni JSONL formatga o'tkazadi")
    parser.add_argument("--input", type=Path, required=True, help="Fayl yoki papka yo'li")
    parser.add_argument("--type", type=str, required=True,
                        choices=["dua", "hadith", "tafsir", "fiqh", "general"],
                        help="Kitob turi")
    parser.add_argument("--lang", type=str, required=True,
                        help="Til kodi: uz_cyrillic, uz_latin, en, ru")
    parser.add_argument("--source", type=str, default="",
                        help="Manba nomi")
    parser.add_argument("--out", type=Path, required=True, help="Chiqish JSONL fayl")
    parser.add_argument("--max-chunk", type=int, default=1500,
                        help="Chunk uchun maksimal belgilar soni")
    args = parser.parse_args()

    files = [args.input] if args.input.is_file() else sorted(
        f for f in args.input.glob("**/*")
        if f.suffix.lower() in {".pdf", ".txt", ".json"}
    )

    if not files:
        print(f"❌ Fayllar topilmadi: {args.input}")
        sys.exit(1)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    total = 0

    with args.out.open("w", encoding="utf-8") as out:
        for filepath in files:
            print(f"  O'qilmoqda: {filepath.name}")
            raw_text = read_file(filepath)
            cleaned = clean_text(raw_text)
            chunks = paragraph_chunks(cleaned, max_chars=args.max_chunk)

            for idx, chunk in enumerate(chunks):
                entry = {
                    "text": chunk,
                    "type": args.type,
                    "lang": args.lang,
                    "source": args.source or filepath.stem,
                    "chunk_idx": idx,
                    "total_chunks": len(chunks),
                    "filename": filepath.name,
                }
                out.write(json.dumps(entry, ensure_ascii=False) + "\n")
                total += 1

    print(f"✅ {total} ta chunk → {args.out}")


if __name__ == "__main__":
    main()
