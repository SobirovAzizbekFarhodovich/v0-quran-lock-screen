#!/usr/bin/env python3
"""Generates every Noorain logo asset from one geometry.

The mark: two interlaced squares (the "two lights", noorain) forming the
Rub el Hizb star (۞, the hizb marker of the Mushaf), with a light at its centre.

Outputs SVG masters in brand/ and Android vector drawables in app/src/main/res/.
Wordmarks are converted to outlines from the bundled fonts, so the SVGs need no
installed font. Run from the repo root: python3 brand/generate.py
"""
from __future__ import annotations

import math
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
BRAND = ROOT / "brand"
RES = ROOT / "app/src/main/res"
FONTS = RES / "font"

# Palette (mirrors ui/theme/Theme.kt NurColors).
GREEN = "#0E3B2E"
GREEN_DEEP = "#082A20"
GOLD = "#C9A646"
GOLD_LIGHT = "#E6CC7E"
PAPER = "#FBF8F1"
INK = "#0E3B2E"

# Geometry on a 512 canvas.
C = 256.0
HALF = 150.0  # half side of each square
BAND = 26.0  # band (stroke) width
EDGE = 7.0  # dark keyline on each side of a band, so the interlace reads
LIGHT_R = 40.0


def square_a() -> list[tuple[float, float]]:
    return [(C - HALF, C - HALF), (C + HALF, C - HALF), (C + HALF, C + HALF), (C - HALF, C + HALF)]


def square_b() -> list[tuple[float, float]]:
    d = HALF * math.sqrt(2)
    return [(C, C - d), (C + d, C), (C, C + d), (C - d, C)]


def poly_path(points, scale=1.0, dx=0.0, dy=0.0) -> str:
    pts = [f"{x * scale + dx:.2f},{y * scale + dy:.2f}" for x, y in points]
    return "M" + " L".join(pts) + " Z"


def circle_path(cx, cy, r, scale=1.0, dx=0.0, dy=0.0) -> str:
    cx, cy, r = cx * scale + dx, cy * scale + dy, r * scale
    return (f"M{cx - r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1,0 {cx + r:.2f},{cy:.2f} "
            f"A{r:.2f},{r:.2f} 0 1,0 {cx - r:.2f},{cy:.2f} Z")


# Crossings where square B passes OVER square A (alternating interlace).
B_OVER = [(C + 62, C - HALF), (C + HALF, C + 62), (C - 62, C + HALF), (C - HALF, C - 62)]
PATCH_R = 26.0


def star_inner(r_out, r_in, cx=C, cy=C, rot=-90.0, n=8):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180.0 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


# ---------------------------------------------------------------- SVG mark
def svg_mark(gold: str, gold_light: str, keyline: str, light_core: str, glow: bool, s=1.0, dx=0.0, dy=0.0) -> str:
    a, b = poly_path(square_a(), s, dx, dy), poly_path(square_b(), s, dx, dy)
    band = BAND * s
    key = (BAND + 2 * EDGE) * s

    def band_group(path, fill_color):
        return (f'<path d="{path}" fill="none" stroke="{keyline}" stroke-width="{key:.2f}" stroke-linejoin="miter"/>'
                f'<path d="{path}" fill="none" stroke="{fill_color}" stroke-width="{band:.2f}" stroke-linejoin="miter"/>')

    patches = "".join(f'<path d="{circle_path(x, y, PATCH_R, s, dx, dy)}"/>' for x, y in B_OVER)
    uid = f"{int(s * 1000)}{int(dx)}{int(dy)}"
    out = [f'<defs><clipPath id="over{uid}">{patches}</clipPath>']
    if glow:
        out.append(
            f'<radialGradient id="glow{uid}" cx="{C * s + dx:.2f}" cy="{C * s + dy:.2f}" r="{LIGHT_R * 2.1 * s:.2f}" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{light_core}" stop-opacity="1"/>'
            f'<stop offset="0.45" stop-color="{gold_light}" stop-opacity="0.55"/>'
            f'<stop offset="1" stop-color="{gold_light}" stop-opacity="0"/></radialGradient>')
    out.append("</defs>")
    out.append(band_group(b, gold_light))
    out.append(band_group(a, gold))
    out.append(f'<g clip-path="url(#over{uid})">{band_group(b, gold_light)}</g>')
    if glow:
        out.append(f'<path d="{circle_path(C, C, LIGHT_R * 2.1, s, dx, dy)}" fill="url(#glow{uid})"/>')
    out.append(f'<path d="{poly_path(star_inner(LIGHT_R, LIGHT_R * 0.55), s, dx, dy)}" fill="{light_core}"/>')
    return "".join(out)


