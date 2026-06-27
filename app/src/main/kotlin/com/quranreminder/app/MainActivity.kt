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
import com.quranreminder.app.ui.screens.HomeScreen
import com.quranreminder.app.ui.screens.LockScreenPreviewScreen
import com.quranreminder.app.ui.screens.RemindersScreen
import com.quranreminder.app.ui.screens.SearchScreen
import com.quranreminder.app.ui.screens.SettingsScreen
import com.quranreminder.app.ui.screens.SurahDetailScreen
import com.quranreminder.app.ui.screens.TadabburChatScreen
import com.quranreminder.app.ui.theme.QuranReminderTheme
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import com.quranreminder.app.ui.viewmodel.QuranViewModelFactory
import com.quranreminder.app.ui.viewmodel.ReminderViewModel
import com.quranreminder.app.ui.viewmodel.ReminderViewModelFactory
import com.quranreminder.app.ui.viewmodel.SettingsViewModel
import com.quranreminder.app.ui.viewmodel.SettingsViewModelFactory
import androidx.lifecycle.lifecycleScope
import com.quranreminder.app.database.DatabaseInitializer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var quranViewModel: QuranViewModel
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repository
        val database = QuranDatabase.getDatabase(this)
        
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
            QuranReminderTheme(darkTheme = settings?.darkMode ?: false) {
                navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    quranViewModel = quranViewModel,
                    reminderViewModel = reminderViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
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
    quranViewModel: QuranViewModel,
    reminderViewModel: ReminderViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(navController = navController, startDestination = "home") {
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
            TadabburChatScreen(navController = navController)
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
