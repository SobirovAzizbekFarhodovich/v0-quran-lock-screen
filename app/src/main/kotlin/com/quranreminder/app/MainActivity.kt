package com.quranreminder.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quranreminder.app.data.QuranRepository
import com.quranreminder.app.database.QuranDatabase
import com.quranreminder.app.lockscreen.DailyAyahScheduler
import com.quranreminder.app.ui.screens.BookmarksScreen
import com.quranreminder.app.ui.screens.LanguageSelectionScreen
import com.quranreminder.app.ui.screens.AuthScreen
import com.quranreminder.app.ui.screens.ForgotPasswordScreen
import com.quranreminder.app.ui.screens.LoadingScreen
import com.quranreminder.app.ui.screens.OnboardingScreen
import com.quranreminder.app.ui.screens.RecitationScreen
import com.quranreminder.app.ui.screens.HomeScreen
import com.quranreminder.app.ui.screens.LockScreenPreviewScreen
import com.quranreminder.app.ui.screens.RemindersScreen
import com.quranreminder.app.ui.screens.SearchScreen
import com.quranreminder.app.ui.screens.SettingsScreen
import com.quranreminder.app.ui.screens.SplashScreen
import com.quranreminder.app.ui.screens.SurahDetailScreen
import com.quranreminder.app.ui.screens.TadabburChatScreen
import com.quranreminder.app.ui.theme.QuranReminderTheme
import com.quranreminder.app.ui.viewmodel.LanguageDownloadState
import com.quranreminder.app.ui.viewmodel.QuranViewModel
import com.quranreminder.app.ui.viewmodel.QuranViewModelFactory
import com.quranreminder.app.ui.viewmodel.ReminderViewModel
import com.quranreminder.app.ui.viewmodel.ReminderViewModelFactory
import com.quranreminder.app.ui.viewmodel.SettingsViewModel
import com.quranreminder.app.ui.viewmodel.SettingsViewModelFactory
import androidx.lifecycle.lifecycleScope
import com.quranreminder.app.database.DatabaseInitializer
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.data.QuranDataLoader
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var quranViewModel: QuranViewModel
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var navController: NavHostController
    private lateinit var sessionManager: SessionManager
    private val startDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Drop the system splash's own fade: NurLogoIntro draws the same mark in the same
            // place, so an instant swap is seamless while a crossfade shows an empty frame.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        // Edge-to-edge: the green header band runs under a transparent status bar with light icons.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

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
            QuranViewModelFactory(repository, applicationContext, sessionManager::markContentReady)
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

        // The start destination is decided exactly once per activity: recomputing it on
        // every settings change would rebuild the NavHost graph and reset the back stack.
        // Purely local checks — startup never waits on (or fails because of) the network.
        lifecycleScope.launch {
            val settings = settingsViewModel.settings.filterNotNull().first()
            val storedVerses = withContext(Dispatchers.IO) {
                runCatching { database.quranVerseDao().getVerseCount() }.getOrDefault(0)
            }
            startDestination.value = when {
                !sessionManager.isSignedIn -> "auth"
                settings.language.isEmpty() -> LANGUAGE_SELECTION_ROUTE
                sessionManager.contentReadyLanguage != settings.language ||
                    storedVerses < QuranDataLoader.EXPECTED_VERSE_COUNT -> LOADING_ROUTE
                // Bundled Uzbek text was updated: reload it once from assets (offline, ~1 s).
                QuranDataLoader.isBundled(settings.language) &&
                    sessionManager.contentVersion < QuranDataLoader.BUNDLED_CONTENT_VERSION -> LOADING_ROUTE
                else -> "home"
            }
        }

        setContent {
            val settings = settingsViewModel.settings.collectAsState().value
            val startDest = startDestination.value
            // The logo intro plays once per cold start (survives rotation), over the app.
            var introDone by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            if (settings != null && startDest != null) {
                QuranReminderTheme(darkTheme = settings.darkMode) {
                    navController = rememberNavController()
                    androidx.compose.runtime.LaunchedEffect(intent) {
                        handleDeepLink(intent)
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.quranreminder.app.ui.theme.LocalAppStrings provides
                            com.quranreminder.app.ui.theme.AppStrings.forLanguage(settings.language)
                    ) {
                        AppNavigation(
                            navController = navController,
                            startDestination = startDest,
                            quranViewModel = quranViewModel,
                            reminderViewModel = reminderViewModel,
                            settingsViewModel = settingsViewModel,
                            sessionManager = sessionManager
                        )
                    }
                }
            } else {
                QuranReminderTheme {
                    SplashScreen()
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !introDone,
                enter = androidx.compose.animation.EnterTransition.None,
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(380)) +
                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(380), targetScale = 1.04f)
            ) {
                QuranReminderTheme(darkTheme = settings?.darkMode ?: false) {
                    com.quranreminder.app.ui.theme.NurLogoIntro(onFinished = { introDone = true })
                }
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
            // Consume the extras: the composition re-runs this on recreation (e.g. after
            // a language change) and would otherwise reopen the same ayah again.
            intent.removeExtra("surah_number")
            intent.removeExtra("ayah_number")
            navController.navigate("surah_detail/$surahNumber?ayah=$ayahNumber")
        }
    }

    private fun scheduleDailyAyahWallpaper() {
        // WorkManager alone is enough here — no foreground service is needed to enqueue work.
        DailyAyahScheduler.schedule(this)
        DailyAyahScheduler.updateToday(this)
        // Re-render once a language switch has replaced the Quran content in Room.
        DailyAyahScheduler.refreshOnContentChange(this, this)
        // Re-arm reminder alarms: a force-stop clears them and sends no broadcast.
        com.quranreminder.app.notification.ReminderScheduler.rescheduleAllAsync(this)
    }
}

