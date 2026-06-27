#!/usr/bin/env python3
import json
import pathlib
import time
import urllib.request

BASE_URL = "https://quranenc.com/api/v1/translation/sura/uzbek_sadiq/{surah}"
OUTPUT = pathlib.Path("app/src/main/assets/quran/verses.json")


def fetch_surah(surah: int) -> list[dict]:
    request = urllib.request.Request(
        BASE_URL.format(surah=surah),
        headers={"User-Agent": "QuranReminderAndroid/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return payload["result"]


def main() -> None:
    verses: list[dict] = []
    for surah in range(1, 115):
        verses.extend(fetch_surah(surah))
        time.sleep(0.12)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(
            {
                "source": "https://quranenc.com/en/browse/uzbek_sadiq",
                "translation_key": "uzbek_sadiq",
                "result": verses,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"Wrote {len(verses)} verses to {OUTPUT}")


if __name__ == "__main__":
    main()