def text_outline(font_file: Path, text: str, size: float, x: float, baseline: float, rtl=False) -> tuple[str, float]:
    """Outlines `text` (simple shaping: one glyph per char; fine for Latin)."""
    font = TTFont(font_file)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    upm = font["head"].unitsPerEm
    k = size / upm
    hmtx = font["hmtx"]
    paths, cursor = [], 0.0
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        tpen = TransformPen(pen, (k, 0, 0, -k, x + cursor, baseline))
        gs[name].draw(tpen)
        paths.append(pen.getCommands())
        cursor += hmtx[name][0] * k
    return " ".join(paths), cursor


def svg_doc(w, h, body, bg=None) -> str:
    bgrect = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">{bgrect}{body}</svg>\n'


# ------------------------------------------------------- Android vectors
def vd(w, h, vw, vh, body) -> str:
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by brand/generate.py; edit the generator, not this file. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{w}dp" android:height="{h}dp"\n'
            f'    android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n{body}</vector>\n')


def vd_band(path, color, keyline, s):
    out = ""
    if keyline:
        out += (f'    <path android:pathData="{path}" android:strokeColor="{keyline}" '
                f'android:strokeWidth="{(BAND + 2 * EDGE) * s:.2f}" android:strokeLineJoin="miter"/>\n')
    out += (f'    <path android:pathData="{path}" android:strokeColor="{color}" '
            f'android:strokeWidth="{BAND * s:.2f}" android:strokeLineJoin="miter"/>\n')
    return out


def vd_mark(gold, gold_light, keyline, core, s, dx, dy, glow=True) -> str:
    a, b = poly_path(square_a(), s, dx, dy), poly_path(square_b(), s, dx, dy)
    clip = " ".join(circle_path(x, y, PATCH_R, s, dx, dy) for x, y in B_OVER)
    out = vd_band(b, gold_light, keyline, s) + vd_band(a, gold, keyline, s)
    out += f'    <group>\n    <clip-path android:pathData="{clip}"/>\n' + vd_band(b, gold_light, keyline, s) + "    </group>\n"
    if glow:
        cx, cy, r = C * s + dx, C * s + dy, LIGHT_R * 2.1 * s
        out += (f'    <path android:pathData="{circle_path(C, C, LIGHT_R * 2.1, s, dx, dy)}">\n'
                '        <aapt:attr name="android:fillColor">\n'
                f'            <gradient android:type="radial" android:centerX="{cx:.2f}" android:centerY="{cy:.2f}" android:gradientRadius="{r:.2f}">\n'
                f'                <item android:offset="0" android:color="#FF{core[1:]}"/>\n'
                f'                <item android:offset="0.45" android:color="#8C{gold_light[1:]}"/>\n'
                f'                <item android:offset="1" android:color="#00{gold_light[1:]}"/>\n'
                '            </gradient>\n        </aapt:attr>\n    </path>\n')
    out += f'    <path android:pathData="{poly_path(star_inner(LIGHT_R, LIGHT_R * 0.55), s, dx, dy)}" android:fillColor="{core}"/>\n'
    return out


