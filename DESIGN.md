---
name: Noorain
description: A lock-screen-first Quran companion in deep mosque green, antique gold and warm paper, ornamented only by the eight-point star.
colors:
  mosque-green: "#0E3B2E"
  mosque-green-deep: "#082A20"
  mosque-green-bright: "#1C5A47"
  green-mist: "#DCEBE3"
  antique-gold: "#C9A646"
  gold-ink: "#8E6F1C"
  gold-light: "#E6CC7E"
  gold-pale: "#F3E6BF"
  paper: "#F6F2E8"
  paper-raised: "#FFFDF8"
  paper-sunk: "#EFE8D8"
  hairline: "#DCCFAE"
  hairline-soft: "#E8DFCB"
  ink: "#1B2620"
  ink-muted: "#55615A"
  on-band-muted: "#C9DCD2"
  terracotta: "#A8432F"
  terracotta-soft: "#F6DAD6"
  success: "#2E7D5B"
  success-soft: "#D6EEE2"
  night-green: "#07211A"
  night-surface: "#0C2C23"
  night-raised: "#123A2F"
  night-band: "#0B3227"
  night-band-deep: "#051A14"
  night-paper-text: "#F3EEDF"
  night-muted: "#B9CCC2"
  night-outline: "#2E5A4C"
  night-terracotta: "#E59A7A"
typography:
  display:
    fontFamily: "PT Serif, Georgia, serif"
    fontSize: "40sp"
    fontWeight: 700
    lineHeight: "46sp"
  headline:
    fontFamily: "PT Serif, Georgia, serif"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "34sp"
  headline-screen:
    fontFamily: "PT Serif, Georgia, serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "30sp"
  title-serif:
    fontFamily: "PT Serif, Georgia, serif"
    fontSize: "21sp"
    fontWeight: 700
    lineHeight: "27sp"
  title:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "17sp"
    fontWeight: 600
    lineHeight: "23sp"
  body:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "26sp"
  body-md:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "22sp"
  body-sm:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
  label:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "15sp"
    fontWeight: 600
    lineHeight: "20sp"
  label-sm:
    fontFamily: "Golos Text, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.2sp"
  quran:
    fontFamily: "Amiri Quran, serif"
    fontSize: "26sp"
    fontWeight: 400
    lineHeight: "52sp"
rounded:
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  ayah-inner: "18dp"
  lg: "22dp"
  xl: "28dp"
  full: "9999dp"
spacing:
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.mosque-green}"
    textColor: "{colors.paper-raised}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "0 24dp"
    height: "56dp"
  button-secondary:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.mosque-green}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: "52dp"
  icon-button:
    backgroundColor: "{colors.paper-sunk}"
    textColor: "{colors.mosque-green}"
    rounded: "{rounded.full}"
    size: "48dp"
  icon-button-on-band-emphasized:
    backgroundColor: "{colors.antique-gold}"
    textColor: "{colors.mosque-green-deep}"
    rounded: "{rounded.full}"
    size: "48dp"
  surface-card:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  ayah-card:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "20dp 22dp"
  list-row:
    textColor: "{colors.ink}"
    typography: "{typography.title}"
    padding: "10dp 16dp"
    height: "64dp"
  pill:
    backgroundColor: "{colors.gold-pale}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    padding: "6dp 12dp"
  nav-bar:
    backgroundColor: "{colors.paper-sunk}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    height: "64dp"
  nav-bar-selected:
    textColor: "{colors.mosque-green}"
---

# Design System: Noorain

## Overview

**Creative North Star: "The Illuminated Mushaf Page"**

Noorain plays the category-standard Islamic companion straight and at full craft: a deep mosque-green field crowns every primary screen, content rests on warm paper, and antique gold appears only as hairline frames, references and the star. It is the look people already trust from Muslim Pro, Quran.com and Tarteel, finished properly rather than reinvented. The only ornament in the whole system is the eight-point star (Rub el Hizb): as a faint watermark lattice on the green band, as the medallion behind every feature icon, as the four corner stars of the ayah card, and as the Noorain mark itself (two interlaced squares with a light at the centre).

The system is calm and generous for an audience that includes grandparents: large serif headings, 17sp body text, 48dp minimum targets, one obvious action per screen. Dark theme is first-class, not an inversion: paper becomes green-black, the band deepens, and gold stays gold (shifted to its light tone so it carries text).

