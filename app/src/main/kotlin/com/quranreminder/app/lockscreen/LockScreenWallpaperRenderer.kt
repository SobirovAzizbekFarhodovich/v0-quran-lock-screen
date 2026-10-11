package com.quranreminder.app.lockscreen

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.quranreminder.app.R
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.quranreminder.app.database.QuranVerse

object LockScreenWallpaperRenderer {
    fun render(context: Context, verse: QuranVerse): Bitmap {
        val (width, height) = getRealScreenSize(context)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(context, canvas, width, height)
        drawContent(canvas, width, height, verse, context)

        return bitmap
    }

    /**
     * The lock screen's pixel size, always in PORTRAIT. The hourly alarm often renders while the
     * phone is in landscape (a video, a game); window metrics then report 2412x1080, and a
     * landscape wallpaper gets centre-cropped by the lock screen into a zoomed slice of the
     * background with the ayah cut off. The display's real size is orientation-independent
     * once sorted, and works from a non-visual (application) context.
     */
    internal fun getRealScreenSize(context: Context): Pair<Int, Int> {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        if (w <= 0 || h <= 0) {
            val fallback = context.resources.displayMetrics
            w = fallback.widthPixels.coerceAtLeast(360)
            h = fallback.heightPixels.coerceAtLeast(640)
        }
        return Pair(minOf(w, h), maxOf(w, h))
    }

