# Noorain — brend to'plami

`Noorain` (نورين — *"ikki nur"*) ilovasining rasmiy identifikatsiya to'plami.
Barcha fayllar sof SVG: shriftlar konturga (outline) aylantirilgan, tashqi
bog'liqlik yo'q, har qanday o'lchamda toza chiqadi.

## Fayllar

| Fayl | O'lcham | Nima uchun |
|---|---|---|
| `noorain-emblem-light.svg` / `-dark.svg` | 512×512 | Faqat belgi. Avatar, favicon, splash, watermark |
| `noorain-wordmark-light.svg` / `-dark.svg` | 1076×199 | Faqat nom. Belgi allaqachon ko'ringan joyda |
| `noorain-icon-light.svg` / `-dark.svg` | 1024×1024 | Ilova ikonkasi (fon + squircle mask bilan) |
| `noorain-lockup-horizontal-light.svg` / `-dark.svg` | 734×224 | Asosiy lockup. Header, nav bar, hujjat sarlavhasi |
| `noorain-lockup-vertical-light.svg` / `-dark.svg` | 683×568 | Markazlashgan lockup. Splash, onboarding, poster |

**light** = och (parchment) fon uchun · **dark** = to'q (night) fon uchun.

## Belgi ma'nosi

- **Mehrob ravog'i** — Qur'on va namoz makoni; ilovaning "muqaddas oyna" ramzi.
- **Ikki yarim oy** — *noorain*, ikki nur: Qur'on va Sunnat. Ular markazga qaragan,
  nurni quchoqlab turadi.
- **Sakkiz burchakli yulduz** (xotam) — an'anaviy islomiy geometriya, muhr va nur nuqtasi.
- **Oltin chiziq** — ostona; har kuni qaytib kelinadigan joy.
- **Wordmark'dagi "OO"** — nomdagi ikki nur. Shu sababli faqat shu ikki harf oltin.

## Palitra

Loyihaning `NurColors` (Theme.kt) tizimi bilan bir xil.

| Rol | Light | Dark |
|---|---|---|
| Struktura (ravoq + yulduz) | Ink `#16332E` | Parchment `#F2E8D2` |
| Oltin (yorug') | `#D8C077` | `#F0DEA0` |
| Oltin (soya) | `#B8912F` | `#C9A342` |
| Fon | `#F6EFDE` (icon: `#FFFCF6`→`#EDDFBE`) | `#0D211E` (icon: `#143A33`→`#0A1B19`) |
| Wordmark matni | `#16332E` | `#F6EFDE` |

## Tipografika

**Lato Semibold**, katta harflar, tracking +90/1000 em. Wordmark konturga aylantirilgan,
shu sababli shriftni o'rnatish shart emas. Ilova ichidagi sarlavhalar uchun ham Lato
(yoki loyihadagi mavjud `Type.kt` oilasi) ishlatilsin.

Arabcha `نورين` — Naskh, faqat vertikal lockup'da, oltin rangda, ikkinchi darajali element.

## Qoidalar

**Bo'sh joy (clear space).** Har tomondan kamida belgi balandligining 25% bo'sh
qolsin. Lockup uchun — wordmark cap-height'ining 100%.

**Minimal o'lcham.** Belgi 24 px (bosmada 8 mm). Gorizontal lockup 120 px kenglik.
Undan kichikroq joyda faqat belgi ishlatilsin, lockup emas.

**Fon.** Och fonda `-light`, to'q fonda `-dark`. Rasm ustida ishlatilsa —
oldin 40–60% qoraytiruvchi qatlam qo'ying, keyin `-dark` variantni joylashtiring.

**Qilinmaydi.** Ranglarni almashtirish · cho'zish yoki qiyshaytirish · soya/kontur
qo'shish · elementlarni qayta joylashtirish · ravoqni to'ldirib bo'yash ·
belgi va wordmark orasidagi masofani o'zgartirish.

## PNG eksport

```bash
for s in 48 72 96 144 192 512 1024; do google-chrome --headless --disable-gpu --screenshot=icon-$s.png --window-size=$s,$s --default-background-color=00000000 noorain-icon-dark.svg; done
```

Android adaptive icon uchun: fonni alohida `<rect>` sifatida `ic_launcher_background`ga,
emblemni `ic_launcher_foreground`ga ajrating (foreground 108dp canvas ichida 66dp safe zone).
