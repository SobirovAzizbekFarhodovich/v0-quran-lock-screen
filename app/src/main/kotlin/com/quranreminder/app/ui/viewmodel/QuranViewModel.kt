package com.quranreminder.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.data.QuranDataLoader
import com.quranreminder.app.database.Bookmark
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.SurahHeader
import com.quranreminder.app.data.ContentLoadException
import com.quranreminder.app.util.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Content-load state, always tagged with the language it belongs to. */
sealed interface LanguageDownloadState {
    data object Idle : LanguageDownloadState
    data class InProgress(val language: String) : LanguageDownloadState
    data class Success(val language: String) : LanguageDownloadState
    /** [messageKey] is an AppStrings key (e.g. `loading.error.offline`). */
    data class Failure(val language: String, val messageKey: String) : LanguageDownloadState
}

class QuranViewModel(
    private val repository: QuranRepository,
    private val appContext: Context,
    /** Persists which language is now stored in the database (called right after the swap). */
    private val onContentReady: (String) -> Unit = {}
) : ViewModel() {
    
    private val _surahs = MutableStateFlow<List<SurahHeader>>(emptyList())
    val surahs: StateFlow<List<SurahHeader>> = _surahs.asStateFlow()

    private val _currentSurahVerses = MutableStateFlow<List<QuranVerse>>(emptyList())
    val currentSurahVerses: StateFlow<List<QuranVerse>> = _currentSurahVerses.asStateFlow()

    private val _searchResults = MutableStateFlow<List<QuranVerse>>(emptyList())
    val searchResults: StateFlow<List<QuranVerse>> = _searchResults.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _bookmarkedVerseKeys = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedVerseKeys: StateFlow<Set<String>> = _bookmarkedVerseKeys.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentSurahJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadSurahs()
        loadBookmarks()
    }

    private fun loadSurahs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getAllSurahs().collect { surahs ->
                    _surahs.value = surahs
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadSurahVerses(surahNumber: Int) {
        currentSurahJob?.cancel()
        currentSurahJob = viewModelScope.launch {
            try {
                repository.getVersesBySurah(surahNumber).collect { verses ->
                    _currentSurahVerses.value = verses
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun searchVerses(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Debounce: the DAO search is an unindexed LIKE scan over ~6k rows,
            // so only run it once typing pauses.
            delay(SEARCH_DEBOUNCE_MS)
            try {
                repository.searchVerses(query).collect { results ->
                    _searchResults.value = results
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            repository.getAllBookmarks().collect { bookmarks ->
                _bookmarks.value = bookmarks
                _bookmarkedVerseKeys.value = bookmarks.map { it.key }.toSet()
            }
        }
    }

    fun toggleBookmark(surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            try {
                val existing = repository.getBookmark(surahNumber, ayahNumber).first()
                if (existing != null) {
                    repository.deleteBookmark(existing)
                } else {
                    repository.insertBookmark(
                        Bookmark(
                            surahNumber = surahNumber,
                            ayahNumber = ayahNumber
                        )
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private val _languageDownloadState = MutableStateFlow<LanguageDownloadState>(LanguageDownloadState.Idle)
    val languageDownloadState: StateFlow<LanguageDownloadState> = _languageDownloadState.asStateFlow()

    private var downloadJob: Job? = null
    private val replaceMutex = Mutex()

    /**
     * Loads [language]'s Quran content into the database. Bundled languages (Uzbek
     * Latin/Cyrillic) come from assets with no network; others are downloaded. A new
     * request for a different language cancels the one in flight; a repeat request for
     * the language already loading/loaded is a no-op. The previous content stays in the
     * database until the new one is fully loaded and validated.
     */
    fun downloadLanguageContent(language: String) {
        when (val current = _languageDownloadState.value) {
            is LanguageDownloadState.InProgress ->
                if (current.language == language && downloadJob?.isActive == true) return
            is LanguageDownloadState.Success -> if (current.language == language) return
            else -> Unit
        }
        downloadJob?.cancel()
        _languageDownloadState.value = LanguageDownloadState.InProgress(language)
        downloadJob = viewModelScope.launch {
            val outcome = try {
                val verses = withContext(Dispatchers.IO) {
                    if (QuranDataLoader.isBundled(language)) {
                        QuranDataLoader.loadFromAssets(appContext, language)
                    } else {
                        // A language downloaded before loads from its on-disk copy: instant, offline.
                        QuranDataLoader.loadFromDiskCache(appContext, language) ?: run {
                            if (!NetworkStatus.isServerReachable(appContext)) {
                                throw ContentLoadException("loading.error.offline")
                            }
                            QuranDataLoader.loadFromServer(appContext, language)
                        }
                    }
                }
                if (verses.size < QuranDataLoader.EXPECTED_VERSE_COUNT) {
                    throw ContentLoadException("loading.error.incomplete")
                }
                replaceMutex.withLock {
                    ensureActive()
                    repository.replaceVerses(verses)
                    onContentReady(language)
                }
                LanguageDownloadState.Success(language)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ContentLoadException) {
                LanguageDownloadState.Failure(language, error.code)
            } catch (error: Exception) {
                android.util.Log.e("QuranViewModel", "Loading $language failed", error)
                LanguageDownloadState.Failure(
                    language,
                    if (QuranDataLoader.isBundled(language)) "loading.error.bundled" else "loading.error.server"
                )
            }
            _languageDownloadState.value = outcome
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        fun verseKey(surahNumber: Int, ayahNumber: Int): String = "$surahNumber:$ayahNumber"
    }
}

private val Bookmark.key: String
    get() = QuranViewModel.verseKey(surahNumber, ayahNumber)

class QuranViewModelFactory(
    private val repository: QuranRepository,
    private val appContext: Context,
    private val onContentReady: (String) -> Unit = {}
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuranViewModel(repository, appContext.applicationContext, onContentReady) as T
    }
}
