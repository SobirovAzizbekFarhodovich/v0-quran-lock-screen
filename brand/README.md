# Noorain — logo va brend fayllari

`Noorain` (نورين — *"ikki nur"*). Barcha fayllar `generate.py` dan bitta geometriya
asosida chiqariladi: SVG master fayllar shu papkada, Android ikonkalari esa
`app/src/main/res/` ichida. Logoni o'zgartirish kerak bo'lsa, generatorni tahrirlang va
`python3 brand/generate.py` ni ishga tushiring (fontTools kerak).

## Belgi

Ikki kvadrat bir-biriga to'qilgan (biri ustidan, biri ostidan o'tadi) va birgalikda
**Rub al-Hizb** (۞) yulduzini hosil qiladi. Bu belgi Mushafda hizb boshini bildiradi.

- **Ikki kvadrat** — *noorain*, ikki nur: Qur'on va Sunnat.
- **To'qilish** — ikkalasi bir-biriga bog'langan, biri ikkinchisisiz to'liq emas.
- **Markazdagi nur** — sakkiz qirrali kichik yulduz va undan taraladigan yorug'lik.

## Fayllar

| Fayl | Nima uchun |
|---|---|
| `noorain-mark-light.svg` / `-dark.svg` | Faqat belgi; och (qog'oz) va to'q (yashil) fon uchun |
| `noorain-wordmark-light.svg` / `-dark.svg` | "Noorain" so'zi, PT Serif Bold, konturga aylantirilgan |
| `noorain-lockup-light.svg` / `-dark.svg` | Belgi va so'z yonma-yon |
| `noorain-icon.svg` | 1024×1024 ilova ikonkasi (Play Store uchun eksport qilinadi) |

Android: `mipmap-anydpi-v26/ic_launcher*.xml` (adaptive icon: yashil fon + oltin belgi +
Android 13 uchun monoxrom variant), `drawable/splash_mark.xml` (Android 12+ splash),
`drawable/noorain_mark*.xml` (ilova ichida), `drawable/ic_quran_notification.xml`.

## Palitra

| Rol | Rang |
|---|---|
| To'q yashil (tasma, asosiy) | `#0E3B2E`, chuqur `#082A20` |
| Antiqa oltin | `#C9A646`, och `#E6CC7E`, matn uchun `#8E6F1C` |
| Qog'oz | `#F6F2E8`, karta `#FFFDF8` |

## Shriftlar

PT Serif (sarlavhalar, wordmark), Golos Text (interfeys), Amiri Quran (Qur'on matni).
Hammasi SIL Open Font License; fayllar `app/src/main/res/font/` da.

## Qoidalar

- Atrofida kamida belgi balandligining 25% bo'sh joy qoldiring.
- Eng kichik o'lcham: belgi 24 px, lockup 120 px kenglik.
- Ranglarni almashtirmang, cho'zmang, soya yoki kontur qo'shmang, kvadratlarning
  to'qilish tartibini o'zgartirmang.
- Rasm ustida faqat `-dark` variant, oldidan 40–60% qoraytiruvchi qatlam bilan.
