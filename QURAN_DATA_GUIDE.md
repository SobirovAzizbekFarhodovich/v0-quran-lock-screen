# Adding Complete Quran Data

This guide explains how to add the complete Quran (114 Surahs) in Uzbek translation to your app.

## Overview

The app currently includes a small sample dataset. To add a complete Uzbek translation, use a text source you are licensed or otherwise permitted to distribute.

## Option 1: JSON File Import (Recommended)

### Step 1: Prepare Your Data

Create a JSON file with the following structure:

**File**: `app/src/main/assets/quran/verses.json`

```json
{
  "verses": [
    {
      "surahNumber": 1,
      "surahName": "Al-Fatiha",
      "surahNameArabic": "الفاتحة",
      "ayahNumber": 1,
      "ayahText": "Bismillah ir-Rahman ir-Rahim",
      "ayahTextArabic": "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      "translation": "Rahmani, rahimiy Allohning nomida boshlayman",
      "transliteration": "Bismillah ir-Rahman ir-Rahim"
    },
    ...more verses...
  ]
}
```

### Step 2: Create Data Loader

Create a new file: `app/src/main/kotlin/com/quranreminder/app/data/QuranDataLoader.kt`

```kotlin
package com.quranreminder.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.database.QuranVerse

data class QuranData(
    @SerializedName("verses")
    val verses: List<QuranVerseDto>
)

data class QuranVerseDto(
    @SerializedName("surahNumber")
    val surahNumber: Int,
    @SerializedName("surahName")
    val surahName: String,
    @SerializedName("surahNameArabic")
    val surahNameArabic: String,
    @SerializedName("ayahNumber")
    val ayahNumber: Int,
    @SerializedName("ayahText")
    val ayahText: String,
    @SerializedName("ayahTextArabic")
    val ayahTextArabic: String,
    @SerializedName("translation")
    val translation: String,
    @SerializedName("transliteration")
    val transliteration: String = ""
)

object QuranDataLoader {
    fun loadFromAssets(context: Context): List<QuranVerse> {
        return try {
            val jsonString = context.assets.open("quran/verses.json").bufferedReader().use {
                it.readText()
            }
            val gson = Gson()
            val data = gson.fromJson(jsonString, QuranData::class.java)
            
            data.verses.map { dto ->
                QuranVerse(
                    surahNumber = dto.surahNumber,
                    surahName = dto.surahName,
                    surahNameArabic = dto.surahNameArabic,
                    ayahNumber = dto.ayahNumber,
                    ayahText = dto.ayahText,
                    ayahTextArabic = dto.ayahTextArabic,
                    translation = dto.translation,
                    transliteration = dto.transliteration
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
```

### Step 3: Update DatabaseInitializer

Edit: `app/src/main/kotlin/com/quranreminder/app/database/DatabaseInitializer.kt`

```kotlin
suspend fun initializeDatabase(database: QuranDatabase) {
    withContext(Dispatchers.IO) {
        val verseCount = database.quranVerseDao().getVerseCount()
        
        if (verseCount == 0) {
            // Try to load full data from assets first
            val fullVerses = QuranDataLoader.loadFromAssets(context)
            
            val versesToInsert = if (fullVerses.isNotEmpty()) {
                fullVerses
            } else {
                // Fall back to sample data if JSON not found
                QuranSampleData.getSampleVerses()
            }
            
            database.quranVerseDao().insertVerses(versesToInsert)
            
            // Initialize default settings
            val defaultSettings = AppSettings(...)
            database.appSettingsDao().insertSettings(defaultSettings)
        }
    }
}
```

## Option 2: SQL Migration

### Step 1: Create Migration File

Create: `app/src/main/assets/migrations/add_quran_data.sql`

```sql
INSERT INTO quran_verses (surahNumber, surahName, surahNameArabic, ayahNumber, ayahText, ayahTextArabic, translation, transliteration)
VALUES
  (1, 'Al-Fatiha', 'الفاتحة', 1, 'Bismillah', 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ', 'Rahmani, rahimiy...', ''),
  (1, 'Al-Fatiha', 'الفاتحة', 2, 'Al-hamdu lillah', 'الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ', 'Barcha olam-jahonlarning Rabi...', ''),
  ...
```

### Step 2: Update DatabaseInitializer

