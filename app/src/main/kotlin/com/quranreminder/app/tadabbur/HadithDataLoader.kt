package com.quranreminder.app.tadabbur

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HadithDataLoader {
    private var cache: List<HadithEntry>? = null

    fun load(context: Context): List<HadithEntry> {
        cache?.let { return it }
        val entries = runCatching {
            context.assets.open("hadith/hadith_index.json").bufferedReader().use { reader ->
                val type = object : TypeToken<List<HadithEntry>>() {}.type
                Gson().fromJson<List<HadithEntry>>(reader, type)
            }
        }.getOrElse { emptyList() }
        cache = entries
        return entries
    }
}
