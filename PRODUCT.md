# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

A broad audience of Muslims in Uzbekistan, of every age including older people who are not confident with smartphones. They read in Uzbek (Latin or Cyrillic script), Russian or English. They come to the app in short daily moments rather than long sessions.

## Product Purpose

Noorain (نورين, "two lights") keeps the Quran present in everyday life. It puts an ayah with its translation on the phone's lock screen and refreshes it every hour, lets people read and search the Quran offline, reminds them to recite at set times, checks their recitation with AI, and answers spiritual questions from trusted sources. Success means people meet an ayah many times a day without effort and turn to the app when they need comfort or guidance.

## Positioning

A lock-screen-first Quran companion in the user's own language: the ayah reaches the user without opening the app. Behind it sits an Islamic AI assistant whose answers are grounded in a curated library of Quran, hadith and dua sources. A live recitation checker highlights words as they are spoken.

## Operating Context

- The most used features are the **AI assistant**, the **lock-screen wallpaper** and **recitation (tilovat) checking**.
- Sign-in with internet is required on first use; afterwards reading, search, bookmarks, wallpaper and reminders work fully offline.
- Uzbek Latin and Uzbek Cyrillic Quran content ships inside the APK (Uzbek Latin is the default); Russian and English are downloaded from the server on demand.
- AI chat and recitation need the server (currently plain HTTP at 3.73.130.154) and fail fast with a clear message when offline.
- Lock-screen wallpaper is rendered on the device and applied hourly via WorkManager. On Honor/Huawei in dark mode the system shows the home wallpaper on the lock screen; the app offers an opt-in to apply to both.

## Capabilities and Constraints

- Native Android app: Kotlin, Jetpack Compose, Material 3, Room, WorkManager. minSdk 26, targetSdk 34. Tested on an Honor ABR-NX1, Android 16.
- Four UI languages (uz_latin, uz_cyrillic, ru, en). All user-facing text goes through `ui/theme/Strings.kt`.
- Arabic Quran text (Uthmani) must render correctly and right-to-left.
- Package `com.quranreminder.app` and theme name `QuranReminderTheme` are legacy names and do not reflect the brand.
- Google sign-in, email verification and HTTPS/domain are not set up yet.

## Brand Commitments

- Name: **Noorain** (Arabic نورين, "two lights"). The previous identity kit in `brand/` is being replaced by a new logo at the owner's request (2026-10-10).
- Religious respect: Quran text is never decorated, cropped or shown carelessly; no imagery of living beings.
- Standing visual preference (owner, 2026-10-10): the category-standard Islamic app look (deep green, gold, geometric ornament), executed at full craft rather than reinvented. Craft bar: Muslim Pro, Quran.com, Tarteel, and the regional Hidoyat / Quranify apps.

## Evidence on Hand

- Quran translations (uz_latin, uz_cyrillic, ru, en) and hadith/dua collections in `ai/rag/data/`.
- Three bundled lock-screen backgrounds (`app/src/main/res/drawable-nodpi/bg_local_*.png`) plus two remote ones in `remote_backgrounds/`.
- No user testimonials, ratings, scholar endorsements or download numbers exist; none may be invented.

## Product Principles

1. The ayah comes first: every screen and the wallpaper exist to put the Quran in front of the user with dignity.
2. Works without internet once signed in; network features say plainly when they cannot work.
3. Simple enough for a grandparent: large targets, plain words, one obvious action per screen.
4. Trustworthy guidance: AI answers cite real sources and never pretend to be a scholar.
5. Speak the user's language and script everywhere, including errors and notifications.

## Accessibility & Inclusion

Older users are a core audience: readable type sizes with a user-adjustable font size, strong contrast in light and dark themes, touch targets of at least 48dp, and no information carried by color alone.