private const val LANGUAGE_SELECTION_ROUTE = "language_selection?isFirstRun={isFirstRun}"
private const val LOADING_ROUTE = "loading/{language}"

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
    // A definitive 401 on token refresh signed the user out: route back to sign-in.
    androidx.compose.runtime.LaunchedEffect(navController) {
        SessionManager.sessionExpired.collect {
            navController.navigate("auth") { popUpTo(0) }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Shared liquid-glass geometry: the bar publishes itself, the content layer frosts/refracts.
    val glassState = androidx.compose.runtime.remember { com.quranreminder.app.ui.theme.NurGlassState() }
    androidx.compose.runtime.CompositionLocalProvider(com.quranreminder.app.ui.theme.LocalGlassState provides glassState) {
    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in com.quranreminder.app.ui.theme.NurTopLevelRoutes) {
                com.quranreminder.app.ui.theme.NurNavigationBar(currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { innerPadding ->
    // NavHost is NOT padded: top-level screens draw under the floating glass bar and use
    // LocalNavBarPadding for their bottom content padding (0 on every other route).
    val navBarPadding = if (currentRoute in com.quranreminder.app.ui.theme.NurTopLevelRoutes) {
        innerPadding.calculateBottomPadding()
    } else {
        androidx.compose.ui.unit.Dp(0f)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        com.quranreminder.app.ui.theme.LocalNavBarPadding provides navBarPadding
    ) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .then(com.quranreminder.app.ui.theme.nurGlassBackdropIf(currentRoute in com.quranreminder.app.ui.theme.NurTopLevelRoutes, glassState))
    ) {
        composable("auth") {
            AuthScreen(
                session = sessionManager,
                onForgotPassword = { navController.navigate("forgot_password") }
            ) { user, isNewAccount ->
                // The backend currently seeds a default language for brand-new accounts
                // (never truly blank), so isNewAccount from the button pressed is the
                // primary signal; user.language.isBlank() is kept as a fallback in case
                // that default is ever removed server-side.
                val language = when (user.language) {
                    "uz", "uz_latin" -> "uz_latin"
                    "uz_cyrillic", "ru", "en" -> user.language
                    else -> null
                }
                if (isNewAccount || language == null) {
                    navController.navigate("language_selection") { popUpTo("auth") { inclusive = true } }
                } else {
                    settingsViewModel.setLanguage(language)
                    com.quranreminder.app.lockscreen.LockScreenWallpaperApplier.setLockScreenLanguage(appContext, language)
                    val dest = if (sessionManager.contentReadyLanguage == language) {
                        "home"
                    } else {
                        quranViewModel.downloadLanguageContent(language)
                        "loading/$language"
                    }
                    navController.navigate(dest) { popUpTo("auth") { inclusive = true } }
                }
            }
        }
        composable(
            route = "forgot_password?token={token}",
            arguments = listOf(
                navArgument("token") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            ForgotPasswordScreen(
                session = sessionManager,
                initialToken = token,
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = LANGUAGE_SELECTION_ROUTE,
            arguments = listOf(
                navArgument("isFirstRun") {
                    type = NavType.BoolType
                    defaultValue = true
                }
            )
        ) { backStackEntry ->
            val isFirstRun = backStackEntry.arguments?.getBoolean("isFirstRun") ?: true
            LanguageSelectionScreen(
                viewModel = settingsViewModel,
                onLanguageSelected = { language ->
                    com.quranreminder.app.lockscreen.LockScreenWallpaperApplier.setLockScreenLanguage(appContext, language)
                    DailyAyahScheduler.updateToday(appContext)
                    scope.launch { sessionManager.updateLanguage(language) }
                    // Start the download now — it runs in the background while the user
                    // reads the onboarding slides (first run) or immediately gates (settings change).
                    if (!isFirstRun && sessionManager.contentReadyLanguage == language) {
                        // Re-picked the language that's already stored: nothing to load.
                        navController.popBackStack()
                    } else {
                        quranViewModel.downloadLanguageContent(language)
                        val nextDestination = if (isFirstRun) "onboarding" else "loading/$language"
                        navController.navigate(nextDestination) {
                            popUpTo(LANGUAGE_SELECTION_ROUTE) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("onboarding") {
            val downloadState by quranViewModel.languageDownloadState.collectAsState()
            val language = settingsViewModel.settings.collectAsState().value?.language ?: "uz_latin"
            OnboardingScreen(onFinish = {
                if ((downloadState as? LanguageDownloadState.Success)?.language == language ||
                    sessionManager.contentReadyLanguage == language
                ) {
                    sessionManager.markContentReady(language)
                    navController.navigate("home") { popUpTo(0) }
                } else {
                    navController.navigate("loading/$language") { popUpTo("onboarding") { inclusive = true } }
                }
            })
        }
        composable(
            route = LOADING_ROUTE,
            arguments = listOf(navArgument("language") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            // As the start destination the route carries no concrete argument, so fall
            // back to the saved language (which is what the start destination was built from).
            val settingsLanguage = settingsViewModel.settings.collectAsState().value?.language
            val language = backStackEntry.arguments?.getString("language")
                ?.takeIf { it.isNotBlank() && it != "{language}" }
                ?: settingsLanguage?.takeIf { it.isNotBlank() }
                ?: "uz_latin"
            // A language that is known to work offline: the content currently in the
            // database, else the bundled Uzbek Latin translation.
            val fallback = sessionManager.contentReadyLanguage?.takeIf { it != language }
                ?: listOf("uz_latin", "uz_cyrillic").first { it != language }
            LoadingScreen(
                language = language,
                viewModel = quranViewModel,
                onDownloadComplete = {
                    sessionManager.markContentReady(language)
                    navController.navigate("home") { popUpTo(0) }
                },
                fallbackLanguage = fallback,
                onUseFallback = { fallbackLanguage ->
                    settingsViewModel.setLanguage(fallbackLanguage)
                    com.quranreminder.app.lockscreen.LockScreenWallpaperApplier.setLockScreenLanguage(appContext, fallbackLanguage)
                    DailyAyahScheduler.updateToday(appContext)
                    scope.launch { sessionManager.updateLanguage(fallbackLanguage) }
                    if (sessionManager.contentReadyLanguage == fallbackLanguage) {
                        navController.navigate("home") { popUpTo(0) }
                    } else {
                        quranViewModel.downloadLanguageContent(fallbackLanguage)
                        navController.navigate("loading/$fallbackLanguage") { popUpTo(0) }
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
                viewModel = settingsViewModel,
                session = sessionManager
            )
        }
        composable(
            "lock_preview?surah={surah}&ayah={ayah}",
            arguments = listOf(
                navArgument("surah") { type = NavType.IntType; defaultValue = -1 },
                navArgument("ayah") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            LockScreenPreviewScreen(
                navController = navController,
                surah = entry.arguments?.getInt("surah") ?: -1,
                ayah = entry.arguments?.getInt("ayah") ?: -1
            )
        }
        composable("tadabbur_chat") {
            TadabburChatScreen(
                navController = navController,
                language = settingsViewModel.settings.collectAsState().value?.language ?: "uz_latin"
            )
        }
        composable("recitation") {
            RecitationScreen(navController, quranViewModel)
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
    }
    }
}
