---
version: 1
slug: "app-src-main-kotlin-com-quranreminder-app-ui"
primary_target: "app/src/main/kotlin/com/quranreminder/app/ui"
related_targets: []
---

# Noorain app UI (all screens)

Mode: Operate. Native Android, Jetpack Compose + Material 3, phone only.

Audience and job: Uzbek Muslims of every age; short daily visits to the hourly ayah, the AI assistant, the lock-screen wallpaper and recitation checking. One obvious action per screen, large targets, plain words.

Constraints: Arabic Quran text is never decorated or cropped; four UI languages through Strings.kt; offline-first; dark theme is first-class.

## Direction contract

THESIS: The category-standard Islamic companion (deep green, antique gold, geometric ornament), played straight at the craft of Muslim Pro, Quran.com and Tarteel. It refuses the half-finished version of that look: flat cream pages, mismatched card shells, default serif and emoji-level icons.

OWN-WORLD: A deep green field (#0E3B2E family) owns the top band of every primary screen; content sits on warm paper-white surfaces with antique-gold hairline frames. The only ornament is the eight-point star (Rub el Hizb), used as a watermark lattice, a medallion behind icons, and the logo. Arabic is set in Amiri Quran, interface in Golos Text, headings in PT Serif. Dark theme swaps the paper for green-black, keeping the gold.

STORY: The user meets today's ayah first, understands it is refreshed every hour and is on their lock screen, and reaches AI, Wallpaper, Tilovat or the Quran in one tap.

FIRST VIEWPORT: Home: the green band fills the top ~36%, with the Noorain logo and greeting at left and the star lattice watermark at right. The hourly-ayah card overlaps the band's lower edge: gold hairline frame, Arabic at 26sp right-aligned, translation, and the reference in gold. Below it, a 2x2 grid of feature tiles, each with a gold-ringed star medallion icon. A Material NavigationBar with a green pill indicator sits at the bottom. Signature interaction: the splash assembles the logo (two squares rotate into the star, the light at its centre brightens) once per cold start.

FORM: Category standard (canon), chosen by the owner over the rolled direction. Seed key ff669778.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