    private fun drawBackground(context: Context, canvas: Canvas, width: Int, height: Int) {
        val wallpaper = LockScreenBackgroundManager.loadSelectedBitmap(context) ?: getUserWallpaper(context)
        if (wallpaper != null) {
            drawFullBackground(canvas, wallpaper, width, height)
            drawLegibilityOverlay(canvas, width, height)
            return
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.rgb(8, 31, 45),
                Color.rgb(18, 83, 83),
                Color.rgb(39, 45, 68)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = null
        paint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(width * 0.78f, height * 0.18f, width * 0.22f, paint)
        paint.color = Color.argb(38, 255, 255, 255)
        canvas.drawCircle(width * 0.18f, height * 0.82f, width * 0.34f, paint)
        drawLegibilityOverlay(canvas, width, height)
    }

    private fun drawLegibilityOverlay(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.argb(80, 0, 0, 0),
                Color.argb(34, 0, 0, 0),
                Color.argb(12, 0, 0, 0),
                Color.argb(72, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.36f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun getUserWallpaper(context: Context): Bitmap? {
        val fileBitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                WallpaperManager.getInstance(context)
                    .getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                    ?.use { file -> BitmapFactory.decodeFileDescriptor(file.fileDescriptor) }
            } else {
                null
            }
        }.getOrNull()
        if (fileBitmap != null) {
            return fileBitmap
        }

        return runCatching {
            val manager = WallpaperManager.getInstance(context)
            val drawable = manager.drawable ?: return@runCatching null
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val width = drawable.intrinsicWidth.coerceAtLeast(1080)
                    val height = drawable.intrinsicHeight.coerceAtLeast(1920)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                    }
                }
            }
        }.getOrNull()
    }

    /** Draws [bitmap] centre-cropped to fill the screen, keeping its aspect ratio (never stretched). */
    private fun drawFullBackground(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int) {
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val srcW = width / scale
        val srcH = height / scale
        val left = (bitmap.width - srcW) / 2f
        val top = (bitmap.height - srcH) / 2f
        val src = android.graphics.Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        val dest = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, src, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * Just the verse layer (transparent background) at [width] x [height], exactly as [render]
     * draws it — the lock-screen preview cards show this so the preview is what gets applied.
     */
    fun renderTextOverlay(context: Context, verse: QuranVerse, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        drawContent(Canvas(bitmap), bitmap.width, bitmap.height, verse, context)
        return bitmap
    }

    private val Cream = Color.rgb(251, 247, 238)
    private val Gold = Color.rgb(230, 204, 126)

    private fun golos(context: Context, weight: Int): Typeface {
        val base = ResourcesCompat.getFont(context, R.font.golos_text) ?: return Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false) else base
    }

    /**
     * The verse, centred between the clock and the bottom controls: the Arabic ayah (short and
     * medium ayahs only), a small gold star between hairlines, the full translation, and the
     * reference in gold. The translation is never cut: when it is long the Arabic is left out
     * and the text size steps down until everything fits.
     */
    private fun drawContent(canvas: Canvas, width: Int, height: Int, verse: QuranVerse, context: Context) {
        val selectedLang = LockScreenWallpaperApplier.getLockScreenLanguage(context)
        val selectedFont = LockScreenWallpaperApplier.getLockScreenFont(context)

        var translation = LockScreenText.cleanTranslation(verse.translation)
        var surahTitle = verse.surahName
        if (selectedLang == "uz_latin" || selectedLang == "uz") {
            translation = CyrillicToLatinConverter.convert(translation)
            surahTitle = CyrillicToLatinConverter.convert(surahTitle)
        }
        translation = FancyTextConverter.convert(translation, selectedFont)
        surahTitle = FancyTextConverter.convert(surahTitle, selectedFont)
        val arabic = verse.ayahTextArabic.trim()

        val textFace = when (selectedFont) {
            LockScreenFontFamily.STANDART -> golos(context, 500)
            LockScreenFontFamily.KLASSIK -> ResourcesCompat.getFont(context, R.font.pt_serif_regular) ?: Typeface.SERIF
            else -> selectedFont.typeface
        }
        val arabicFace = ResourcesCompat.getFont(context, R.font.amiri_quran) ?: Typeface.SERIF
        val shadowRadius = width * 0.007f
        val shadowDy = width * 0.0025f

        val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = arabicFace
            color = Cream
            textSize = width * 0.058f
            setShadowLayer(shadowRadius, 0f, shadowDy, Color.argb(160, 0, 0, 0))
        }
        val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = textFace
            color = Cream
            setShadowLayer(shadowRadius, 0f, shadowDy, Color.argb(160, 0, 0, 0))
        }
        val referencePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = golos(context, 600)
            color = Gold
            textSize = width * 0.028f
            letterSpacing = 0.12f
            setShadowLayer(shadowRadius, 0f, shadowDy, Color.argb(140, 0, 0, 0))
        }

        // Between the system clock (top ~27%) and the bottom shortcuts (~80%).
        val area = RectF(width * 0.08f, height * 0.30f, width * 0.92f, height * 0.80f)
        val textWidth = area.width().toInt()
        val ornamentGap = width * 0.075f
        val referenceGap = width * 0.035f
        val reference = "${surahTitle.uppercase()}  ·  ${verse.surahNumber}:${verse.ayahNumber}"
        val referenceLayout = buildLayout(reference, referencePaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1f)

        var withArabic = LockScreenText.showArabic(arabic, translation)
        var arabicLayout: StaticLayout? = null
        var translationLayout: StaticLayout
        var total: Float
        var size = width * 0.046f
        while (true) {
            translationPaint.textSize = size
            translationLayout = buildLayout(translation, translationPaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.18f)
            arabicLayout = if (withArabic) buildLayout(arabic, arabicPaint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.12f) else null
            total = (arabicLayout?.height?.toFloat()?.plus(ornamentGap) ?: 0f) +
                translationLayout.height + referenceGap + referenceLayout.height
            if (total <= area.height()) break
            if (withArabic && size <= width * 0.034f) {
                // Not enough room for both: the translation matters more on the lock screen.
                withArabic = false
                size = width * 0.046f
                continue
            }
            if (size <= width * 0.020f) break
            size -= width * 0.002f
        }

        val top = (area.centerY() - total / 2f).coerceAtLeast(area.top)
        // A soft dark veil behind the block keeps it readable over any photo.
        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 4, 20, 16)
            maskFilter = BlurMaskFilter(width * 0.06f, BlurMaskFilter.Blur.NORMAL)
        }
        val padX = width * 0.03f
        val padY = width * 0.07f
        canvas.drawRoundRect(
            RectF(area.left - padX, top - padY, area.right + padX, top + total + padY),
            width * 0.08f, width * 0.08f, veil
        )

        var y = top
        arabicLayout?.let {
            drawLayout(canvas, it, area.left, y)
            y += it.height
            drawOrnament(canvas, area.centerX(), y + ornamentGap / 2f, width)
            y += ornamentGap
        }
        drawLayout(canvas, translationLayout, area.left, y)
        y += translationLayout.height + referenceGap
        drawLayout(canvas, referenceLayout, area.left, y)
    }

    /** Hairline – eight-point star – hairline, in gold. */
    private fun drawOrnament(canvas: Canvas, cx: Float, cy: Float, width: Int) {
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Gold
            strokeWidth = (width * 0.002f).coerceAtLeast(1.5f)
            alpha = 190
        }
        val outer = width * 0.012f
        val lineLen = width * 0.09f
        canvas.drawLine(cx - outer * 2.2f - lineLen, cy, cx - outer * 2.2f, cy, gold)
        canvas.drawLine(cx + outer * 2.2f, cy, cx + outer * 2.2f + lineLen, cy, gold)
        val star = android.graphics.Path()
        for (i in 0 until 16) {
            val r = if (i % 2 == 0) outer else outer * 0.62f
            val a = -Math.PI / 2 + i * Math.PI / 8
            val x = cx + (r * Math.cos(a)).toFloat()
            val yy = cy + (r * Math.sin(a)).toFloat()
            if (i == 0) star.moveTo(x, yy) else star.lineTo(x, yy)
        }
        star.close()
        gold.alpha = 255
        gold.style = Paint.Style.FILL
        canvas.drawPath(star, gold)
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment,
        spacingMultiplier: Float
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(alignment)
        .setLineSpacing(0f, spacingMultiplier)
        .setIncludePad(false)
        .build()
}

/** What the lock screen shows of a verse; shared by the renderer and the verse selector. */
internal object LockScreenText {
    /** Auto-picked hourly verses longer than this (cleaned translation) are skipped when possible. */
    const val COMFORTABLE_LENGTH = 300

    /**
     * Drops bracketed editorial notes and long parenthetical commentary — "(Ya'ni, …)" — which
     * explain the ayah rather than translate it. Short inline parentheses such as "(ketgani)"
     * are part of the sentence and stay, so the meaning is never cut.
     */
    fun cleanTranslation(text: String): String = text
        .replace(Regex("\\s*\\[[^]]+\\]"), "")
        // One level of nesting: "(… Ikkovi(ayol)dan …)" is still a single commentary note.
        .replace(Regex("\\s*\\((?:[^()]|\\([^()]*\\))*\\)")) { if (it.value.trim().length >= 60) "" else it.value }
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    /** The Arabic is shown only for short and medium ayahs; long ones get the translation alone. */
    fun showArabic(arabic: String, cleanedTranslation: String): Boolean =
        arabic.isNotBlank() && arabic.length <= 110 && cleanedTranslation.length <= 200
}
