package com.quranreminder.app.notification

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.quranreminder.app.ui.theme.t

/**
 * Compose-side view of the two things reminders depend on: permission to post notifications
 * (runtime on Android 13+) and exact-alarm access (Android 12+). Both are re-checked on resume,
 * since the user changes them in system settings.
 */
class NotificationAccess(
    val canPost: Boolean,
    /** True once we've asked at least once (or no runtime ask exists) — a denial is then final-ish. */
    val askedBefore: Boolean,
    val canScheduleExact: Boolean,
    val requestPermission: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openExactAlarmSettings: () -> Unit
) {
    /** Asks for POST_NOTIFICATIONS if this OS needs a runtime grant and we don't have it. */
    fun ensurePermission() {
        if (!canPost) requestPermission()
    }

    /** Show the "notifications are off — open settings" hint. */
    val shouldShowDeniedHint: Boolean get() = !canPost && askedBefore
}

@Composable
fun rememberNotificationAccess(onExactAccessGranted: () -> Unit = {}): NotificationAccess {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(NotificationManager.canPost(context)) }
    var canExact by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
    var asked by remember { mutableStateOf(wasPermissionRequested(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        markPermissionRequested(context)
        asked = true
        canPost = NotificationManager.canPost(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canPost = NotificationManager.canPost(context)
                val exactNow = ReminderScheduler.canScheduleExact(context)
                if (exactNow && !canExact) onExactAccessGranted()
                canExact = exactNow
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return NotificationAccess(
        canPost = canPost,
        askedBefore = asked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        canScheduleExact = canExact,
        requestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasRuntimePermission(context)
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Pre-13, or granted but blocked in system settings: no dialog can help,
                // so surface the hint with its "open settings" button instead.
                markPermissionRequested(context)
                asked = true
                canPost = NotificationManager.canPost(context)
            }
        },
        openNotificationSettings = { openAppNotificationSettings(context) },
        openExactAlarmSettings = { openExactAlarmSettings(context) }
    )
}

/** Localized "notifications are off" hint with a button to the app's notification settings. */
@Composable
fun NotificationDeniedHint(access: NotificationAccess, modifier: Modifier = Modifier) {
    HintCard(
        text = t("reminders.permission.denied"),
        action = t("reminders.permission.openSettings"),
        onAction = access.openNotificationSettings,
        modifier = modifier
    )
}

/** Localized hint asking for "Alarms & reminders" access (Android 12+ only). */
@Composable
fun ExactAlarmHint(access: NotificationAccess, modifier: Modifier = Modifier) {
    HintCard(
        text = t("reminders.exactAlarm.hint"),
        action = t("reminders.exactAlarm.allow"),
        onAction = access.openExactAlarmSettings,
        modifier = modifier
    )
}

@Composable
fun HintCard(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private const val PERMISSION_PREFS = "notification_permission"
private const val KEY_REQUESTED = "post_notifications_requested"

private fun wasPermissionRequested(context: Context): Boolean =
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUESTED, false)

private fun markPermissionRequested(context: Context) {
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_REQUESTED, true).apply()
}

private fun hasRuntimePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppNotificationSettings(context)
    }
}
