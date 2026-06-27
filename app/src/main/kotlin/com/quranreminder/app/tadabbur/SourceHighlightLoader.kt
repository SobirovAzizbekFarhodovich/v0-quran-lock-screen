package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SourceHighlightLoader {
    private var cache: Map<String, SourceHighlight>? = null

    fun load(context: Context): Map<String, SourceHighlight> {
        cache?.let { return it }
        val highlights = runCatching {
            context.assets.open("tadabbur/source_highlights.json").bufferedReader().use { reader ->
                val type = object : TypeToken<List<SourceHighlight>>() {}.type
                Gson().fromJson<List<SourceHighlight>>(reader, type)
            }
        }.getOrElse { emptyList() }
        cache = highlights.associateBy { it.id }
        return cache.orEmpty()
    }
}
