package com.quranreminder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.tadabbur.EvidenceType
import com.quranreminder.app.tadabbur.TadabburAnswer
import com.quranreminder.app.tadabbur.TadabburEvidence
import com.quranreminder.app.tadabbur.TadabburRepository
import com.quranreminder.app.tadabbur.RagChatRepository
import com.quranreminder.app.ui.theme.NurColors
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSectionTitle
import com.quranreminder.app.ui.theme.NurSurfaceCard
import kotlinx.coroutines.launch

@Composable
fun TadabburChatScreen(navController: NavHostController, language: String = "uz_latin") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { RagChatRepository(context) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun send() {
        val query = input.trim()
        if (query.isBlank() || isLoading) return
        input = ""
        messages.add(ChatMessage.User(query))
        isLoading = true
        scope.launch {
            val answer = runCatching { repository.answer(query, language) }.getOrElse {
                TadabburAnswer(
                    intro = "Javob tayyorlashda xatolik bo'ldi. Qayta urinib ko'ring.",
                    evidence = emptyList()
                )
            }
            messages.add(ChatMessage.Assistant(answer))
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NurScreenHeader(
            title = "Islomiy AI yordamchi",
            eyebrow = "Nur AI · ishonchli manbalar",
            leading = {
                NurIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Orqaga",
                    onClick = { navController.popBackStack() }
                )
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatState()
                }
            }
            items(messages) { message ->
                when (message) {
                    is ChatMessage.User -> UserBubble(message.text)
                    is ChatMessage.Assistant -> AssistantAnswer(message.answer)
                }
            }
            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NurEmblem(size = 26.dp)
                        Text(
                            text = "AI tahlil qilmoqda...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.67f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Savolingizni yozing...") },
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4
            )
            NurIconButton(
                icon = Icons.Default.Send,
                contentDescription = "Yuborish",
                onClick = { send() },
                modifier = Modifier.size(48.dp),
                emphasized = true
            )
        }
    }
}

@Composable
private fun EmptyChatState() {
    NurSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(NurColors.Ink, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NurEmblem(size = 24.dp, color = NurColors.GoldSoft)
            }
            Text(
                text = "Assalomu alaykum",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Savolingizni yozing — AI Qur'on va hadisdan mos dalillarni topib beradi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .background(NurColors.Gold, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = NurColors.Charcoal
            )
        }
    }
}

@Composable
private fun AssistantAnswer(answer: TadabburAnswer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(NurColors.Ink, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            NurEmblem(size = 18.dp, color = NurColors.GoldSoft)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NurSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = answer.intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (answer.aiInsight.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NurColors.SageSoft, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "AI tavsiyasi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NurColors.Sage
                                )
                                Text(
                                    text = answer.aiInsight,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            answer.evidence.forEach { EvidenceCard(it) }
        }
    }
}

@Composable
private fun EvidenceCard(evidence: TadabburEvidence) {
    var expanded by remember(evidence.title, evidence.text) { mutableStateOf(false) }
    val canExpand = evidence.type == EvidenceType.HADITH && evidence.text.length > 520
    val tone = if (evidence.type == EvidenceType.QURAN) NurColors.Sage else NurColors.Gold

    NurSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NurSectionTitle("${evidence.type.label} · ${evidence.title}")
            HighlightTags(evidence.highlights)
            Text(
                text = evidence.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded || !canExpand) Int.MAX_VALUE else 9,
                overflow = TextOverflow.Ellipsis
            )
            if (canExpand) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) "Kamroq ko'rsatish" else "Ko'proq ko'rsatish",
                        color = tone
                    )
                }
            }
            if (evidence.narrator.isNotBlank()) {
                Text(
                    text = "Roviy: ${evidence.narrator}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            NurPill(
                text = evidence.source,
                tone = NurColors.SageSoft
            )
            if (evidence.aiRelevance.isNotBlank()) {
                Text(
                    text = "✦ ${evidence.aiRelevance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tone,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun HighlightTags(highlights: List<String>) {
    val visible = highlights.filter { it.isNotBlank() }.take(4)
    if (visible.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { NurPill(text = it) }
    }
}

private sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Assistant(val answer: TadabburAnswer) : ChatMessage
}
