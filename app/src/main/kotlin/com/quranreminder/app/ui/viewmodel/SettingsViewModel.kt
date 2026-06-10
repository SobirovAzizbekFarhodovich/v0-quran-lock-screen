package com.quranreminder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranreminder.app.database.AppSettings
import com.quranreminder.app.database.AppSettingsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsDao: AppSettingsDao) : ViewModel() {
    
    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                settingsDao.getSettings().collect { appSettings ->
                    _settings.value = appSettings
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            try {
                settingsDao.updateSettings(settings)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNotifications() {
        _settings.value?.let { current ->
            updateSettings(current.copy(notificationsEnabled = !current.notificationsEnabled))
        }
    }

    fun toggleVibration() {
        _settings.value?.let { current ->
            updateSettings(current.copy(vibrateOnReminder = !current.vibrateOnReminder))
        }
    }

    fun toggleSound() {
        _settings.value?.let { current ->
            updateSettings(current.copy(soundEnabled = !current.soundEnabled))
        }
    }

    fun toggleDarkMode() {
        _settings.value?.let { current ->
            updateSettings(current.copy(darkMode = !current.darkMode))
        }
    }

    fun toggleLockScreenNotifications() {
        _settings.value?.let { current ->
            updateSettings(current.copy(lockScreenNotifications = !current.lockScreenNotifications))
        }
    }

    fun setFontSize(size: Int) {
        _settings.value?.let { current ->
            updateSettings(current.copy(fontSize = size))
        }
    }
}

class SettingsViewModelFactory(private val settingsDao: AppSettingsDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(settingsDao) as T
    }
}
