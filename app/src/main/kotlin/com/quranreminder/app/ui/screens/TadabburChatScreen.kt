package com.quranreminder.app.ui.screens

import com.quranreminder.app.ui.theme.LocalNavBarPadding
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quranreminder.app.tadabbur.ChatHistoryStore
import com.quranreminder.app.tadabbur.EvidenceType
import com.quranreminder.app.tadabbur.RagChatRepository
import com.quranreminder.app.tadabbur.StoredChatMessage
import com.quranreminder.app.tadabbur.TadabburAnswer
import com.quranreminder.app.tadabbur.TadabburEvidence
import com.quranreminder.app.ui.theme.NurAyahCard
import com.quranreminder.app.ui.theme.NurEmblem
import com.quranreminder.app.ui.theme.NurIconButton
import com.quranreminder.app.ui.theme.NurPill
import com.quranreminder.app.ui.theme.NurScreenHeader
import com.quranreminder.app.ui.theme.NurSurfaceCard
import com.quranreminder.app.ui.theme.nurColors
import com.quranreminder.app.ui.theme.starPath
import com.quranreminder.app.ui.theme.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun TadabburChatScreen(navController: NavHostController, language: String = "uz_latin") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { RagChatRepository(context) }
    val history = remember { ChatHistoryStore(context) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<StoredChatMessage>() }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // Hides the suggestions until the saved conversation is read, so they don't flash.
    var historyLoaded by remember { mutableStateOf(false) }
    // Bumped on "clear" so an answer still in flight doesn't land in the new conversation.
    var conversation by remember { mutableIntStateOf(0) }
    val errorMessage = t("tadabbur.error")
    val listState = rememberLazyListState()

    // A question about an ayah, handed over from the reader's ayah menu.
    LaunchedEffect(com.quranreminder.app.ui.AyahHandoff.askText) {
        com.quranreminder.app.ui.AyahHandoff.askText?.let {
            input = it
            com.quranreminder.app.ui.AyahHandoff.askText = null
        }
    }

    // Past answers come from the device, so they show instantly and offline.
    LaunchedEffect(Unit) {
        messages.addAll(0, history.load())
        historyLoaded = true
        // Jump (not animate) to the latest turn when reopening a long conversation.
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    fun send() {
        val query = input.trim()
        if (query.isBlank() || isLoading || !historyLoaded) return
        input = ""
        messages.add(StoredChatMessage.User(query))
        history.persist(messages.toList())
        isLoading = true
        val sentIn = conversation
        scope.launch {
            val answer = runCatching { repository.answer(query, language) }.getOrElse { error ->
                // Leaving the screen must not save a fake "error" answer into the history.
                if (error is CancellationException) throw error
                TadabburAnswer(
                    intro = errorMessage,
                    evidence = emptyList()
                )
            }
            if (sentIn != conversation) return@launch
            messages.add(StoredChatMessage.Assistant(answer))
            history.persist(messages.toList())
            isLoading = false
        }
    }

    // The gateway has no endpoint to drop the server-side (Redis) history, so only the
    // local copy is cleared.
    fun clearConversation() {
        conversation++
        messages.clear()
        isLoading = false
        history.clear()
    }

    // Keep the newest message (or the typing indicator) in view.
    LaunchedEffect(messages.size, isLoading) {
        val count = listState.layoutInfo.totalItemsCount
        if (messages.isNotEmpty() && count > 0) listState.animateScrollToItem(count - 1)
    }

    // The screen draws under the floating glass bar: the input bar sits above it (navBar).
    // When the keyboard is open it covers the glass bar, so lift by the keyboard instead —
    // whichever is larger, never both. bottomGapPx guards against any outer bottom padding.
    val navBar = LocalNavBarPadding.current
    val density = LocalDensity.current
    var bottomGapPx by remember { mutableIntStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val keyboardLift: Dp = with(density) { (imeBottomPx - bottomGapPx).coerceAtLeast(0).toDp() }
    val inputBottom: Dp = maxOf(navBar, keyboardLift)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coords ->
                val windowHeight = coords.findRootCoordinates().size.height
                bottomGapPx = (windowHeight - coords.boundsInWindow().bottom).toInt().coerceAtLeast(0)
            }
    ) {
        NurScreenHeader(
            title = t("tadabbur.title"),
            eyebrow = t("tadabbur.subtitle"),
            trailing = if (messages.isNotEmpty()) {
                { NurIconButton(Icons.Outlined.DeleteSweep, t("tadabbur.clear"), onClick = { clearConversation() }) }
            } else null
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (messages.isEmpty() && historyLoaded) {
                item {
                    EmptyChatState(onSuggestionClick = { selected ->
                        input = selected
                        send()
                    })
                }
            }
            items(messages) { message ->
                when (message) {
                    is StoredChatMessage.User -> UserBubble(message.text)
                    is StoredChatMessage.Assistant -> AssistantAnswer(message.answer)
                }
            }
            if (isLoading) {
                item { TypingIndicator() }
            }
        }

        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = { send() },
            modifier = Modifier.padding(bottom = inputBottom)
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = nurColors
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.frame.copy(alpha = 0.5f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                placeholder = {
                    Text(
                        t("tadabbur.placeholder"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ornament,
                    unfocusedBorderColor = c.frame,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4
            )
            NurIconButton(
                icon = Icons.Default.Send,
                contentDescription = t("tadabbur.send"),
                onClick = onSend,
                modifier = Modifier.padding(bottom = 2.dp),
                emphasized = true
            )
        }
    }
}

@Composable
private fun EmptyChatState(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf(
        t("tadabbur.suggestion1"),
        t("tadabbur.suggestion2"),
        t("tadabbur.suggestion3"),
        t("tadabbur.suggestion4")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NurEmblem(size = 72.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = t("tadabbur.empty.greeting"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = t("tadabbur.empty.body"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = t("tadabbur.empty.suggestionsLabel").trimEnd(':', ' '),
            style = MaterialTheme.typography.titleSmall,
            color = nurColors.goldText,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
        )
        suggestions.forEach { suggestion ->
            SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val c = nurColors
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, c.frame), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GoldStar(size = 10.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .padding(start = 48.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun AssistantAnswer(answer: TadabburAnswer) {
    val c = nurColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NurSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    NurEmblem(size = 22.dp)
                    Text("Noorain", style = MaterialTheme.typography.labelLarge, color = c.goldText)
                }
                FormattedAnswer(text = answer.intro)
                if (answer.aiInsight.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = t("tadabbur.aiInsight"),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.goldText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = answer.aiInsight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (answer.evidence.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            ) {
                GoldStar(size = 10.dp)
                Text(
                    t("tadabbur.sources"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            answer.evidence.forEach { EvidenceBlock(it) }
        }
    }
}

private val HEADING_EMOJI = listOf("📖", "🤲", "🌙", "🕌", "✨", "🤍")

/**
 * Renders the AI's lightly-markdown-formatted answer (headings, **bold**,
 * "— Reference" citation lines, block quotes, bullet steps) since it arrives
 * as one free-form string from the server, not structured fields.
 */
@Composable
private fun FormattedAnswer(text: String, modifier: Modifier = Modifier) {
    val lines = remember(text) { text.trim().lines() }
    val c = nurColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(6.dp))

                line.startsWith("#") -> Text(
                    text = boldAnnotated(line.trimStart('#', ' ').removeSurrounding("**")),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.goldText,
                    modifier = Modifier.padding(top = 6.dp)
                )

                line.length <= 60 && HEADING_EMOJI.any { line.startsWith(it) } -> Text(
                    text = boldAnnotated(line.removeSurrounding("**")),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.goldText,
                    modifier = Modifier.padding(top = 6.dp)
                )

                line.startsWith("—") -> Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.goldText,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )

                line.startsWith(">") -> Text(
                    text = boldAnnotated(line.trimStart('>', ' ')),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                )

                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    GoldStar(size = 8.dp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = boldAnnotated(line.removePrefix("- ").removePrefix("* ")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                else -> Text(
                    text = boldAnnotated(line),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Turns `**bold**` markers into an AnnotatedString instead of showing them literally. */
private fun boldAnnotated(line: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var rest = line
        while (true) {
            val start = rest.indexOf("**")
            if (start == -1) { append(rest); break }
            val end = rest.indexOf("**", start + 2)
            if (end == -1) { append(rest); break }
            append(rest.substring(0, start))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(rest.substring(start + 2, end))
            pop()
            rest = rest.substring(end + 2)
        }
    }
}

/** A cited source: Quran in the gold-framed ayah card, hadith in a gold-keyline block. */
@Composable
private fun EvidenceBlock(evidence: TadabburEvidence) {
    val reference = remember(evidence.title, evidence.source) {
        listOf(evidence.title, evidence.source).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }
    if (evidence.type == EvidenceType.QURAN) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NurAyahCard(
                arabic = "",
                translation = evidence.text,
                reference = reference,
                label = t("chat.source.quran"),
                modifier = Modifier.fillMaxWidth()
            )
            EvidenceNotes(evidence)
        }
    } else {
        HadithBlock(evidence, reference)
    }
}

@Composable
private fun HadithBlock(evidence: TadabburEvidence, reference: String) {
    var expanded by remember(evidence.title, evidence.text) { mutableStateOf(false) }
    val canExpand = evidence.text.length > 520
    val c = nurColors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, c.frame), shape)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldStar(size = 10.dp)
            Text(t("chat.source.hadith"), style = MaterialTheme.typography.labelMedium, color = c.goldText)
        }
        Text(
            text = evidence.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded || !canExpand) Int.MAX_VALUE else 9,
            overflow = TextOverflow.Ellipsis
        )
        if (canExpand) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = if (expanded) t("tadabbur.showLess") else t("tadabbur.showMore"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (evidence.narrator.isNotBlank()) {
            Text(
                text = "${t("tadabbur.narrator")}${evidence.narrator}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (reference.isNotBlank()) {
            Text(reference, style = MaterialTheme.typography.labelLarge, color = c.goldText)
        }
        EvidenceNotes(evidence, padded = false)
    }
}

/** Topic tags and the AI's note on why this source fits, shown under a source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvidenceNotes(evidence: TadabburEvidence, padded: Boolean = true) {
    val visible = evidence.highlights.filter { it.isNotBlank() }.take(4)
    if (visible.isEmpty() && evidence.aiRelevance.isBlank()) return
    Column(
        modifier = if (padded) Modifier.padding(horizontal = 8.dp) else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (visible.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visible.forEach { NurPill(text = it) }
            }
        }
        if (evidence.aiRelevance.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                GoldStar(size = 8.dp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = evidence.aiRelevance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

/** Three small gold stars pulsing in turn while the assistant prepares an answer. */
@Composable
private fun TypingIndicator() {
    val label = t("tadabbur.analyzing")
    val transition = rememberInfiniteTransition(label = "typing")
    val alphas = (0 until 3).map { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 200)
            ),
            label = "star$index"
        )
    }
    Row(
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NurEmblem(size = 28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            alphas.forEach { a ->
                val value by a
                GoldStar(size = 12.dp, modifier = Modifier.alpha(value))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoldStar(size: Dp, modifier: Modifier = Modifier, color: Color = nurColors.ornament) {
    Canvas(modifier.size(size)) { drawPath(starPath(center, this.size.minDimension / 2f), color) }
}