def lattice_vd(size, color, alpha_hex) -> str:
    """A faint field of small eight-point stars for the icon background."""
    out, step = "", size / 4
    for gy in range(5):
        for gx in range(5):
            cx, cy = gx * step, gy * step
            out += f'    <path android:pathData="{poly_path(star_inner(step * 0.22, step * 0.13, cx, cy))}" android:fillColor="#{alpha_hex}{color[1:]}"/>\n'
    return out


def splash_avd(s: float, off: float) -> str:
    """AnimatedVectorDrawable for windowSplashScreenAnimatedIcon (≤1000ms)."""
    a, b = poly_path(square_a(), s, off, off), poly_path(square_b(), s, off, off)
    clip = " ".join(circle_path(x, y, PATCH_R, s, off, off) for x, y in B_OVER)
    key_w, band_w = (BAND + 2 * EDGE) * s, BAND * s
    cx = cy = C * s + off
    r = LIGHT_R * 2.1 * s
    core = poly_path(star_inner(LIGHT_R, LIGHT_R * 0.55), s, off, off)

    def band(name, path, color, alpha_anim=False):
        start = 'android:strokeAlpha="0"' if alpha_anim else 'android:trimPathEnd="0"'
        return (f'                    <path android:name="{name}_key" android:pathData="{path}" android:strokeColor="{GREEN_DEEP}" '
                f'android:strokeWidth="{key_w:.2f}" android:strokeLineJoin="miter" {start}/>\n'
                f'                    <path android:name="{name}" android:pathData="{path}" android:strokeColor="{color}" '
                f'android:strokeWidth="{band_w:.2f}" android:strokeLineJoin="miter" {start}/>\n')

    def anim(target, prop, frm, to, dur, delay=0, value_type="floatType"):
        return (f'    <target android:name="{target}">\n        <aapt:attr name="android:animation">\n'
                f'            <objectAnimator android:propertyName="{prop}" android:valueFrom="{frm}" android:valueTo="{to}" '
                f'android:valueType="{value_type}" android:duration="{dur}" android:startOffset="{delay}" '
                'android:interpolator="@android:interpolator/fast_out_slow_in"/>\n'
                '        </aapt:attr>\n    </target>\n')

    vector = (
        '        <vector android:width="240dp" android:height="240dp" android:viewportWidth="240" android:viewportHeight="240">\n'
        f'            <group android:name="turn" android:pivotX="{cx:.2f}" android:pivotY="{cy:.2f}" android:rotation="-45">\n'
        f'                <group android:name="grow" android:pivotX="{cx:.2f}" android:pivotY="{cy:.2f}" android:scaleX="0.85" android:scaleY="0.85">\n'
        f'                    <path android:name="glow" android:pathData="{circle_path(C, C, LIGHT_R * 2.1, s, off, off)}" android:fillAlpha="0">\n'
        '                        <aapt:attr name="android:fillColor">\n'
        f'                            <gradient android:type="radial" android:centerX="{cx:.2f}" android:centerY="{cy:.2f}" android:gradientRadius="{r:.2f}">\n'
        '                                <item android:offset="0" android:color="#FFFFF6DA"/>\n'
        f'                                <item android:offset="0.45" android:color="#8C{GOLD_LIGHT[1:]}"/>\n'
        f'                                <item android:offset="1" android:color="#00{GOLD_LIGHT[1:]}"/>\n'
        '                            </gradient>\n                        </aapt:attr>\n                    </path>\n'
        + band("b", b, GOLD_LIGHT) + band("a", a, GOLD)
        + f'                    <group>\n                    <clip-path android:pathData="{clip}"/>\n'
        + band("o", b, GOLD_LIGHT, alpha_anim=True)
        + '                    </group>\n'
        + f'                    <path android:name="core" android:pathData="{core}" android:fillColor="#FFFFF6DA" android:fillAlpha="0"/>\n'
        '                </group>\n            </group>\n        </vector>\n')
    targets = (
        anim("turn", "rotation", -45, 0, 900)
        + anim("grow", "scaleX", 0.85, 1, 900) + anim("grow", "scaleY", 0.85, 1, 900)
        + anim("a_key", "trimPathEnd", 0, 1, 680) + anim("a", "trimPathEnd", 0, 1, 680)
        + anim("b_key", "trimPathEnd", 0, 1, 680, 120) + anim("b", "trimPathEnd", 0, 1, 680, 120)
        + anim("o_key", "strokeAlpha", 0, 1, 160, 780) + anim("o", "strokeAlpha", 0, 1, 160, 780)
        + anim("glow", "fillAlpha", 0, 1, 200, 800) + anim("core", "fillAlpha", 0, 1, 200, 800)
    )
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by brand/generate.py; edit the generator, not this file. -->\n'
            '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt">\n'
            '    <aapt:attr name="android:drawable">\n' + vector + '    </aapt:attr>\n' + targets + '</animated-vector>\n')


