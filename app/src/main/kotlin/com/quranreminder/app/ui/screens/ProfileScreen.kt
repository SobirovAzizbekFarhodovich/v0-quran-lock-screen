package com.quranreminder.app.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.quranreminder.app.profile.Profile
import com.quranreminder.app.profile.ProfileRepository
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurDivider
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** The user's photo in a gold ring, or their initial on a green medallion. */
@Composable
fun NurAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by ProfileRepository.avatarVersion.collectAsState()
    val image by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) {
            val file = ProfileRepository.avatarFile(context)
            if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(if (size > 60.dp) 3.dp else 1.5.dp, nurColors.ornament, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            if (initial != null) {
                Text(
                    initial,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp
                )
            } else {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.5f))
            }
        }
    }
}

@Composable
fun ProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by ProfileRepository.profile.collectAsState()
    LaunchedEffect(Unit) {
        ProfileRepository.load(context)
        ProfileRepository.refresh(context)
    }
    val current = profile ?: Profile()

    // Editable copy; reset whenever a fresh profile arrives and nothing has been edited.
    var name by rememberSaveable { mutableStateOf("") }
    var birthYear by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var goal by rememberSaveable { mutableStateOf<String?>(null) }
    var edited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!edited) {
            name = p.name
            birthYear = p.birthYear?.toString().orEmpty()
            gender = p.gender
            level = p.quranLevel
            goal = p.goal
        }
    }

    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val savedText = t("profile.saved")
    val saveFailedText = t("profile.saveFailed")
    val photoFailedText = t("profile.photo.failed")

    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val yearValue = birthYear.toIntOrNull()
    val yearValid = birthYear.isBlank() || (yearValue != null && yearValue in 1900..thisYear)
    val nameValid = name.trim().isNotEmpty()
    val dirty = name.trim() != current.name || (yearValue ?: -1) != (current.birthYear ?: -1) ||
        gender != current.gender || level != current.quranLevel || goal != current.goal

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            ProfileRepository.uploadAvatar(context, uri).onFailure {
                Toast.makeText(context, photoFailedText, Toast.LENGTH_LONG).show()
            }
            uploading = false
        }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun save() {
        if (!nameValid || !yearValid) return
        saving = true
        val fields = linkedMapOf<String, Any?>(
            "name" to name.trim(),
            "birth_year" to yearValue,
            "gender" to gender,
            "quran_level" to level,
            "goal" to goal
        )
        scope.launch {
            ProfileRepository.update(context, fields)
                .onSuccess {
                    edited = false
                    Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, saveFailedText, Toast.LENGTH_LONG).show() }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The header scrolls away with the form, so small screens keep room for the fields.
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
            NurBand(bottomPadding = 28.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NurIconButton(Icons.Default.ArrowBack, t("common.back"), onClick = { navController.popBackStack() })
                    Text(t("profile.title"), style = MaterialTheme.typography.headlineMedium, color = nurColors.onBand)
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClickLabel = t("profile.photo.change"), role = Role.Button) { pickPhoto() }
                        ) {
                            NurAvatar(name = current.name, size = 112.dp)
                            if (uploading) {
                                Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = nurColors.ornament, strokeWidth = 3.dp)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(nurColors.ornament)
                                .clickable(onClickLabel = t("profile.photo.change"), role = Role.Button) { pickPhoto() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = nurColors.bandDeep, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(current.name.ifBlank { " " }, style = MaterialTheme.typography.titleLarge, color = nurColors.onBand, textAlign = TextAlign.Center)
                    if (current.email.isNotBlank()) {
                        Text(current.email, style = MaterialTheme.typography.bodyMedium, color = nurColors.onBandMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pickPhoto() }, enabled = !uploading) {
                            Text(
                                if (current.avatarUrl == null) t("profile.photo.add") else t("profile.photo.change"),
                                color = nurColors.ornament,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        if (current.avatarUrl != null) {
                            TextButton(onClick = {
                                uploading = true
                                scope.launch {
                                    ProfileRepository.removeAvatar(context).onFailure {
                                        Toast.makeText(context, saveFailedText, Toast.LENGTH_LONG).show()
                                    }
                                    uploading = false
                                }
                            }, enabled = !uploading) {
                                Text(t("profile.photo.remove"), color = nurColors.onBandMuted, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            }
            item { NurSectionTitle(t("profile.section.about"), Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60); edited = true },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("profile.name")) },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            singleLine = true,
                            isError = !nameValid,
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = birthYear,
                            onValueChange = { birthYear = it.filter(Char::isDigit).take(4); edited = true },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("profile.birthYear")) },
                            leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
                            singleLine = true,
                            isError = !yearValid,
                            supportingText = {
                                when {
                                    !yearValid -> Text(t("profile.birthYear.invalid"))
                                    yearValue != null -> Text(t("profile.age").replace("{n}", (thisYear - yearValue).toString()))
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )
                        Text(t("profile.gender"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoicePill(t("profile.gender.male"), gender == "male", Modifier.weight(1f)) { gender = "male"; edited = true }
                            ChoicePill(t("profile.gender.female"), gender == "female", Modifier.weight(1f)) { gender = "female"; edited = true }
                        }
                    }
                }
            }
            item { NurSectionTitle(t("profile.section.quran"), Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
            item {
                NurSurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.selectableGroup()) {
                        Text(t("profile.level"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                        listOf("beginner", "fluent", "hafiz").forEach { key ->
                            ChoiceRow(t("profile.level.$key"), t("profile.level.$key.desc"), level == key) { level = key; edited = true }
                        }
                        NurDivider()
                        Text(t("profile.goal"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                        listOf("memorize", "understand", "learn").forEach { key ->
                            ChoiceRow(t("profile.goal.$key"), null, goal == key) { goal = key; edited = true }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item {
                Text(
                    t("profile.privacy"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                NurPrimaryButton(
                    text = t("profile.save"),
                    onClick = { save() },
                    enabled = dirty && nameValid && yearValid,
                    loading = saving,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
