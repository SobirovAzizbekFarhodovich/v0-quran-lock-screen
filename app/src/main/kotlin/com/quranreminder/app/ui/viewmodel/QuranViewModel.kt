package com.quranreminder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.data.QuranDataLoader
import com.quranreminder.app.database.Bookmark
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.SurahHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LanguageDownloadState {
    data object Idle : LanguageDownloadState
    data object InProgress : LanguageDownloadState
    data class Success(val language: String) : LanguageDownloadState
    data class Failure(val language: String, val message: String) : LanguageDownloadState
}

class QuranViewModel(private val repository: QuranRepository) : ViewModel() {
    
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

    fun downloadLanguageContent(language: String) {
        if (_languageDownloadState.value == LanguageDownloadState.InProgress) return
        viewModelScope.launch {
            _languageDownloadState.value = LanguageDownloadState.InProgress
            try {
                val verses = withContext(Dispatchers.IO) {
                    QuranDataLoader.loadFromServer(language)
                }
                check(verses.size >= 6236) { "Qur’on tarjimasi to‘liq yuklanmadi" }
                repository.replaceVerses(verses)
                _languageDownloadState.value = LanguageDownloadState.Success(language)
            } catch (error: Exception) {
                _languageDownloadState.value = LanguageDownloadState.Failure(
                    language,
                    error.message ?: "Qur’onni serverdan yuklab bo‘lmadi"
                )
            }
        }
    }

    companion object {
        fun verseKey(surahNumber: Int, ayahNumber: Int): String = "$surahNumber:$ayahNumber"
    }
}

private val Bookmark.key: String
    get() = QuranViewModel.verseKey(surahNumber, ayahNumber)

class QuranViewModelFactory(private val repository: QuranRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuranViewModel(repository) as T
    }
}