def splash_two_lights() -> str:
    """Android 12+ system splash: the two lights at rest, exactly where NurLogoIntro starts.

    The 240-unit vector is shown at 288dp (icon without background), so 1 unit = 1.2dp.
    The lights sit 64dp left and right of centre; halo 46dp, glow 22dp, core 4.5dp.
    """
    k = 1.2
    off = 64 / k
    halo, glow, core = 46 / k, 22 / k, 4.5 / k
    body = ""
    for cx in (120 - off, 120 + off):
        body += (f'    <path android:pathData="{circle_path(cx, 120, halo)}">\n'
                 '        <aapt:attr name="android:fillColor">\n'
                 f'            <gradient android:type="radial" android:centerX="{cx:.2f}" android:centerY="120" android:gradientRadius="{halo:.2f}">\n'
                 f'                <item android:offset="0" android:color="#4D{GOLD_LIGHT[1:]}"/>\n'
                 f'                <item android:offset="1" android:color="#00{GOLD_LIGHT[1:]}"/>\n'
                 '            </gradient>\n        </aapt:attr>\n    </path>\n')
        body += (f'    <path android:pathData="{circle_path(cx, 120, glow)}">\n'
                 '        <aapt:attr name="android:fillColor">\n'
                 f'            <gradient android:type="radial" android:centerX="{cx:.2f}" android:centerY="120" android:gradientRadius="{glow:.2f}">\n'
                 '                <item android:offset="0" android:color="#FFFFF6DA"/>\n'
                 f'                <item android:offset="0.35" android:color="#BF{GOLD_LIGHT[1:]}"/>\n'
                 f'                <item android:offset="1" android:color="#00{GOLD_LIGHT[1:]}"/>\n'
                 '            </gradient>\n        </aapt:attr>\n    </path>\n')
        body += f'    <path android:pathData="{circle_path(cx, 120, core)}" android:fillColor="#FFFFFBEF"/>\n'
    return vd(240, 240, 240, 240, body)