Motion is physical and sparing. The floating liquid-glass tab bar moves on damped springs and can be grabbed; the cold-start intro tells the name: two lights dance, meet and draw the mark. Everything else is still. With system animations off (animator duration scale 0) every motion jumps to its end state.

**Key Characteristics:**
- Green band on top, paper below; the ayah card overlaps the band's lower edge on Home.
- Gold is a line and a reference colour, never a fill for large areas.
- The eight-point star is the single ornament; no other pattern, flourish or illustration.
- Arabic is set in Amiri Quran, right-to-left, never decorated or cropped.
- Floating clear-glass tab bar with a draggable refracting lens.
- Four UI languages (uz_latin, uz_cyrillic, ru, en); all copy comes from `ui/theme/Strings.kt`.

## Colors

A two-pole palette: deep mosque green for structure and action, antique gold for keylines and references, over warm paper (light) or green-black (dark).

### Primary
- **Mosque Green** (mosque-green): the band, primary buttons, selected nav tint, icon tints on paper. The voice of the app.
- **Deep Mosque Green** (mosque-green-deep): the lower stop of the band's vertical gradient; text on gold.
- **Bright Green** (mosque-green-bright): tertiary role in light theme.
- **Green Mist** (green-mist): fill of star medallions and primary containers.

### Secondary
- **Antique Gold** (antique-gold): ornament colour: the star lattice, medallion keylines, ayah-card corner stars, the emphasized on-band icon button. Frames use it at 55% alpha (light) and gold-light at 35% (dark).
- **Gold Ink** (gold-ink): gold dark enough for text on paper (4.5:1 or better). Ayah references, source citations in AI answers.
- **Gold Light** (gold-light): the dark theme's primary, ornament and gold text colour; selected tab tint in dark.
- **Gold Pale** (gold-pale): pill and secondary-container fill.

### Neutral
- **Paper** (paper): app background (light).
- **Raised Paper** (paper-raised): card and sheet surfaces; text on the band.
- **Sunk Paper** (paper-sunk): surface variant, resting icon-button fill, nav track fallback.
- **Hairline** (hairline) / **Soft Hairline** (hairline-soft): outline and in-card dividers.
- **Ink** (ink) / **Muted Ink** (ink-muted): text and secondary text.
- **On-band Muted** (on-band-muted): subtitles under headings on the green band.
- **Night Green / Night Surface / Night Raised** (night-green, night-surface, night-raised): dark background, surfaces and containers; **Night Band** pair for the dark band gradient.
- **Terracotta** (terracotta, night-terracotta): errors. Warm, so errors stay inside the green/gold world instead of Material's default pink.

### Named Rules
**The Gold Is a Line Rule.** Antique gold appears as 1dp keylines, small stars, the reference line and text; it never fills a card, band or button background (the single exception is the emphasized icon button on the band).

**The Gold Keeps Its Contrast Rule.** Gold text on paper is always gold-ink, never antique-gold; on dark surfaces it is gold-light.

**The One Field Rule.** Mosque green owns the top band of every primary screen and nothing else gets a full-bleed colour field.

## Typography

**Display Font:** PT Serif (Bold), headings and the wordmark
**Body Font:** Golos Text (variable; 400/500/600/700), all interface text; covers Latin, Cyrillic and Uzbek
**Scripture Font:** Amiri Quran, Uthmani Arabic only

**Character:** A bookish serif for the voice of the page, a clear humanist sans for everything you operate, and a true Mushaf face for the Quran itself.

### Hierarchy
- **Display** (PT Serif 700, 40sp / 46sp): reserved for large single statements; rarely used.
- **Headline** (PT Serif 700, 28sp / 34sp): the Home greeting on the band.
- **Headline Screen** (PT Serif 700, 24sp / 30sp): screen titles in the band header and the lockup wordmark; max two lines.
- **Title Serif** (PT Serif 700, 21sp / 27sp): section titles, preceded by a 12dp gold star.
- **Title** (Golos 600, 17sp / 23sp): list-row and tile titles, auto-hyphenated for long Uzbek and Russian words.
- **Body** (Golos 400, 17sp / 26sp): ayah translations and reading text.
- **Body Medium / Small** (Golos 400, 15sp and 13sp): row subtitles, explanatory copy; body-sm for the subtitle under a band heading.
- **Label** (Golos 600, 15sp / 20sp): button text and ayah references.
- **Label Small** (Golos 500, 12sp, +0.2sp): nav labels; steps to 10.5sp when a label exceeds 8 characters.
- **Quran** (Amiri Quran 400, 26sp, line height 2.0x): right-aligned, RTL; line height is doubled so stacked marks never collide.

