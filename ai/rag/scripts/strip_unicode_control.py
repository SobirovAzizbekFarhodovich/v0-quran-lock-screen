#!/usr/bin/env python3
"""
JSONL fayllardagi ko'rinmas Unicode nazorat belgilarini (U+200F, U+200E, U+200B, U+FEFF va b.)
tozalovchi skript.

Bu belgilar VS Code da qizil [U+200F] bo'lib ko'rinadi va qidiruvda shovqin tug'dirishi mumkin.
"""

import json
from pathlib import Path

DATA_DIR = Path(__file__).parent.parent / "data"

# Olsa bo'ladigan ko'rinmas/xalaqit beruvchi unicode belgilari
CONTROL_CHARS = [
    "\u200f",  # Right-to-Left Mark (RLM)
    "\u200e",  # Left-to-Right Mark (LRM)
    "\u200b",  # Zero Width Space (ZWSP)
    "\u200c",  # Zero Width Non-Joiner
    "\u200d",  # Zero Width Joiner
    "\ufeff",  # Byte Order Mark (BOM)
]


def clean_string(text: str) -> str:
    if not isinstance(text, str):
        return text
    for ch in CONTROL_CHARS:
        text = text.replace(ch, "")
    return text


def clean_object(obj):
    if isinstance(obj, str):
        return clean_string(obj)
    elif isinstance(obj, dict):
        return {k: clean_object(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [clean_object(x) for x in obj]
    return obj


def main():
    jsonl_files = list(DATA_DIR.rglob("*.jsonl"))
    print(f"Barcha JSONL fayllar tozalanmoqda: {len(jsonl_files)} ta fayl...")

    cleaned_count = 0
    for filepath in jsonl_files:
        lines = []
        modified = False
        with filepath.open("r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                data = json.loads(line)
                cleaned_data = clean_object(data)
                lines.append(json.dumps(cleaned_data, ensure_ascii=False) + "\n")

        with filepath.open("w", encoding="utf-8") as f:
            f.writelines(lines)
        cleaned_count += 1
        print(f"  ✅ Tozalandi: {filepath.relative_to(DATA_DIR)}")

    print(f"\n✨ Barcha {cleaned_count} ta fayldan U+200F va barcha ko'rinmas unicode belgilar to'liq tozalandi!")


if __name__ == "__main__":
    main()
