package com.quranreminder.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.recitation.RecitationClient
import com.quranreminder.app.recitation.RecitationResult
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSurfaceCard

@Composable
fun RecitationScreen(navController: NavHostController) {
    val context = LocalContext.current
    val token = remember { SessionManager(context).accessToken.orEmpty() }
    val client = remember(token) { RecitationClient(token) }
    var surah by remember { mutableStateOf("1") }
    var ayah by remember { mutableStateOf("1") }
    var fullSurah by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RecitationResult?>(null) }
    var message by remember { mutableStateOf("Sura va oyatni tanlab, tilovatni boshlang.") }
    fun begin() {
        val surahNumber = surah.toIntOrNull()?.coerceIn(1, 114) ?: 1
        val ayahNumber = if (fullSurah) 0 else ayah.toIntOrNull()?.coerceAtLeast(1) ?: 1
        result = null
        client.start(surahNumber, ayahNumber, { value -> result = value; recording = false; message = "Tahlil tayyor" }, { error -> recording = false; message = error })
        recording = true
        message = "Tinglanmoqda... Tilovatni tugatgach to‘xtating."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else message = "Tilovatni tekshirish uchun mikrofon ruxsati kerak."
    }
    DisposableEffect(Unit) { onDispose { client.close() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NurScreenHeader(
            title = "Tilovat tekshiruvi", eyebrow = "Nur AI · tajvid yordamchisi",
            leading = { NurIconButton(Icons.Default.ArrowBack, "Orqaga", onClick = { navController.popBackStack() }) }
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("AI tilovatingizni tinglab, matn va ehtimoliy xatolarni ko‘rsatadi.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(surah, { surah = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Sura raqami") }, singleLine = true)
            if (!fullSurah) {
                OutlinedTextField(ayah, { ayah = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Oyat raqami") }, singleLine = true)
            }
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("To‘liq surani tekshirish", style = MaterialTheme.typography.titleSmall)
                    Text("Tahlil faqat o‘zingiz To‘xtatish tugmasini bosgach boshlanadi.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = fullSurah, onCheckedChange = { fullSurah = it })
            }
            Button(
                onClick = {
                    if (recording) { client.stop(); message = "Tahlil qilinmoqda..." }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { androidx.compose.material3.Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Text(if (recording) "  To‘xtatish" else "  Tilovatni boshlash") }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            result?.let { value ->
                NurSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Natija: ${value.score}%", style = MaterialTheme.typography.headlineSmall)
                        Text("Eshitilgan matn: ${value.transcript.ifBlank { "Aniq matn topilmadi" }}")
                        if (value.issues.isNotEmpty()) Text("E’tibor beriladigan joylar: ${value.issues.joinToString()}")
                    }
                }
            }
        }
    }
}