### Named Rules
**The Subtitle-Below Rule.** A heading may carry a short subtitle set below it in body-sm/body-md muted colour. Small labels above headings (eyebrows, kickers) are not part of this system.

**The Untouched Scripture Rule.** Arabic is only ever set with the quran style: never bold, never letter-spaced, never truncated, never placed over ornament.

## Layout

Phone-only, edge-to-edge, single column. Primary screens open with the green band drawn behind the status bar; on Home it fills about 36% of screen height and the hourly ayah card rises over its lower edge. Other screens use the band header (12dp side padding, 8dp top) and a sheet with 28dp top corners or plain paper beneath.

Content gutters are 16-20dp; card internals 16-22dp. Vertical rhythm comes from a 4dp grid with 8, 10, 12 and 14dp gaps doing most of the work. List rows are at least 64dp tall; every touch target is at least 48dp.

Top-level screens (Home, Quran, Assistant, Recitation, Settings) draw under the floating tab bar and pad their scroll content by `LocalNavBarPadding` so lists scroll beneath the glass; fixed bottom controls (chat input, record button) sit above it.

## Elevation & Depth

A light hybrid. In light theme cards carry a soft green-tinted shadow (6dp, ambient green at 10%, spot at 14%) plus a gold hairline; in dark theme cards are flat and separated by tone and hairline only. The band uses a vertical gradient from band to band-deep. The tab bar is the one lifted object: a 10dp shadow under the track and a 14dp shadow under the lens.

### Shadow Vocabulary
- **Card** (6dp, green-tinted, light theme only): every surface card and ayah card.
- **Nav track** (10dp, green 18-22% light / black dark): the floating tab bar.
- **Lens** (14dp, black 22-30%): the glass drop shown while the bar is touched.

### Named Rules
**The Tonal Night Rule.** In dark theme nothing casts a shadow except the tab bar; depth comes from night-green to night-surface to night-raised.

## Shapes

Soft, generous rounding on a five-step scale (8, 12, 16, 22, 28dp), with full pills for every button, chip and icon button. Cards use 16dp; the ayah card 22dp outside with an inset 18dp gold frame 6dp in (a double frame). Sheets round only their top corners at 28dp.

The second shape language is geometric: the eight-point star (16-vertex path, inner radius 0.72-0.84 of outer) and the Rub el Hizb outline (a square plus the same square turned 45°). Medallions, section stars, ayah-card corners, the band lattice and the logo all derive from it.

## Components

### Buttons
Full-width, calm and unmistakable.
- **Shape:** full pill.
- **Primary:** mosque green fill, raised-paper label, 56dp tall, 24dp side padding; an optional 20dp leading icon; a 22dp spinner replaces the label while loading.
- **Secondary:** outlined pill, 52dp tall, 1dp gold frame keyline, green label.
- **Icon button:** 48dp circle, 22dp icon. On paper: sunk-paper fill with green icon (emphasized: green fill). On the band: white at 10% with on-band icon (emphasized: gold fill with deep-green icon).

### Chips
- **Pill:** gold-pale fill, label-md text, 12dp by 6dp padding, full radius. Used for status and counts.

### Cards / Containers
- **Corner Style:** 16dp (surface card), 22dp (ayah card), 28dp top corners (sheet).
- **Background:** raised paper (light), night surface (dark).
- **Shadow Strategy:** the card shadow in light theme only (see Elevation).
- **Border:** 1dp gold frame at 60% of the frame alpha.
- **Internal Padding:** 16-22dp.

### Navigation
A floating liquid-glass tab bar, after the owner's iOS reference. A 64dp pill track floats 14dp from the sides and 10dp above the system nav inset, holding five tabs (Home, Quran, Assistant, Recitation, Settings), each a 24dp icon over a label-sm label; outlined icons at rest, filled when selected. Selected tint is mosque green (gold-light in dark); unselected is muted ink.

The lens exists only while the bar is touched: at rest the bar is clean and the selected tab is carried by its filled icon and selected colour. On press a clear glass drop grows out of the touch point (appear spring 520 / 26, from 45% size), follows the finger, settles on a tab and then shrinks away (260 / 30). It stands 6dp proud of the track on each side. On Android 13+ an AGSL shader refracts what lies under it: the centre magnifies, the rim compresses, with a slight chromatic split at the edge; the lens is drawn with a dark band under the top rim, light pooled at the bottom, a bright 1dp rim and a small specular glint. Below Android 13 a painted lens fakes it and the covered tab scales up to 7%.

