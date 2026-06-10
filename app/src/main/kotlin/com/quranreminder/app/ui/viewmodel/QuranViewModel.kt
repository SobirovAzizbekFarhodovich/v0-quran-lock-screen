package com.quranreminder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.database.QuranVerse
import com.quranreminder.app.database.SurahHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuranViewModel(private val repository: QuranRepository) : ViewModel() {
    
    private val _surahs = MutableStateFlow<List<SurahHeader>>(emptyList())
    val surahs: StateFlow<List<SurahHeader>> = _surahs.asStateFlow()

    private val _currentSurahVerses = MutableStateFlow<List<QuranVerse>>(emptyList())
    val currentSurahVerses: StateFlow<List<QuranVerse>> = _currentSurahVerses.asStateFlow()

    private val _searchResults = MutableStateFlow<List<QuranVerse>>(emptyList())
    val searchResults: StateFlow<List<QuranVerse>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadSurahs()
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
        viewModelScope.launch {
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
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                repository.searchVerses(query).collect { results ->
                    _searchResults.value = results
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class QuranViewModelFactory(private val repository: QuranRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return QuranViewModel(repository) as T
    }
}
