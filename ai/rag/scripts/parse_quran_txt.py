#!/usr/bin/env python3
"""
Qur'on TXT fayllarini (Tanzil.net formati: surah|ayah|text) structured JSONL ga parse qiladi.

Foydalanish:
    python parse_quran_txt.py \\
        --input ~/Downloads/uz.sodik\ \(3\).txt \\
        --lang uz_cyrillic \\
        --source "Muhammad Sodiq Muhammad Yusuf" \\
        --out ../data/quran/uz_cyrillic.jsonl

    # Kirill → Lotin konvertatsiya bilan:
    python parse_quran_txt.py \\
        --input ~/Downloads/uz.sodik\ \(3\).txt \\
        --lang uz_latin \\
        --source "Muhammad Sodiq Muhammad Yusuf" \\
        --out ../data/quran/uz_latin.jsonl \\
        --convert-to-latin
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Skript papkasidagi cyrillic_to_latin modulni import qilish
sys.path.insert(0, str(Path(__file__).parent))
from cyrillic_to_latin import cyrillic_to_latin


# 114 suraning metadata'si — nomlari va asosiy ma'lumotlari
SURAH_METADATA = [
    {"number": 1, "name_ar": "الفاتحة", "name_en": "Al-Fatihah", "name_uz_cyr": "Фотиҳа", "name_uz_lat": "Fotiha", "ayah_count": 7, "revelation": "meccan"},
    {"number": 2, "name_ar": "البقرة", "name_en": "Al-Baqarah", "name_uz_cyr": "Бақара", "name_uz_lat": "Baqara", "ayah_count": 286, "revelation": "medinan"},
    {"number": 3, "name_ar": "آل عمران", "name_en": "Ali 'Imran", "name_uz_cyr": "Оли Имрон", "name_uz_lat": "Oli Imron", "ayah_count": 200, "revelation": "medinan"},
    {"number": 4, "name_ar": "النساء", "name_en": "An-Nisa", "name_uz_cyr": "Нисо", "name_uz_lat": "Niso", "ayah_count": 176, "revelation": "medinan"},
    {"number": 5, "name_ar": "المائدة", "name_en": "Al-Ma'idah", "name_uz_cyr": "Моида", "name_uz_lat": "Moida", "ayah_count": 120, "revelation": "medinan"},
    {"number": 6, "name_ar": "الأنعام", "name_en": "Al-An'am", "name_uz_cyr": "Анъом", "name_uz_lat": "Anʼom", "ayah_count": 165, "revelation": "meccan"},
    {"number": 7, "name_ar": "الأعراف", "name_en": "Al-A'raf", "name_uz_cyr": "Аъроф", "name_uz_lat": "Aʼrof", "ayah_count": 206, "revelation": "meccan"},
    {"number": 8, "name_ar": "الأنفال", "name_en": "Al-Anfal", "name_uz_cyr": "Анфол", "name_uz_lat": "Anfol", "ayah_count": 75, "revelation": "medinan"},
    {"number": 9, "name_ar": "التوبة", "name_en": "At-Tawbah", "name_uz_cyr": "Тавба", "name_uz_lat": "Tavba", "ayah_count": 129, "revelation": "medinan"},
    {"number": 10, "name_ar": "يونس", "name_en": "Yunus", "name_uz_cyr": "Юнус", "name_uz_lat": "Yunus", "ayah_count": 109, "revelation": "meccan"},
    {"number": 11, "name_ar": "هود", "name_en": "Hud", "name_uz_cyr": "Ҳуд", "name_uz_lat": "Hud", "ayah_count": 123, "revelation": "meccan"},
    {"number": 12, "name_ar": "يوسف", "name_en": "Yusuf", "name_uz_cyr": "Юсуф", "name_uz_lat": "Yusuf", "ayah_count": 111, "revelation": "meccan"},
    {"number": 13, "name_ar": "الرعد", "name_en": "Ar-Ra'd", "name_uz_cyr": "Раъд", "name_uz_lat": "Raʼd", "ayah_count": 43, "revelation": "medinan"},
    {"number": 14, "name_ar": "إبراهيم", "name_en": "Ibrahim", "name_uz_cyr": "Иброҳим", "name_uz_lat": "Ibrohim", "ayah_count": 52, "revelation": "meccan"},
    {"number": 15, "name_ar": "الحجر", "name_en": "Al-Hijr", "name_uz_cyr": "Ҳижр", "name_uz_lat": "Hijr", "ayah_count": 99, "revelation": "meccan"},
    {"number": 16, "name_ar": "النحل", "name_en": "An-Nahl", "name_uz_cyr": "Наҳл", "name_uz_lat": "Nahl", "ayah_count": 128, "revelation": "meccan"},
    {"number": 17, "name_ar": "الإسراء", "name_en": "Al-Isra", "name_uz_cyr": "Исро", "name_uz_lat": "Isro", "ayah_count": 111, "revelation": "meccan"},
    {"number": 18, "name_ar": "الكهف", "name_en": "Al-Kahf", "name_uz_cyr": "Каҳф", "name_uz_lat": "Kahf", "ayah_count": 110, "revelation": "meccan"},
    {"number": 19, "name_ar": "مريم", "name_en": "Maryam", "name_uz_cyr": "Марям", "name_uz_lat": "Maryam", "ayah_count": 98, "revelation": "meccan"},
    {"number": 20, "name_ar": "طه", "name_en": "Taha", "name_uz_cyr": "Тоҳа", "name_uz_lat": "Toha", "ayah_count": 135, "revelation": "meccan"},
    {"number": 21, "name_ar": "الأنبياء", "name_en": "Al-Anbiya", "name_uz_cyr": "Анбиё", "name_uz_lat": "Anbiyo", "ayah_count": 112, "revelation": "meccan"},
    {"number": 22, "name_ar": "الحج", "name_en": "Al-Hajj", "name_uz_cyr": "Ҳаж", "name_uz_lat": "Haj", "ayah_count": 78, "revelation": "medinan"},
    {"number": 23, "name_ar": "المؤمنون", "name_en": "Al-Mu'minun", "name_uz_cyr": "Мўъминун", "name_uz_lat": "Moʻminun", "ayah_count": 118, "revelation": "meccan"},
    {"number": 24, "name_ar": "النور", "name_en": "An-Nur", "name_uz_cyr": "Нур", "name_uz_lat": "Nur", "ayah_count": 64, "revelation": "medinan"},
    {"number": 25, "name_ar": "الفرقان", "name_en": "Al-Furqan", "name_uz_cyr": "Фурқон", "name_uz_lat": "Furqon", "ayah_count": 77, "revelation": "meccan"},
    {"number": 26, "name_ar": "الشعراء", "name_en": "Ash-Shu'ara", "name_uz_cyr": "Шуаро", "name_uz_lat": "Shuaro", "ayah_count": 227, "revelation": "meccan"},
    {"number": 27, "name_ar": "النمل", "name_en": "An-Naml", "name_uz_cyr": "Намл", "name_uz_lat": "Naml", "ayah_count": 93, "revelation": "meccan"},
    {"number": 28, "name_ar": "القصص", "name_en": "Al-Qasas", "name_uz_cyr": "Қасас", "name_uz_lat": "Qasas", "ayah_count": 88, "revelation": "meccan"},
    {"number": 29, "name_ar": "العنكبوت", "name_en": "Al-Ankabut", "name_uz_cyr": "Анкабут", "name_uz_lat": "Ankabut", "ayah_count": 69, "revelation": "meccan"},
    {"number": 30, "name_ar": "الروم", "name_en": "Ar-Rum", "name_uz_cyr": "Рум", "name_uz_lat": "Rum", "ayah_count": 60, "revelation": "meccan"},
    {"number": 31, "name_ar": "لقمان", "name_en": "Luqman", "name_uz_cyr": "Луқмон", "name_uz_lat": "Luqmon", "ayah_count": 34, "revelation": "meccan"},
    {"number": 32, "name_ar": "السجدة", "name_en": "As-Sajdah", "name_uz_cyr": "Сажда", "name_uz_lat": "Sajda", "ayah_count": 30, "revelation": "meccan"},
    {"number": 33, "name_ar": "الأحزاب", "name_en": "Al-Ahzab", "name_uz_cyr": "Аҳзоб", "name_uz_lat": "Ahzob", "ayah_count": 73, "revelation": "medinan"},
    {"number": 34, "name_ar": "سبأ", "name_en": "Saba", "name_uz_cyr": "Сабаъ", "name_uz_lat": "Sabaʼ", "ayah_count": 54, "revelation": "meccan"},
    {"number": 35, "name_ar": "فاطر", "name_en": "Fatir", "name_uz_cyr": "Фотир", "name_uz_lat": "Fotir", "ayah_count": 45, "revelation": "meccan"},
    {"number": 36, "name_ar": "يس", "name_en": "Ya-Sin", "name_uz_cyr": "Ёсин", "name_uz_lat": "Yosin", "ayah_count": 83, "revelation": "meccan"},
    {"number": 37, "name_ar": "الصافات", "name_en": "As-Saffat", "name_uz_cyr": "Соффот", "name_uz_lat": "Soffot", "ayah_count": 182, "revelation": "meccan"},
    {"number": 38, "name_ar": "ص", "name_en": "Sad", "name_uz_cyr": "Сод", "name_uz_lat": "Sod", "ayah_count": 88, "revelation": "meccan"},
    {"number": 39, "name_ar": "الزمر", "name_en": "Az-Zumar", "name_uz_cyr": "Зумар", "name_uz_lat": "Zumar", "ayah_count": 75, "revelation": "meccan"},
    {"number": 40, "name_ar": "غافر", "name_en": "Ghafir", "name_uz_cyr": "Ғофир", "name_uz_lat": "Gʻofir", "ayah_count": 85, "revelation": "meccan"},
    {"number": 41, "name_ar": "فصلت", "name_en": "Fussilat", "name_uz_cyr": "Фуссилат", "name_uz_lat": "Fussilat", "ayah_count": 54, "revelation": "meccan"},
    {"number": 42, "name_ar": "الشورى", "name_en": "Ash-Shura", "name_uz_cyr": "Шўро", "name_uz_lat": "Shoʻro", "ayah_count": 53, "revelation": "meccan"},
    {"number": 43, "name_ar": "الزخرف", "name_en": "Az-Zukhruf", "name_uz_cyr": "Зухруф", "name_uz_lat": "Zuxruf", "ayah_count": 89, "revelation": "meccan"},
    {"number": 44, "name_ar": "الدخان", "name_en": "Ad-Dukhan", "name_uz_cyr": "Духон", "name_uz_lat": "Duxon", "ayah_count": 59, "revelation": "meccan"},
    {"number": 45, "name_ar": "الجاثية", "name_en": "Al-Jathiyah", "name_uz_cyr": "Жосия", "name_uz_lat": "Josiya", "ayah_count": 37, "revelation": "meccan"},
    {"number": 46, "name_ar": "الأحقاف", "name_en": "Al-Ahqaf", "name_uz_cyr": "Аҳқоф", "name_uz_lat": "Ahqof", "ayah_count": 35, "revelation": "meccan"},
    {"number": 47, "name_ar": "محمد", "name_en": "Muhammad", "name_uz_cyr": "Муҳаммад", "name_uz_lat": "Muhammad", "ayah_count": 38, "revelation": "medinan"},
    {"number": 48, "name_ar": "الفتح", "name_en": "Al-Fath", "name_uz_cyr": "Фатҳ", "name_uz_lat": "Fath", "ayah_count": 29, "revelation": "medinan"},
    {"number": 49, "name_ar": "الحجرات", "name_en": "Al-Hujurat", "name_uz_cyr": "Ҳужурот", "name_uz_lat": "Hujurot", "ayah_count": 18, "revelation": "medinan"},
    {"number": 50, "name_ar": "ق", "name_en": "Qaf", "name_uz_cyr": "Қоф", "name_uz_lat": "Qof", "ayah_count": 45, "revelation": "meccan"},
    {"number": 51, "name_ar": "الذاريات", "name_en": "Adh-Dhariyat", "name_uz_cyr": "Зориёт", "name_uz_lat": "Zoriyot", "ayah_count": 60, "revelation": "meccan"},
    {"number": 52, "name_ar": "الطور", "name_en": "At-Tur", "name_uz_cyr": "Тур", "name_uz_lat": "Tur", "ayah_count": 49, "revelation": "meccan"},
    {"number": 53, "name_ar": "النجم", "name_en": "An-Najm", "name_uz_cyr": "Нажм", "name_uz_lat": "Najm", "ayah_count": 62, "revelation": "meccan"},
    {"number": 54, "name_ar": "القمر", "name_en": "Al-Qamar", "name_uz_cyr": "Қамар", "name_uz_lat": "Qamar", "ayah_count": 55, "revelation": "meccan"},
    {"number": 55, "name_ar": "الرحمن", "name_en": "Ar-Rahman", "name_uz_cyr": "Раҳмон", "name_uz_lat": "Rahmon", "ayah_count": 78, "revelation": "medinan"},
    {"number": 56, "name_ar": "الواقعة", "name_en": "Al-Waqi'ah", "name_uz_cyr": "Воқеа", "name_uz_lat": "Voqea", "ayah_count": 96, "revelation": "meccan"},
    {"number": 57, "name_ar": "الحديد", "name_en": "Al-Hadid", "name_uz_cyr": "Ҳадид", "name_uz_lat": "Hadid", "ayah_count": 29, "revelation": "medinan"},
    {"number": 58, "name_ar": "المجادلة", "name_en": "Al-Mujadila", "name_uz_cyr": "Мужодала", "name_uz_lat": "Mujodala", "ayah_count": 22, "revelation": "medinan"},
    {"number": 59, "name_ar": "الحشر", "name_en": "Al-Hashr", "name_uz_cyr": "Ҳашр", "name_uz_lat": "Hashr", "ayah_count": 24, "revelation": "medinan"},
    {"number": 60, "name_ar": "الممتحنة", "name_en": "Al-Mumtahanah", "name_uz_cyr": "Мумтаҳана", "name_uz_lat": "Mumtahana", "ayah_count": 13, "revelation": "medinan"},
    {"number": 61, "name_ar": "الصف", "name_en": "As-Saf", "name_uz_cyr": "Саф", "name_uz_lat": "Saf", "ayah_count": 14, "revelation": "medinan"},
    {"number": 62, "name_ar": "الجمعة", "name_en": "Al-Jumu'ah", "name_uz_cyr": "Жумъа", "name_uz_lat": "Jumʼa", "ayah_count": 11, "revelation": "medinan"},
    {"number": 63, "name_ar": "المنافقون", "name_en": "Al-Munafiqun", "name_uz_cyr": "Мунофиқун", "name_uz_lat": "Munofiqun", "ayah_count": 11, "revelation": "medinan"},
    {"number": 64, "name_ar": "التغابن", "name_en": "At-Taghabun", "name_uz_cyr": "Тағобун", "name_uz_lat": "Tagʻobun", "ayah_count": 18, "revelation": "medinan"},
    {"number": 65, "name_ar": "الطلاق", "name_en": "At-Talaq", "name_uz_cyr": "Талоқ", "name_uz_lat": "Taloq", "ayah_count": 12, "revelation": "medinan"},
    {"number": 66, "name_ar": "التحريم", "name_en": "At-Tahrim", "name_uz_cyr": "Таҳрим", "name_uz_lat": "Tahrim", "ayah_count": 12, "revelation": "medinan"},
    {"number": 67, "name_ar": "الملك", "name_en": "Al-Mulk", "name_uz_cyr": "Мулк", "name_uz_lat": "Mulk", "ayah_count": 30, "revelation": "meccan"},
    {"number": 68, "name_ar": "القلم", "name_en": "Al-Qalam", "name_uz_cyr": "Қалам", "name_uz_lat": "Qalam", "ayah_count": 52, "revelation": "meccan"},
    {"number": 69, "name_ar": "الحاقة", "name_en": "Al-Haqqah", "name_uz_cyr": "Ҳоққа", "name_uz_lat": "Hoqqa", "ayah_count": 52, "revelation": "meccan"},
    {"number": 70, "name_ar": "المعارج", "name_en": "Al-Ma'arij", "name_uz_cyr": "Маъориж", "name_uz_lat": "Maʼorij", "ayah_count": 44, "revelation": "meccan"},
    {"number": 71, "name_ar": "نوح", "name_en": "Nuh", "name_uz_cyr": "Нуҳ", "name_uz_lat": "Nuh", "ayah_count": 28, "revelation": "meccan"},
    {"number": 72, "name_ar": "الجن", "name_en": "Al-Jinn", "name_uz_cyr": "Жин", "name_uz_lat": "Jin", "ayah_count": 28, "revelation": "meccan"},
    {"number": 73, "name_ar": "المزمل", "name_en": "Al-Muzzammil", "name_uz_cyr": "Муззаммил", "name_uz_lat": "Muzzammil", "ayah_count": 20, "revelation": "meccan"},
    {"number": 74, "name_ar": "المدثر", "name_en": "Al-Muddaththir", "name_uz_cyr": "Муддассир", "name_uz_lat": "Muddassir", "ayah_count": 56, "revelation": "meccan"},
    {"number": 75, "name_ar": "القيامة", "name_en": "Al-Qiyamah", "name_uz_cyr": "Қиёма", "name_uz_lat": "Qiyoma", "ayah_count": 40, "revelation": "meccan"},
    {"number": 76, "name_ar": "الإنسان", "name_en": "Al-Insan", "name_uz_cyr": "Инсон", "name_uz_lat": "Inson", "ayah_count": 31, "revelation": "medinan"},
    {"number": 77, "name_ar": "المرسلات", "name_en": "Al-Mursalat", "name_uz_cyr": "Мурсалот", "name_uz_lat": "Mursalot", "ayah_count": 50, "revelation": "meccan"},
    {"number": 78, "name_ar": "النبأ", "name_en": "An-Naba", "name_uz_cyr": "Набаъ", "name_uz_lat": "Nabaʼ", "ayah_count": 40, "revelation": "meccan"},
    {"number": 79, "name_ar": "النازعات", "name_en": "An-Nazi'at", "name_uz_cyr": "Нозиот", "name_uz_lat": "Noziot", "ayah_count": 46, "revelation": "meccan"},
    {"number": 80, "name_ar": "عبس", "name_en": "Abasa", "name_uz_cyr": "Абаса", "name_uz_lat": "Abasa", "ayah_count": 42, "revelation": "meccan"},
    {"number": 81, "name_ar": "التكوير", "name_en": "At-Takwir", "name_uz_cyr": "Таквир", "name_uz_lat": "Takvir", "ayah_count": 29, "revelation": "meccan"},
    {"number": 82, "name_ar": "الانفطار", "name_en": "Al-Infitar", "name_uz_cyr": "Инфитор", "name_uz_lat": "Infitor", "ayah_count": 19, "revelation": "meccan"},
    {"number": 83, "name_ar": "المطففين", "name_en": "Al-Mutaffifin", "name_uz_cyr": "Мутаффифин", "name_uz_lat": "Mutaffifin", "ayah_count": 36, "revelation": "meccan"},
    {"number": 84, "name_ar": "الانشقاق", "name_en": "Al-Inshiqaq", "name_uz_cyr": "Иншиқоқ", "name_uz_lat": "Inshiqoq", "ayah_count": 25, "revelation": "meccan"},
    {"number": 85, "name_ar": "البروج", "name_en": "Al-Buruj", "name_uz_cyr": "Буруж", "name_uz_lat": "Buruj", "ayah_count": 22, "revelation": "meccan"},
    {"number": 86, "name_ar": "الطارق", "name_en": "At-Tariq", "name_uz_cyr": "Ториқ", "name_uz_lat": "Toriq", "ayah_count": 17, "revelation": "meccan"},
    {"number": 87, "name_ar": "الأعلى", "name_en": "Al-A'la", "name_uz_cyr": "Аъло", "name_uz_lat": "Aʼlo", "ayah_count": 19, "revelation": "meccan"},
    {"number": 88, "name_ar": "الغاشية", "name_en": "Al-Ghashiyah", "name_uz_cyr": "Ғошия", "name_uz_lat": "Gʻoshiya", "ayah_count": 26, "revelation": "meccan"},
    {"number": 89, "name_ar": "الفجر", "name_en": "Al-Fajr", "name_uz_cyr": "Фажр", "name_uz_lat": "Fajr", "ayah_count": 30, "revelation": "meccan"},
    {"number": 90, "name_ar": "البلد", "name_en": "Al-Balad", "name_uz_cyr": "Балад", "name_uz_lat": "Balad", "ayah_count": 20, "revelation": "meccan"},
    {"number": 91, "name_ar": "الشمس", "name_en": "Ash-Shams", "name_uz_cyr": "Шамс", "name_uz_lat": "Shams", "ayah_count": 15, "revelation": "meccan"},
    {"number": 92, "name_ar": "الليل", "name_en": "Al-Layl", "name_uz_cyr": "Лайл", "name_uz_lat": "Layl", "ayah_count": 21, "revelation": "meccan"},
    {"number": 93, "name_ar": "الضحى", "name_en": "Ad-Duhaa", "name_uz_cyr": "Зуҳо", "name_uz_lat": "Zuho", "ayah_count": 11, "revelation": "meccan"},
    {"number": 94, "name_ar": "الشرح", "name_en": "Ash-Sharh", "name_uz_cyr": "Шарҳ", "name_uz_lat": "Sharh", "ayah_count": 8, "revelation": "meccan"},
    {"number": 95, "name_ar": "التين", "name_en": "At-Tin", "name_uz_cyr": "Тин", "name_uz_lat": "Tin", "ayah_count": 8, "revelation": "meccan"},
    {"number": 96, "name_ar": "العلق", "name_en": "Al-Alaq", "name_uz_cyr": "Алақ", "name_uz_lat": "Alaq", "ayah_count": 19, "revelation": "meccan"},
    {"number": 97, "name_ar": "القدر", "name_en": "Al-Qadr", "name_uz_cyr": "Қадр", "name_uz_lat": "Qadr", "ayah_count": 5, "revelation": "meccan"},
    {"number": 98, "name_ar": "البينة", "name_en": "Al-Bayyinah", "name_uz_cyr": "Баййина", "name_uz_lat": "Bayyina", "ayah_count": 8, "revelation": "medinan"},
    {"number": 99, "name_ar": "الزلزلة", "name_en": "Az-Zalzalah", "name_uz_cyr": "Залзала", "name_uz_lat": "Zalzala", "ayah_count": 8, "revelation": "medinan"},
    {"number": 100, "name_ar": "العاديات", "name_en": "Al-Adiyat", "name_uz_cyr": "Одиёт", "name_uz_lat": "Odiyot", "ayah_count": 11, "revelation": "meccan"},
    {"number": 101, "name_ar": "القارعة", "name_en": "Al-Qari'ah", "name_uz_cyr": "Қориа", "name_uz_lat": "Qoria", "ayah_count": 11, "revelation": "meccan"},
    {"number": 102, "name_ar": "التكاثر", "name_en": "At-Takathur", "name_uz_cyr": "Такосур", "name_uz_lat": "Takosur", "ayah_count": 8, "revelation": "meccan"},
    {"number": 103, "name_ar": "العصر", "name_en": "Al-Asr", "name_uz_cyr": "Аср", "name_uz_lat": "Asr", "ayah_count": 3, "revelation": "meccan"},
    {"number": 104, "name_ar": "الهمزة", "name_en": "Al-Humazah", "name_uz_cyr": "Ҳумаза", "name_uz_lat": "Humaza", "ayah_count": 9, "revelation": "meccan"},
    {"number": 105, "name_ar": "الفيل", "name_en": "Al-Fil", "name_uz_cyr": "Фил", "name_uz_lat": "Fil", "ayah_count": 5, "revelation": "meccan"},
    {"number": 106, "name_ar": "قريش", "name_en": "Quraysh", "name_uz_cyr": "Қурайш", "name_uz_lat": "Quraysh", "ayah_count": 4, "revelation": "meccan"},
    {"number": 107, "name_ar": "الماعون", "name_en": "Al-Ma'un", "name_uz_cyr": "Моун", "name_uz_lat": "Moun", "ayah_count": 7, "revelation": "meccan"},
    {"number": 108, "name_ar": "الكوثر", "name_en": "Al-Kawthar", "name_uz_cyr": "Кавсар", "name_uz_lat": "Kavsar", "ayah_count": 3, "revelation": "meccan"},
    {"number": 109, "name_ar": "الكافرون", "name_en": "Al-Kafirun", "name_uz_cyr": "Кофирун", "name_uz_lat": "Kofirun", "ayah_count": 6, "revelation": "meccan"},
    {"number": 110, "name_ar": "النصر", "name_en": "An-Nasr", "name_uz_cyr": "Наср", "name_uz_lat": "Nasr", "ayah_count": 3, "revelation": "medinan"},
    {"number": 111, "name_ar": "المسد", "name_en": "Al-Masad", "name_uz_cyr": "Масад", "name_uz_lat": "Masad", "ayah_count": 5, "revelation": "meccan"},
    {"number": 112, "name_ar": "الإخلاص", "name_en": "Al-Ikhlas", "name_uz_cyr": "Ихлос", "name_uz_lat": "Ixlos", "ayah_count": 4, "revelation": "meccan"},
    {"number": 113, "name_ar": "الفلق", "name_en": "Al-Falaq", "name_uz_cyr": "Фалақ", "name_uz_lat": "Falaq", "ayah_count": 5, "revelation": "meccan"},
    {"number": 114, "name_ar": "الناس", "name_en": "An-Nas", "name_uz_cyr": "Нос", "name_uz_lat": "Nos", "ayah_count": 6, "revelation": "meccan"},
]


def parse_tanzil_file(
    filepath: Path,
    lang: str,
    source: str,
    convert_to_latin: bool = False,
) -> list[dict]:
    """Tanzil.net formatdagi TXT faylni parse qiladi.

    Format: surah_number|ayah_number|translation_text
    '#' bilan boshlanuvchi satrlar — metadata, o'tkaziladi.
    Bo'sh satrlar o'tkaziladi.
    """
    entries = []
    text = filepath.read_text(encoding="utf-8")

    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        parts = line.split("|", 2)
        if len(parts) != 3:
            continue

        try:
            surah = int(parts[0])
            ayah = int(parts[1])
        except ValueError:
            continue

        verse_text = parts[2].strip()
        if convert_to_latin:
            verse_text = cyrillic_to_latin(verse_text)

        # Sura metadata topish
        surah_meta = next((s for s in SURAH_METADATA if s["number"] == surah), None)

        entry = {
            "surah": surah,
            "ayah": ayah,
            "text": verse_text,
            "lang": lang,
            "source": source,
            "type": "quran",
        }

        # Sura nomi qo'shish (AI retrieval uchun foydali)
        if surah_meta:
            entry["surah_name_ar"] = surah_meta["name_ar"]
            entry["surah_name_en"] = surah_meta["name_en"]
            if lang.startswith("uz"):
                entry["surah_name"] = (
                    surah_meta["name_uz_lat"] if "latin" in lang else surah_meta["name_uz_cyr"]
                )
            elif lang == "ru":
                entry["surah_name"] = surah_meta["name_en"]  # ru uchun en nomi
            else:
                entry["surah_name"] = surah_meta["name_en"]
            entry["revelation"] = surah_meta["revelation"]

        entries.append(entry)

    return entries


def write_jsonl(entries: list[dict], output: Path) -> None:
    """JSONL formatda yozadi."""
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as f:
        for entry in entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    print(f"✅ {len(entries)} ta oyat → {output}")


def write_surah_metadata(output: Path) -> None:
    """114 sura metadata ni JSON ga yozadi."""
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as f:
        json.dump(SURAH_METADATA, f, ensure_ascii=False, indent=2)
    print(f"✅ {len(SURAH_METADATA)} ta sura metadata → {output}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Qur'on TXT fayllarini JSONL formatga o'tkazadi"
    )
    parser.add_argument("--input", type=Path, required=True, help="Tanzil.net TXT fayl yo'li")
    parser.add_argument("--lang", type=str, required=True,
                        help="Til kodi: uz_cyrillic, uz_latin, en, ru")
    parser.add_argument("--source", type=str, required=True,
                        help="Tarjimachi/manba nomi")
    parser.add_argument("--out", type=Path, required=True,
                        help="Chiqish JSONL fayl yo'li")
    parser.add_argument("--convert-to-latin", action="store_true",
                        help="Kirill → Lotin konvertatsiya")
    parser.add_argument("--metadata-out", type=Path, default=None,
                        help="Sura metadata JSON chiqish yo'li")
    args = parser.parse_args()

    if not args.input.exists():
        print(f"❌ Fayl topilmadi: {args.input}")
        sys.exit(1)

    entries = parse_tanzil_file(
        args.input,
        lang=args.lang,
        source=args.source,
        convert_to_latin=args.convert_to_latin,
    )
    write_jsonl(entries, args.out)

    if args.metadata_out:
        write_surah_metadata(args.metadata_out)


if __name__ == "__main__":
    main()
