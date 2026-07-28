#!/usr/bin/env python3
"""
Uzbek Kirill → Lotin transliteratsiya moduli.

O'zbek tilining rasmiy Kirill-Lotin konvertatsiya qoidalariga asoslangan.
Maxsus holatlar: Ц→Ts, Ч→Ch, Ш→Sh, Ё→Yo, Ю→Yu, Я→Ya, Ғ→Gʻ, Қ→Q, Ҳ→H, Ъ→ʼ, Ў→Oʻ
"""

# Kirill → Lotin mapping (tartib muhim: ko'p harfli almashtirishlar birinchi)
_CYRILLIC_TO_LATIN_MAP = [
    # Digraph / trigraph — bosh harflar (katta → katta bilan boshlanadi)
    ("Ўз", "Oʻz"),  # common word-start
    # Ikki harfli almashinuvlar
    ("Ё", "Yo"),
    ("ё", "yo"),
    ("Ю", "Yu"),
    ("ю", "yu"),
    ("Я", "Ya"),
    ("я", "ya"),
    ("Ч", "Ch"),
    ("ч", "ch"),
    ("Ш", "Sh"),
    ("ш", "sh"),
    ("Щ", "Shch"),
    ("щ", "shch"),
    ("Ц", "Ts"),
    ("ц", "ts"),
    # O'zbekchaga xos harflar
    ("Ғ", "Gʻ"),
    ("ғ", "gʻ"),
    ("Қ", "Q"),
    ("қ", "q"),
    ("Ҳ", "H"),
    ("ҳ", "h"),
    ("Ў", "Oʻ"),
    ("ў", "oʻ"),
    ("Ъ", "ʼ"),
    ("ъ", "ʼ"),
    # Oddiy bir-biriga mos harflar
    ("А", "A"),
    ("а", "a"),
    ("Б", "B"),
    ("б", "b"),
    ("В", "V"),
    ("в", "v"),
    ("Г", "G"),
    ("г", "g"),
    ("Д", "D"),
    ("д", "d"),
    ("Е", "E"),
    ("е", "e"),
    ("Ж", "J"),
    ("ж", "j"),
    ("З", "Z"),
    ("з", "z"),
    ("И", "I"),
    ("и", "i"),
    ("Й", "Y"),
    ("й", "y"),
    ("К", "K"),
    ("к", "k"),
    ("Л", "L"),
    ("л", "l"),
    ("М", "M"),
    ("м", "m"),
    ("Н", "N"),
    ("н", "n"),
    ("О", "O"),
    ("о", "o"),
    ("П", "P"),
    ("п", "p"),
    ("Р", "R"),
    ("р", "r"),
    ("С", "S"),
    ("с", "s"),
    ("Т", "T"),
    ("т", "t"),
    ("У", "U"),
    ("у", "u"),
    ("Ф", "F"),
    ("ф", "f"),
    ("Х", "X"),
    ("х", "x"),
    ("Э", "E"),
    ("э", "e"),
    # Yumshatish va qattiq belgisi — odatda tushiriladi yoki apostrof
    ("Ь", ""),
    ("ь", ""),
]


def cyrillic_to_latin(text: str) -> str:
    """Kirill yozuvidagi O'zbek matnni Lotin yozuviga o'giradi."""
    result = text
    for cyr, lat in _CYRILLIC_TO_LATIN_MAP:
        result = result.replace(cyr, lat)
    return result


if __name__ == "__main__":
    # Quick sanity test
    samples = [
        "Меҳрибон ва раҳмли Аллоҳнинг номи билан бошлайман.",
        "Фотиҳа сураси",
        "Ўзбекистон",
        "Қуръони Карим",
    ]
    for s in samples:
        print(f"Kirill:  {s}")
        print(f"Lotin:   {cyrillic_to_latin(s)}")
        print()
