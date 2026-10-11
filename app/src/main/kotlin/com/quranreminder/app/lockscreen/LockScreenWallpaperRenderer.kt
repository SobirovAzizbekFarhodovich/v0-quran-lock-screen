package com.quranreminder.app.lockscreen

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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

    private fun drawContent(canvas: Canvas, width: Int, height: Int, verse: QuranVerse, context: Context) {
        val safe = calculateSafeTextArea(width, height)
        val selectedLang = LockScreenWallpaperApplier.getLockScreenLanguage(context)
        val selectedFont = LockScreenWallpaperApplier.getLockScreenFont(context)

        var rawTranslation = cleanTranslation(verse.translation)
        var surahTitle = verse.surahName

        if (selectedLang == "uz_latin" || selectedLang == "uz") {
            rawTranslation = CyrillicToLatinConverter.convert(rawTranslation)
            surahTitle = CyrillicToLatinConverter.convert(surahTitle)
        }

        rawTranslation = FancyTextConverter.convert(rawTranslation, selectedFont)
        surahTitle = FancyTextConverter.convert(surahTitle, selectedFont)

        val translation = rawTranslation
        val panelPadding = (width * 0.052f).coerceAtLeast(18f)
        val contentWidth = (safe.width() - panelPadding * 2).toInt()
        val maxTextHeight = safe.height() - panelPadding * 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = chooseTitleTextSize(width)
            typeface = android.graphics.Typeface.create(selectedFont.typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0f
            setShadowLayer(width * 0.012f, 0f, width * 0.004f, Color.argb(170, 0, 0, 0))
        }
        val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = chooseTranslationTextSize(width, translation.length)
            typeface = selectedFont.typeface
            isFakeBoldText = true
            letterSpacing = 0f
            setShadowLayer(width * 0.013f, 0f, width * 0.005f, Color.argb(185, 0, 0, 0))
        }

        val title = "$surahTitle ${verse.surahNumber}:${verse.ayahNumber}"
        fitContentToSafeArea(
            title = title,
            titlePaint = titlePaint,
            translation = translation,
            translationPaint = translationPaint,
            width = contentWidth,
            maxHeight = maxTextHeight
        )
        var y = safe.top + panelPadding
        y += drawWrappedText(
            canvas = canvas,
            text = title,
            paint = titlePaint,
            x = safe.left,
            y = y,
            width = contentWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            maxLines = Int.MAX_VALUE
        )
        y += (width * 0.012f).coerceAtLeast(6f)
        drawWrappedText(
            canvas = canvas,
            text = translation,
            paint = translationPaint,
            x = safe.left,
            y = y,
            width = contentWidth,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            maxLines = Int.MAX_VALUE
        )
    }

    /** A calm, centered translucent panel keeps the verse readable over any wallpaper. */
    private fun drawVersePanel(canvas: Canvas, safe: RectF, width: Int) {
        val radius = (width * 0.065f).coerceAtLeast(28f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(92, 0, 0, 0)
            setShadowLayer(width * 0.026f, 0f, width * 0.012f, Color.argb(130, 0, 0, 0))
        }
        canvas.drawRoundRect(safe, radius, radius, shadow)
        shadow.clearShadowLayer()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.0035f).coerceAtLeast(2f)
            color = Color.argb(175, 216, 192, 119)
        }
        canvas.drawRoundRect(safe, radius, radius, border)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(216, 192, 119)
            strokeWidth = (width * 0.008f).coerceAtLeast(4f)
            strokeCap = Paint.Cap.ROUND
        }
        val accentWidth = safe.width() * 0.22f
        canvas.drawLine(
            safe.centerX() - accentWidth / 2,
            safe.top + width * 0.035f,
            safe.centerX() + accentWidth / 2,
            safe.top + width * 0.035f,
            accent
        )
    }

    private fun calculateSafeTextArea(width: Int, height: Int): RectF {
        val aspectRatio = height.toFloat() / width.toFloat()
        val horizontalInset = when {
            aspectRatio >= 2.1f -> width * 0.145f
            aspectRatio >= 1.85f -> width * 0.13f
            else -> width * 0.12f
        }
        val top = when {
            aspectRatio >= 2.1f -> height * 0.305f
            aspectRatio >= 1.85f -> height * 0.295f
            else -> height * 0.27f
        }
        val bottom = height * 0.76f
        return RectF(
            horizontalInset,
            top,
            width - horizontalInset,
            bottom
        )
    }

    private fun chooseTitleTextSize(width: Int): Float {
        return (width * 0.040f).coerceIn(19f, 44f)
    }

    private fun chooseTranslationTextSize(width: Int, length: Int): Float {
        val ratio = when {
            length <= 95 -> 0.038f
            length <= 170 -> 0.034f
            length <= 280 -> 0.029f
            length <= 420 -> 0.025f
            length <= 700 -> 0.021f
            else -> 0.018f
        }
        return (width * ratio).coerceIn(14f, 42f)
    }

    private fun fitContentToSafeArea(
        title: String,
        titlePaint: TextPaint,
        translation: String,
        translationPaint: TextPaint,
        width: Int,
        maxHeight: Float
    ) {
        val minTitleSize = (width * 0.032f).coerceAtMost(22f).coerceAtLeast(12f)
        val minTranslationSize = (width * 0.018f).coerceAtMost(15f).coerceAtLeast(8.5f)
        val gap = (width * 0.014f).coerceAtLeast(6f)

        repeat(44) {
            val titleLayout = buildLayout(title, titlePaint, width, Int.MAX_VALUE)
            val translationLayout = buildLayout(translation, translationPaint, width, Int.MAX_VALUE)
            if (titleLayout.height + gap + translationLayout.height <= maxHeight) {
                return
            }

            if (translationPaint.textSize > minTranslationSize) {
                translationPaint.textSize *= 0.92f
            } else if (titlePaint.textSize > minTitleSize) {
                titlePaint.textSize *= 0.94f
            } else {
                return
            }
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        alignment: Layout.Alignment,
        maxLines: Int
    ): Float {
        val layout = buildLayout(text, paint, width, maxLines, alignment)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.01f)
            .setMaxLines(maxLines)
            .setIncludePad(false)
            .build()
    }

    private fun cleanTranslation(text: String): String {
        return text.replace(Regex("\\s*\\[[^]]+\\]"), "").trim()
    }
}