def main() -> None:
    BRAND.mkdir(exist_ok=True)
    serif_bold = FONTS / "pt_serif_bold.ttf"
    amiri = FONTS / "amiri_quran.ttf"

    # Mark: light and dark grounds.
    for variant, (gold, light, key, core, bg) in {
        "light": (GOLD, "#D9B95E", GREEN, PAPER, None),
        "dark": (GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA", None),
    }.items():
        (BRAND / f"noorain-mark-{variant}.svg").write_text(svg_doc(512, 512, svg_mark(gold, light, key, core, True)))

    # App icon master (1024, green field, rounded square).
    icon_body = (f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#145040"/>'
                 f'<stop offset="1" stop-color="{GREEN_DEEP}"/></linearGradient></defs>'
                 f'<rect width="1024" height="1024" rx="230" fill="url(#bg)"/>'
                 + svg_mark(GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA", True, s=1.5, dx=512 - C * 1.5, dy=512 - C * 1.5))
    (BRAND / "noorain-icon.svg").write_text(svg_doc(1024, 1024, icon_body))

    # Wordmark + horizontal lockup.
    for variant, (word, mark_args) in {
        "light": (INK, (GOLD, "#D9B95E", GREEN, PAPER)),
        "dark": (PAPER, (GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA")),
    }.items():
        word_path, word_w = text_outline(serif_bold, "Noorain", 150, 0, 150)
        (BRAND / f"noorain-wordmark-{variant}.svg").write_text(
            svg_doc(int(word_w) + 4, 190, f'<path d="{word_path}" fill="{word}"/>'))
        mark = svg_mark(*mark_args, True, s=0.42, dx=0, dy=0)
        wp, ww = text_outline(serif_bold, "Noorain", 112, 240, 150)
        lock_w = int(240 + ww + 10)
        (BRAND / f"noorain-lockup-{variant}.svg").write_text(
            svg_doc(lock_w, 216, mark + f'<path d="{wp}" fill="{word}"/>'))

    # Android: adaptive launcher icon (108dp canvas, 66dp safe zone).
    s = 64.0 / 512.0  # mark fits ~64 units of the 108 viewport
    off = (108 - 512 * s) / 2
    (RES / "drawable/ic_launcher_foreground.xml").write_text(
        vd(108, 108, 108, 108, vd_mark(GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA", s, off, off)))
    bg = ('    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">\n'
          '        <aapt:attr name="android:fillColor">\n'
          '            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">\n'
          '                <item android:offset="0" android:color="#FF145040"/>\n'
          f'                <item android:offset="1" android:color="#FF{GREEN_DEEP[1:]}"/>\n'
          '            </gradient>\n        </aapt:attr>\n    </path>\n') + lattice_vd(108, GOLD, "14")
    (RES / "drawable/ic_launcher_background.xml").write_text(vd(108, 108, 108, 108, bg))
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(
        vd(108, 108, 108, 108, vd_mark("#FFFFFFFF", "#FFFFFFFF", None, "#FFFFFFFF", s, off, off, glow=False)))
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
                '</adaptive-icon>\n')
    (RES / "mipmap-anydpi-v26").mkdir(exist_ok=True)
    (RES / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive)
    (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").write_text(adaptive)

    # Splash icon (Android 12+: 240dp canvas, icon inside a 160dp circle).
    s2 = 120.0 / 512.0
    off2 = (240 - 512 * s2) / 2
    (RES / "drawable/splash_mark.xml").write_text(
        vd(240, 240, 240, 240, vd_mark(GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA", s2, off2, off2)))

    # The splash now shows the intro's opening frame: two lights at rest (see NurIntro.kt).
    (RES / "drawable/splash_two_lights.xml").write_text(splash_two_lights())

    # In-app marks for light and dark grounds (Compose painterResource).
    (RES / "drawable/noorain_mark.xml").write_text(vd(48, 48, 512, 512, vd_mark(GOLD, "#D9B95E", GREEN, PAPER, 1, 0, 0)))
    (RES / "drawable/noorain_mark_on_dark.xml").write_text(
        vd(48, 48, 512, 512, vd_mark(GOLD, GOLD_LIGHT, GREEN_DEEP, "#FFF6DA", 1, 0, 0)))

    # Notification small icon: a flat white silhouette of the star.
    star = poly_path(star_inner(11, 7.2, 12, 12, rot=-90 + 22.5 / 1), 1, 0, 0)
    (RES / "drawable/ic_quran_notification.xml").write_text(vd(
        24, 24, 24, 24,
        f'    <path android:pathData="{star} {circle_path(12, 12, 3.2)}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd"/>\n'))
    print("generated brand/ and res/ assets")


if __name__ == "__main__":
    main()
