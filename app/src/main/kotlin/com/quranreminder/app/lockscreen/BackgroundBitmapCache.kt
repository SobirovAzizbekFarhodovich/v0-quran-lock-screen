package com.quranreminder.app.lockscreen

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Process-wide memory cache for decoded lock-screen backgrounds, so swiping the
 * preview pager or re-rendering the wallpaper doesn't decode the same image again.
 * Keys are "<source>@<width>x<height>": one image can be cached at several sizes
 * (small pager cards and the full-screen render).
 *
 * Cached bitmaps are shared: callers must only draw them, never recycle or mutate.
 */
internal object BackgroundBitmapCache {
    // 1/8 of the app heap, measured in KB.
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun key(source: String, reqWidth: Int, reqHeight: Int) = "$source@${reqWidth}x$reqHeight"

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Drops every size cached for [source], e.g. after the file behind it was replaced. */
    fun invalidate(source: String) {
        val prefix = "$source@"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }
}
