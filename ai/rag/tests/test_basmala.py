from app.helpers import strip_basmala

BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"


def test_strips_basmala_prefix_from_first_ayah():
    assert strip_basmala(2, 1, f"{BASMALA} الٓمٓ") == "الٓمٓ"


def test_strips_shadda_variant():
    assert strip_basmala(97, 1, "بِّسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ إِنَّآ") == "إِنَّآ"


def test_keeps_fatiha_and_other_ayahs():
    assert strip_basmala(1, 1, "﻿" + BASMALA) == BASMALA
    assert strip_basmala(2, 2, f"{BASMALA} x") == f"{BASMALA} x"
    assert strip_basmala(9, 1, "بَرَآءَةٌۭ") == "بَرَآءَةٌۭ"
