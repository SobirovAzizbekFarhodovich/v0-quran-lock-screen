#!/usr/bin/env python3
"""
"Muqaddas Oylar va Mustajob Duolar" kitobini structured JSONL formatga parse qiladi.

- Kirill va Lotin versiyalarini yaratadi
- Duolar, oyatlar, salavotlar, oy va kunlar fazilatlarini bo'limlar bo'yicha ajratadi
- ChromaDB ingest uchun tayyorlaydi
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Skript papkasidagi cyrillic_to_latin modulni import qilish
sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin


def clean_line(line: str) -> str:
    """OCR va sahifa raqamlarining xatolarini tozalaydi."""
    line = line.strip()
    # Sahifa belgilari (masalan, \x0c, \x0c12, 12 J)
    line = re.sub(r"[\x00-\x1f\x7f-\x9f]", "", line)
    line = re.sub(r"^\d+\s*J?\s*", "", line)
    # Ko'p joylarga bo'lingan xatolar
    line = re.sub(r"\s+", " ", line)
    return line.strip()


def parse_dua_book(file_path: Path) -> list[dict]:
    """Dua kitobini bo'limlar bo'yicha parse qiladi."""
    raw_lines = file_path.read_text(encoding="utf-8", errors="ignore").splitlines()
    
    cleaned_lines = []
    for line in raw_lines:
        c = clean_line(line)
        if c:
            cleaned_lines.append(c)
            
    # Paragraf / Mantiqiy bloklar yaratish
    blocks = []
    current_block = []
    current_title = "Muqaddas Oylar va Mustajob Duolar"
    
    for line in cleaned_lines:
        # Sarlavha belgilari (KATTA HARFLARDA yoki ma'lum sarlavhalar)
        if (len(line) < 60 and line.isupper()) or any(keyword in line for keyword in [
            "МУҚАДДИМА", "ДУО ҲАҚИДА", "ҚУРЪОНИЙ ДУОЛАР", "ДУОНИНГ ФАЗИЛАТИ",
            "РАСУЛУЛЛОҲНИНГ ДУО", "ДУОНИНГ ОДОБИ", "САЛОТО ВА САЛОМ", "РАЖАБ ОЙИ",
            "ШАЪБОН ОЙИ", "БАРОАТ КЕЧАСИ", "РАМАЗОН ОЙИ", "ҚАДР КЕЧАСИ", "ИФТОР ДУОЛАРИ",
            "ИСТИҒФОР ДУОЛАРИ", "ТАВБА ДУОСИ", "ИСМИ АЪЗАМ", "ОЯТУЛ КУРСИЙ", "ЯСИН"
        ]):
            if current_block:
                text_content = " ".join(current_block).strip()
                if len(text_content) > 50:
                    blocks.append({
                        "title": current_title,
                        "text": text_content
                    })
                current_block = []
            current_title = line
        else:
            current_block.append(line)
            
        # Blok hajmi yetarlicha katta bo'lsa
        if len(" ".join(current_block)) > 1200:
            text_content = " ".join(current_block).strip()
            blocks.append({
                "title": current_title,
                "text": text_content
            })
            current_block = []
            
    if current_block:
        text_content = " ".join(current_block).strip()
        if len(text_content) > 50:
            blocks.append({
                "title": current_title,
                "text": text_content
            })

    entries = []
    for idx, block in enumerate(blocks):
        entries.append({
            "id": f"dua:{idx+1}",
            "title": block["title"],
            "text": block["text"],
            "type": "dua",
            "source": "Muqaddas Oylar va Mustajob Duolar",
        })

    return entries


def main():
    parser = argparse.ArgumentParser(description="Dua kitobini parse qiladi")
    parser.add_argument("--input", type=Path, required=True, help="TXT kitob yo'li")
    parser.add_argument("--out-dir", type=Path, required=True, help="Chiqish data/duas papkasi")
    args = parser.parse_args()

    if not args.input.exists():
        print(f"❌ Fayl topilmadi: {args.input}")
        sys.exit(1)

    cyrillic_entries = parse_dua_book(args.input)
    
    # Kirill JSONL
    args.out_dir.mkdir(parents=True, exist_ok=True)
    cyr_out = args.out_dir / "uz_cyrillic.jsonl"
    with cyr_out.open("w", encoding="utf-8") as f:
        for entry in cyrillic_entries:
            e = dict(entry)
            e["lang"] = "uz_cyrillic"
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    print(f"✅ {len(cyrillic_entries)} ta duolar/bo'limlar → {cyr_out}")

    # Lotin JSONL
    latin_out = args.out_dir / "uz_latin.jsonl"
    with latin_out.open("w", encoding="utf-8") as f:
        for entry in cyrillic_entries:
            e = dict(entry)
            e["lang"] = "uz_latin"
            e["title"] = cyrillic_to_latin(entry["title"])
            e["text"] = cyrillic_to_latin(entry["text"])
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    print(f"✅ {len(cyrillic_entries)} ta duolar/bo'limlar → {latin_out}")


if __name__ == "__main__":
    main()
