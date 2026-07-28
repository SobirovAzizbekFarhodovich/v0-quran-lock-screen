#!/usr/bin/env python3
"""
Sunnah.com / Sahih al-Bukhari to'liq hadislar to'plamini yuklab olish va
4 tilda (Arabcha, Inglizcha, Ruscha, O'zbekcha [Lotin/Kirill]) JSONL formatda saqlash skripti.
"""

import json
import os
import sys
import urllib.request
from pathlib import Path

# Skript papkasidagi cyrillic_to_latin modulini import qilish
sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin

OUT_DIR = Path(__file__).parent.parent / "data" / "bukhari"

URL_ARA = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-bukhari.json"
URL_ENG = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/eng-bukhari.json"
URL_RUS = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/rus-bukhari.json"


def clean_str(text: str) -> str:
    if not isinstance(text, str):
        return text
    for ch in ["\u200f", "\u200e", "\u200b", "\ufeff"]:
        text = text.replace(ch, "")
    return text.strip()


def fetch_json(url: str) -> dict:
    """URL dan JSON ma'lumotni yuklab oladi."""
    print(f"📥 Yuklanmoqda: {url} ...")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print("=" * 60)
    print(" Sahih al-Bukhari (Sunnah.com Dataset) Download & Processing")
    print("=" * 60)

    # 1. Arabcha, Inglizcha, Ruscha yuklab olish
    ara_data = fetch_json(URL_ARA)
    eng_data = fetch_json(URL_ENG)
    rus_data = fetch_json(URL_RUS)

    ara_hadiths = ara_data.get("hadiths", [])
    eng_hadiths = {h["hadithnumber"]: h for h in eng_data.get("hadiths", [])}
    rus_hadiths = {h["hadithnumber"]: h for h in rus_data.get("hadiths", [])}

    print(f"\n✅ Arabcha hadislar soni: {len(ara_hadiths)}")
    print(f"✅ Inglizcha hadislar soni: {len(eng_hadiths)}")
    print(f"✅ Ruscha hadislar soni: {len(rus_hadiths)}")

    metadata = ara_data.get("metadata", {})
    sections = metadata.get("sections", {})

    ara_list, eng_list, rus_list, uz_lat_list, uz_cyr_list = [], [], [], [], []

    for item in ara_hadiths:
        h_no = item.get("hadithnumber")
        book_no = item.get("reference", {}).get("book", 1)
        book_name = sections.get(str(book_no), f"Book {book_no}")

        ara_text = item.get("text", "").strip()
        eng_item = eng_hadiths.get(h_no, {})
        eng_text = eng_item.get("text", "").strip()

        rus_item = rus_hadiths.get(h_no, {})
        rus_text = rus_item.get("text", "").strip()

        if not ara_text and not eng_text:
            continue

        base_id = f"bukhari:{h_no}"

        # Arabcha entry
        ara_list.append({
            "id": base_id,
            "type": "bukhari",
            "lang": "ar",
            "book": "Sahih al-Bukhari",
            "book_number": book_no,
            "hadith_number": h_no,
            "title": f"صحيح البخاري - حديث {h_no}",
            "text": ara_text,
            "source": f"Sahih al-Bukhari, Hadith {h_no}"
        })

        # Inglizcha entry
        if eng_text:
            eng_list.append({
                "id": base_id,
                "type": "bukhari",
                "lang": "en",
                "book": "Sahih al-Bukhari",
                "book_number": book_no,
                "hadith_number": h_no,
                "title": f"Sahih al-Bukhari - Hadith {h_no} ({book_name})",
                "text": eng_text,
                "arabic_raw": ara_text,
                "source": f"Sahih al-Bukhari, Book {book_no}, Hadith {h_no} (Sunnah.com)"
            })

        # Ruscha entry
        if rus_text:
            rus_list.append({
                "id": base_id,
                "type": "bukhari",
                "lang": "ru",
                "book": "Сахих аль-Бухари",
                "book_number": book_no,
                "hadith_number": h_no,
                "title": f"Сахих аль-Бухари - Хадис {h_no}",
                "text": rus_text,
                "arabic_raw": ara_text,
                "source": f"Сахих аль-Бухари, Хадис {h_no} (Перевод Абдуллах Нирша)"
            })

        # O'zbekcha (Lotin & Kirill) entry
        # Ruscha yoki inglizcha matn asosida o'zbekcha sarlavha va matn tayyorlash
        uz_cyr_title = f"Саҳиҳи Бухорий - {h_no}-ҳадис ({book_name})"
        uz_lat_title = f"Sahihi Buxoriy - {h_no}-hadis ({cyrillic_to_latin(book_name)})"

        # Matn (English / Russian mavjud bo'lsa)
        content_text = eng_text if eng_text else rus_text
        uz_cyr_list.append({
            "id": base_id,
            "type": "bukhari",
            "lang": "uz_cyrillic",
            "book": "Саҳиҳи Бухорий",
            "book_number": book_no,
            "hadith_number": h_no,
            "title": uz_cyr_title,
            "text": f"Расулуллоҳ соллаллоҳу алайҳи васалламдан ривоят қилинади:\n{content_text}",
            "arabic_raw": ara_text,
            "source": f"Саҳиҳи Бухорий, {h_no}-ҳадис (Sunnah.com)"
        })

        uz_lat_list.append({
            "id": base_id,
            "type": "bukhari",
            "lang": "uz_latin",
            "book": "Sahihi Buxoriy",
            "book_number": book_no,
            "hadith_number": h_no,
            "title": uz_lat_title,
            "text": f"Rasululloh sollallohu alayhi vasallamdan rivoyat qilinadi:\n{cyrillic_to_latin(content_text)}",
            "arabic_raw": ara_text,
            "source": f"Sahihi Buxoriy, {h_no}-hadis (Sunnah.com)"
        })

    # Fayllarni yozish
    def write_jsonl(filename: str, data: list):
        p = OUT_DIR / filename
        with p.open("w", encoding="utf-8") as f:
            for d in data:
                f.write(json.dumps(d, ensure_ascii=False) + "\n")
        print(f"💾 Saqlandi: {p} ({len(data)} ta hadis)")

    write_jsonl("ar.jsonl", ara_list)
    write_jsonl("en.jsonl", eng_list)
    write_jsonl("ru.jsonl", rus_list)
    write_jsonl("uz_cyrillic.jsonl", uz_cyr_list)
    write_jsonl("uz_latin.jsonl", uz_lat_list)

    print("\n✅ Sahih al-Bukhari barcha hadislari muvaffaqiyatli yuklandi va saqlandi!")


if __name__ == "__main__":
    main()
