package com.quranreminder.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.database.Reminder
import com.quranreminder.app.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ReminderViewModel(
    private val repository: QuranRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Single collector for the ViewModel's lifetime; Room re-emits after every write.
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAllReminders()
                .catch { e ->
                    _error.value = e.message
                    _isLoading.value = false
                }
                .collect { reminders ->
                    _reminders.value = reminders
                    _isLoading.value = false
                }
        }
    }

    fun addReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val id = repository.insertReminder(reminder).toInt()
                appContext?.let { ReminderScheduler.schedule(it, reminder.copy(id = id)) }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                repository.updateReminder(reminder)
                // schedule() cancels the alarm when the reminder is disabled.
                appContext?.let { ReminderScheduler.schedule(it, reminder) }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                repository.deleteReminder(reminder)
                appContext?.let { ReminderScheduler.cancel(it, reminder.id) }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Re-arms all alarms, e.g. after the user grants exact-alarm access. */
    fun rescheduleAll() {
        val context = appContext ?: return
        viewModelScope.launch {
            runCatching { ReminderScheduler.rescheduleAll(context) }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class ReminderViewModelFactory(private val repository: QuranRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        // ComponentActivity supplies the Application in its default creation extras.
        val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        return ReminderViewModel(repository, application?.applicationContext) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReminderViewModel(repository) as T
    }
}
