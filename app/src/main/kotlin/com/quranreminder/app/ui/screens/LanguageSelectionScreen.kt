package com.quranreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quranreminder.app.ui.theme.NurBand
import com.quranreminder.app.ui.theme.NurLockup
import com.quranreminder.app.ui.theme.NurPrimaryButton
import com.quranreminder.app.ui.theme.NurSheet
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import com.quranreminder.app.ui.viewmodel.SettingsViewModel

/**
 * An app language. [label] is its native name (also used for "Back to %s"); [sample]
 * is a short greeting written in that language and script, so a user can recognise
 * their own script even before reading the name.
 */
private data class AppLanguage(val code: String, val label: String, val sample: String)

private val appLanguages = listOf(
    AppLanguage("uz_latin", "O'zbekcha (lotin)", "Assalomu alaykum"),
    AppLanguage("uz_cyrillic", "Ўзбекча (кирилл)", "Ассалому алайкум"),
    AppLanguage("ru", "Русский", "Ассаляму алейкум"),
    AppLanguage("en", "English", "Peace be upon you")
)

/** Native display name of an app language code (falls back to the code itself). */
fun appLanguageLabel(code: String): String = appLanguages.firstOrNull { it.code == code }?.label ?: code

private val SheetOverlap = 24.dp

@Composable
fun LanguageSelectionScreen(
    viewModel: SettingsViewModel,
    onLanguageSelected: (String) -> Unit
) {
    val savedLanguage = viewModel.settings.collectAsState().value?.language
    var selectedCode by remember(savedLanguage) {
        mutableStateOf(appLanguages.firstOrNull { it.code == savedLanguage }?.code ?: "uz_latin")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurBand(bottomPadding = SheetOverlap + 28.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NurLockup(markSize = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = t("languageSelect.tagline"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = nurColors.onBandMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                // Deliberately not translated: the user has not chosen a language yet.
                Text(
                    text = "Tilni tanlang\nВыберите язык\nChoose language",
                    style = MaterialTheme.typography.titleLarge,
                    color = nurColors.onBand,
                    textAlign = TextAlign.Center
                )
            }
        }

        NurSheet(
            modifier = Modifier
                .weight(1f)
                .offset(y = -SheetOverlap)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                appLanguages.forEach { language ->
                    LanguageChoice(
                        language = language,
                        selected = language.code == selectedCode,
                        onClick = { selectedCode = language.code }
                    )
                }
            }
            Box(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + SheetOverlap)
                    .navigationBarsPadding()
            ) {
                NurPrimaryButton(
                    text = t("languageSelect.continue"),
                    onClick = {
                        viewModel.setLanguage(selectedCode)
                        onLanguageSelected(selectedCode)
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageChoice(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = nurColors
    val shape = MaterialTheme.shapes.medium
    val selectedLabel = t("languageSelect.selected")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (selected) BorderStroke(2.dp, c.ornament) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedLabel }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = language.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = language.sample,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StarCheck(selected)
    }
}

/** Selection mark: an empty star outline, or a filled star medallion carrying a check. */
@Composable
private fun StarCheck(selected: Boolean) {
    val c = nurColors
    val outline = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            val r = size.minDimension / 2f
            val star = starPath(center, r, r * 0.84f)
            if (selected) {
                drawPath(star, fill)
                drawPath(star, c.ornament, style = Stroke(1.5.dp.toPx()))
            } else {
                drawPath(star, outline, style = Stroke(1.5.dp.toPx()))
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