Motion: position on a spring of stiffness 230 / damping 17 (underdamped, it overshoots and settles), width 260 / 20, speed-driven stretch 320 / 9 (the wobble, capped at +45%), lift 380 / 20. Pressing anywhere on the bar pulls the lens under the finger; dragging follows at 900 / 45 with a 25% rubber band past the ends and the lens lifts to 1.14x; release (plus a short flick coast, 0.05 s of release velocity) settles on the nearest tab. With reduced motion the lens jumps and taps select directly.

The track is a translucent tint (white 62% light, near-black green 66% dark, with a 1dp white border) rather than a frosted backdrop: the backdrop blur pass is intentionally off because it cost about 40ms per scroll frame on a mid-range phone.

### Band Header
The green band (band to band-deep gradient, star lattice) behind the status bar. Title in headline-screen on-band colour, an optional subtitle under it in on-band-muted, leading and trailing slots for icon buttons, which switch to their on-band styles automatically.

### Star Lattice
Rub el Hizb outlines on a 64dp staggered grid at 12% gold alpha, fading toward the start edge (down to 20% of that) so headings sit on a calm field. Lives only on the band.

### Star Medallion
An icon (44% of the medallion size) on a green-mist eight-point star with a 1.2dp gold keyline. 52dp default, 44dp in list rows, 72dp in empty states.

### Ayah Card
The signature surface. Paper card with the double gold frame and four 5dp gold stars inset 14dp from the corners; inside, 22dp by 20dp padding with 10dp gaps: Arabic (quran style, right-aligned), translation in body, then the reference in label gold-ink with an optional trailing action slot.

### List Row
At least 64dp, 16dp by 10dp padding, 14dp gaps: optional 44dp medallion, title plus muted subtitle, trailing slot. Rows inside a card are separated by a 1dp soft-hairline divider inset 16dp.

### Empty State
Centered: 72dp medallion, serif headline-small title, muted body, optional secondary button at 220dp width.

### Logo Intro — «Two lights»
Noorain means two lights, so the cold start is told with two. The Android 12+ system splash shows the opening frame: two gold lights at rest 64dp left and right of centre on flat green (drawable `splash_two_lights`), and the in-app intro starts from exactly that frame once frames arrive smoothly. Timeline (~3.1s, linear clock): the lights circle each other and spiral inward with comet tails (0–1100ms); they meet in a soft bloom while a gold ring wave rolls outward and reveals the Rub el Hizb lattice behind it (1060–2100ms); the lights part and each becomes the pen of one square, drawing it with the keyline while the mark turns from −18° to rest (1250–2200ms); the knots interlace and the centre star blooms with a slight overshoot (2120–2600ms); «Noorain» opens from its centre with a gold glint running across it, and نورين follows (2250–3150ms). A tap skips to the end; with reduced motion the finished mark shows for 600ms.

## Do's and Don'ts

### Do:
- **Do** open every primary screen with the green band and its star lattice, and put the screen's content on paper below it.
- **Do** use gold-ink for any gold text on paper and gold-light for gold on dark.
- **Do** derive every ornament from the eight-point star or the two-square Rub el Hizb.
- **Do** keep touch targets at least 48dp and list rows at least 64dp.
- **Do** set Arabic only with the quran style (Amiri Quran, 2.0x line height, RTL, right-aligned).
- **Do** route every user-facing string through `ui/theme/Strings.kt` with all four languages filled.
- **Do** make every animation jump to its end state when the system animator scale is 0.
- **Do** pad top-level scroll content with `LocalNavBarPadding` so it scrolls under the glass bar.

### Don't:
- **Don't** fill large areas with gold; it is a keyline, star and reference colour.
- **Don't** add ornament beyond the eight-point star: no arabesques, borders of other patterns, illustrations, or imagery of living beings.
- **Don't** place small labels above headings (eyebrows, kickers); subtitles go below.
- **Don't** use emoji or text glyphs as icons; icons are Material icons, usually inside a star medallion.
- **Don't** decorate, crop, truncate, bold or overlay ornament on Quran text.
- **Don't** re-enable the frosted backdrop pass under the tab bar without measuring frame time.
- **Don't** use Material's default pink error colour; errors are terracotta.
- **Don't** alter the logo's colours, stretch it, add shadows or outlines, or change the interlace order (`brand/README.md`).