```kotlin
private fun loadSQLMigration(database: QuranDatabase) {
    try {
        val sqlScript = context.assets.open("migrations/add_quran_data.sql").bufferedReader().use {
            it.readText()
        }
        database.openHelper.writableDatabase.execSQL(sqlScript)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

## Option 3: API Integration

### Step 1: Create Data Service

```kotlin
package com.quranreminder.app.network

import retrofit2.http.GET
import retrofit2.http.Path
import com.quranreminder.app.database.QuranVerse

interface QuranApiService {
    @GET("quran/verses/uz")  // Example endpoint
    suspend fun getUzbekVerses(): List<QuranVerse>
    
    @GET("quran/surah/{number}/uz")
    suspend fun getSurahVerses(@Path("number") surahNumber: Int): List<QuranVerse>
}
```

### Step 2: Create Retrofit Client

```kotlin
object RetrofitClient {
    private const val BASE_URL = "https://api.quran.example.com/"
    
    val apiService: QuranApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuranApiService::class.java)
    }
}
```

### Step 3: Fetch and Cache Data

```kotlin
suspend fun syncQuranData(database: QuranDatabase) {
    try {
        val verses = RetrofitClient.apiService.getUzbekVerses()
        database.quranVerseDao().insertVerses(verses)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

## Option 4: Database Pre-population

### Step 1: Create Complete Database File

Generate a SQLite database file with all Quran data:

```bash
sqlite3 quran.db
```

Then use the `Room.databaseBuilder()` with `.createFromAsset()`:

```kotlin
Room.databaseBuilder(context, QuranDatabase::class.java, "quran_database")
    .createFromAsset("databases/quran_prepopulated.db")
    .build()
```

## Getting Quran Data Sources

### Free APIs
- **Quran API**: https://quran-api.com (English, Arabic, multiple languages)
- **Wazifa API**: https://www.wazifa.app (Various translations)
- **alQuran API**: https://api.alquran.cloud (Multiple languages)

### Data Files
- GitHub repositories with Quran JSON data
- Islamic app open-source projects
- Quran.com API

## Recommended Uzbek Translation

**Muhammad Sodiq Muhammad Yusuf translation**:
- Use only if you have distribution permission for the app.
- Keep the source/license notes with your data file.
- Put the licensed data in `app/src/main/assets/quran/verses.json`.

## Testing Your Data Import

### Add Test Logging

In `DatabaseInitializer.kt`:

```kotlin
suspend fun initializeDatabase(database: QuranDatabase) {
    withContext(Dispatchers.IO) {
        val verseCount = database.quranVerseDao().getVerseCount()
        Log.d("QuranDB", "Initial verse count: $verseCount")
        
        if (verseCount == 0) {
            val verses = loadYourData()
            database.quranVerseDao().insertVerses(verses)
            
            val newCount = database.quranVerseDao().getVerseCount()
            Log.d("QuranDB", "After import: $newCount verses")
        }
    }
}
```

### Verify in App

1. Open Reminders screen
2. Create a reminder for Surah 114 (last surah)
3. Search for text from later surahs
4. Browse through all surahs to verify data

## Performance Considerations

### Database Size
- Full Quran (~6,236 verses): ~2-3MB SQLite database
- With indices: ~3-4MB total
- App size remains under 5MB

### Load Time
- JSON import: 1-2 seconds on first run
- API fetch: 3-5 seconds (with caching)
- Pre-populated database: Instant

### Recommendations
- Use JSON file for offline-first, controlled rollout
- Use API + caching for always-up-to-date content
- Use pre-populated database for fastest startup

## Migration from Sample to Full Data

```kotlin
// Add this to DatabaseInitializer to clean sample data
if (isMigratingToFullData) {
    database.quranVerseDao().deleteAll()  // Clear sample data
    // Load new complete data
}
```

## Troubleshooting

### "No such table" Error
- Ensure migrations run in correct order
- Check if database.db exists after first run
- Clear app data and rebuild

### Data Not Appearing
- Verify JSON format is valid
- Check file path is correct: `assets/quran/verses.json`
- Add logging to verify data load completion

### App Crashes on Data Load
- Wrap data loading in try-catch
- Use `withContext(Dispatchers.IO)` for database operations
- Check Logcat for detailed error messages

## Next Steps

1. Choose your preferred data import method
2. Prepare or download the complete Uzbek translation
3. Follow the steps for your chosen option
4. Test thoroughly with different Surahs
5. Verify search and reminders work with all data

---

**Happy data loading!** Your app will be complete with the full Quran translation.
