package com.quranreminder.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.lockscreen.DailyAyahForegroundService
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import com.quranreminder.app.ui.screens.BookmarksScreen
import com.quranreminder.app.ui.screens.LanguageSelectionScreen
import com.quranreminder.app.ui.screens.AuthScreen
import com.quranreminder.app.ui.screens.RecitationScreen
import com.quranreminder.app.ui.screens.HomeScreen
import com.quranreminder.app.ui.screens.LockScreenPreviewScreen
import com.quranreminder.app.ui.screens.RemindersScreen
import com.quranreminder.app.ui.screens.SearchScreen
import com.quranreminder.app.ui.screens.SettingsScreen
import com.quranreminder.app.ui.screens.SurahDetailScreen
import com.quranreminder.app.ui.screens.TadabburChatScreen
import com.quranreminder.app.ui.theme.QuranReminderTheme
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import com.quranreminder.app.ui.viewmodel.QuranViewModelFactory
import com.quranreminder.app.ui.viewmodel.ReminderViewModel
import com.quranreminder.app.ui.viewmodel.ReminderViewModelFactory
import com.quranreminder.app.ui.viewmodel.SettingsViewModel
import com.quranreminder.app.ui.viewmodel.SettingsViewModelFactory
import androidx.lifecycle.lifecycleScope
import com.quranreminder.app.database.DatabaseInitializer
import com.quranreminder.app.auth.SessionManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator

class MainActivity : ComponentActivity() {
    private lateinit var quranViewModel: QuranViewModel
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var navController: NavHostController
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repository
        val database = QuranDatabase.getDatabase(this)
        sessionManager = SessionManager(this)
        
        // Initialize database with sample data on first run
        lifecycleScope.launch {
            DatabaseInitializer(this@MainActivity).initializeDatabase(database)
        }
        
        val repository = QuranRepository(
            database.quranVerseDao(),
            database.reminderDao(),
            database.bookmarkDao()
        )

        // Initialize ViewModels
        quranViewModel = ViewModelProvider(
            this,
            QuranViewModelFactory(repository)
        ).get(QuranViewModel::class.java)

        reminderViewModel = ViewModelProvider(
            this,
            ReminderViewModelFactory(repository)
        ).get(ReminderViewModel::class.java)

        settingsViewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(database.appSettingsDao())
        ).get(SettingsViewModel::class.java)

        scheduleDailyAyahWallpaper()

        setContent {
            val settings = settingsViewModel.settings.collectAsState().value
            
            if (settings != null) {
                val startDest = when {
                    !sessionManager.isSignedIn -> "auth"
                    settings.language.isEmpty() -> "language_selection"
                    else -> "home"
                }
                QuranReminderTheme(darkTheme = settings.darkMode) {
                    navController = rememberNavController()
                    androidx.compose.runtime.LaunchedEffect(intent) {
                        handleDeepLink(intent)
                    }
                    AppNavigation(
                        navController = navController,
                        startDestination = startDest,
                        quranViewModel = quranViewModel,
                        reminderViewModel = reminderViewModel,
                        settingsViewModel = settingsViewModel,
                        sessionManager = sessionManager
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(NurColors.Parchment),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NurColors.Gold)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null || !::navController.isInitialized) return
        val surahNumber = intent.getIntExtra("surah_number", -1)
        val ayahNumber = intent.getIntExtra("ayah_number", -1)
        
        if (surahNumber != -1 && ayahNumber != -1) {
            navController.navigate("surah_detail/$surahNumber?ayah=$ayahNumber")
        }
    }

    private fun scheduleDailyAyahWallpaper() {
        DailyAyahScheduler.schedule(this)
        val intent = Intent(this, DailyAyahForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    quranViewModel: QuranViewModel,
    reminderViewModel: ReminderViewModel,
    settingsViewModel: SettingsViewModel,
    sessionManager: SessionManager
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(session = sessionManager) { user, isNewAccount ->
                if (!isNewAccount && user.language.isNotBlank()) {
                    val language = when (user.language) {
                        "uz" -> "uz_latin"
                        else -> user.language
                    }
                    settingsViewModel.setLanguage(language)
                    com.quranreminder.app.lockscreen.LockScreenWallpaperApplier.setLockScreenLanguage(appContext, language)
                    quranViewModel.downloadSelectedLanguage(language)
                    navController.navigate("home") { popUpTo("auth") { inclusive = true } }
                } else {
                    navController.navigate("language_selection") { popUpTo("auth") { inclusive = true } }
                }
            }
        }
        composable("language_selection") {
            LanguageSelectionScreen(
                viewModel = settingsViewModel,
                onLanguageSelected = { language ->
                    quranViewModel.downloadSelectedLanguage(language)
                    com.quranreminder.app.lockscreen.LockScreenWallpaperApplier.setLockScreenLanguage(appContext, language)
                    DailyAyahScheduler.updateToday(appContext)
                    scope.launch { sessionManager.updateLanguage(language) }
                    navController.navigate("home") {
                        popUpTo("language_selection") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = quranViewModel,
                navController = navController
            )
        }
        composable("search") {
            SearchScreen(
                viewModel = quranViewModel,
                navController = navController
            )
        }
        composable("reminders") {
            RemindersScreen(
                viewModel = reminderViewModel,
                navController = navController
            )
        }
        composable("bookmarks") {
            BookmarksScreen(
                navController = navController,
                quranViewModel = quranViewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                viewModel = settingsViewModel
            )
        }
        composable("lock_preview") {
            LockScreenPreviewScreen(navController = navController)
        }
        composable("tadabbur_chat") {
            TadabburChatScreen(
                navController = navController,
                language = settingsViewModel.settings.collectAsState().value?.language ?: "uz_latin"
            )
        }
        composable("recitation") {
            RecitationScreen(navController)
        }
        composable(
            route = "surah_detail/{surahNumber}?ayah={ayahNumber}",
            arguments = listOf(
                navArgument("ayahNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val surahNumber = backStackEntry.arguments?.getString("surahNumber")?.toIntOrNull() ?: 1
            val ayahNumber = backStackEntry.arguments?.getInt("ayahNumber")?.takeIf { it > 0 }
            SurahDetailScreen(
                surahNumber = surahNumber,
                targetAyahNumber = ayahNumber,
                viewModel = quranViewModel,
                navController = navController
            )
        }
    }
}
