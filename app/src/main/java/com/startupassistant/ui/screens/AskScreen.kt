package com.startupassistant.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import com.startupassistant.ai.PendingConfirmation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.startupassistant.ai.AgentViewModel
import com.startupassistant.ai.ChatItem

private data class Suggestion(val text: String, val icon: ImageVector)

private val suggestions = listOf(
    Suggestion("Find free funding events online", Icons.Filled.Event),
    Suggestion("What should I pay a founding engineer in Toronto?", Icons.Filled.Payments),
    Suggestion("Write a job post for a designer at Acme", Icons.Filled.EditNote),
    Suggestion("How much runway do I have?", Icons.Filled.Savings),
    Suggestion("Log a $49 Figma bill", Icons.Filled.Receipt),
)

private data class ToolLabel(val text: String, val icon: ImageVector)

private val toolLabels = mapOf(
    "find_events" to ToolLabel("Searching events", Icons.Filled.Event),
    "get_salary_range" to ToolLabel("Checking salaries", Icons.Filled.Payments),
    "find_candidates" to ToolLabel("Finding candidates", Icons.Filled.PersonSearch),
    "draft_job_post" to ToolLabel("Writing a job post", Icons.Filled.EditNote),
    "get_runway" to ToolLabel("Checking your runway", Icons.Filled.Savings),
    "add_transaction" to ToolLabel("Recording a transaction", Icons.Filled.Receipt),
)

private fun toolLabel(name: String) =
    toolLabels[name] ?: ToolLabel(humanize(name), Icons.Filled.AutoAwesome)

/** "amountUsd" or "amount_usd" -> "Amount usd" */
private fun humanize(key: String): String =
    key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** A chat row as shown: a run of tool calls folds into one step per tool. */
private sealed interface ChatLine {
    data class Item(val item: ChatItem) : ChatLine
    data class ToolStep(val name: String, val done: Boolean) : ChatLine
    data class Result(val result: AgentResult) : ChatLine
}

private fun foldTools(items: List<ChatItem>): List<ChatLine> {
    // Replies that are only function-call markup vanish, so the calls around them form one run.
    val visible = items.mapNotNull { item ->
        if (item !is ChatItem.Assistant) item
        else cleanAgentText(item.text).takeIf { it.isNotBlank() }?.let { ChatItem.Assistant(it) }
    }
    val rows = mutableListOf<ChatLine>()
    var shownResults = false
    var i = 0
    while (i < visible.size) {
        val item = visible[i]
        if (item !is ChatItem.ToolCall && item !is ChatItem.ToolDone) {
            when (item) {
                is ChatItem.User -> { shownResults = false; rows += ChatLine.Item(item) }
                // The cards answer the turn, so its text reply is not shown.
                is ChatItem.Assistant -> if (!shownResults) rows += ChatLine.Item(item)
                else -> rows += ChatLine.Item(item)
            }
            i++
            continue
        }
        var end = i
        while (end < visible.size && (visible[end] is ChatItem.ToolCall || visible[end] is ChatItem.ToolDone)) end++
        val run = visible.subList(i, end)
        val names = run.map { (it as? ChatItem.ToolCall)?.name ?: (it as ChatItem.ToolDone).name }.distinct()
        for (name in names) {
            val calls = run.filterIsInstance<ChatItem.ToolCall>().filter { it.name == name }
            val doneCount = run.count { it is ChatItem.ToolDone && it.name == name }
            val done = doneCount >= calls.size
            rows += ChatLine.ToolStep(name, done)
            if (done) agentResult(name, calls.map { it.args })?.let {
                rows += ChatLine.Result(it)
                shownResults = true
            }
        }
        i = end
    }
    return rows
}

private val toolNames = toolLabels.keys.joinToString("|")
private val markupPatterns = listOf(
    // Fenced tool code or JSON blocks.
    Regex("```(?:tool_code|tool_call|json|python)?\\s*[\\s\\S]*?```"),
    // Tagged function-call markup.
    Regex("<(start_function_call|tool_call|function_call)>[\\s\\S]*?(<(end_function_call|/tool_call|/function_call)>|$)"),
    Regex("<(start|end)_function_(call|response)>|</?escape>|<ctrl\\d+>"),
    // JSON objects that name a tool or carry args.
    Regex("\\{[^{}]*\"(name|function|tool|args|arguments|parameters)\"\\s*:(?:[^{}]|\\{[^{}]*\\})*\\}"),
    // Bare calls such as print(default_api.find_events(query="x")) or call:find_events{...}.
    Regex("(?m)^.*\\b(call:)?(default_api\\.)?($toolNames)\\s*[({].*$"),
)

/** Removes function-call markup the model sometimes writes into its reply. */
private fun cleanAgentText(text: String): String =
    markupPatterns.fold(text) { acc, r -> acc.replace(r, "") }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

private val UserBubble = RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp)
private val AgentBubble = RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AskScreen(modifier: Modifier = Modifier, vm: AgentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val rows = remember(state.items) { foldTools(state.items) }
    // A reply that follows result cards stays hidden, like the stored one.
    val turnHasCards = rows.lastOrNull { it is ChatLine.Result || (it as? ChatLine.Item)?.item is ChatItem.User } is ChatLine.Result
    val streaming = remember(state.streaming, turnHasCards) { if (turnHasCards) "" else cleanAgentText(state.streaming) }
    val empty = state.items.isEmpty() && state.streaming.isEmpty()
    val thinking = state.busy && streaming.isEmpty()

    LaunchedEffect(rows.size, streaming, thinking) {
        val count = rows.size + (if (streaming.isNotEmpty()) 1 else 0) + (if (thinking) 1 else 0)
        if (!empty && count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(text: String) {
        if (text.isBlank() || state.busy) return
        vm.send(text)
        input = ""
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        ScreenHeader("Ask", "An agent that runs on your phone with Gemini Nano.", Icons.Filled.AutoAwesome, TabShapes.ask)
        // Model status, for example while Gemini Nano downloads.
        AnimatedVisibility(state.status != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(state.status.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }

        // The empty state leaves before the chat comes in, so the two never overlap.
        AnimatedContent(
            targetState = empty,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 120)) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically { -it / 8 } + scaleOut(targetScale = 0.96f)))
                    .using(SizeTransform(clip = true))
            },
            label = "askContent",
        ) { isEmpty ->
            if (isEmpty) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hero()
                    suggestions.forEachIndexed { i, s ->
                        // Taps after the first are ignored while that question runs.
                        SuggestionCard(s, enabled = !state.busy, onClick = { send(s.text) }, modifier = Modifier.enterStagger(i + 2))
                    }
                }
            } else {
                ChatList(listState, rows, streaming, thinking)
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            canSend = input.isNotBlank() && !state.busy,
            onSend = { send(input) },
        )
    }

    state.confirmation?.let { pending -> ConfirmDialog(pending, onAnswer = vm::answerConfirmation) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatList(listState: LazyListState, rows: List<ChatLine>, streaming: String, thinking: Boolean) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Index keys let the streaming reply turn into its final row in place.
        itemsIndexed(rows, key = { i, _ -> i }) { _, row ->
            when (row) {
                is ChatLine.Item -> ChatRow(row.item, Modifier.animateItem().enterStagger(0))
                is ChatLine.ToolStep -> ToolPill(row.name, row.done, Modifier.animateItem().enterStagger(0))
                is ChatLine.Result -> AgentResultView(row.result, Modifier.animateItem())
            }
        }
        if (streaming.isNotEmpty()) {
            item(key = rows.size) {
                Bubble(streaming, fromUser = false, modifier = Modifier.animateItem())
            }
        }
        if (thinking) {
            item(key = "thinking") {
                Row(
                    Modifier.animateItem().enterStagger(0).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text("Thinking…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(pending: PendingConfirmation, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
        title = { Text("Record this transaction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pending.args.forEach { (key, value) -> ArgRow(argLabel(key), argValue(key, value)) }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Record") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Cancel") } },
    )
}

private fun argLabel(key: String) = when (key) {
    "amountUsd" -> "Amount"
    else -> humanize(key)
}

private fun argValue(key: String, value: Any): String = when {
    key == "amountUsd" && value is Number -> {
        val amount = value.toDouble()
        (if (amount < 0) "-$" else "$") + "%,.2f".format(kotlin.math.abs(amount))
    }
    value is Double && value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

@Composable
private fun ArgRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Hero(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp).enterStagger(1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(
            Icons.Filled.AutoAwesome,
            TabShapes.ask,
            size = 112.dp,
            spin = true,
            modifier = Modifier.scale(rememberPulse()),
        )
        Text(
            "What do you need today?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            "Ask about events, hiring or money. The agent uses the app's own tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconDot(s.icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            Text(s.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
    val sendScale by animateFloatAsState(
        if (canSend) 1f else 0.85f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sendScale",
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about events, hiring or money") },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.scale(sendScale)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, modifier: Modifier = Modifier) {
    when (item) {
        is ChatItem.User -> Bubble(item.text, fromUser = true, modifier = modifier)
        is ChatItem.Assistant -> Bubble(item.text, fromUser = false, modifier = modifier)
        is ChatItem.ToolCall -> ToolPill(item.name, done = false, modifier = modifier)
        is ChatItem.ToolDone -> ToolPill(item.name, done = true, modifier = modifier)
        is ChatItem.Error -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                Text(item.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolPill(name: String, done: Boolean, modifier: Modifier = Modifier) {
    val label = toolLabel(name)
    Surface(
        color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(label.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label.text, style = MaterialTheme.typography.labelLarge)
            Crossfade(done, label = "toolDone") { finished ->
                if (finished) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", modifier = Modifier.size(18.dp))
                } else {
                    LoadingIndicator(Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = if (fromUser) UserBubble else AgentBubble,
            color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp).animateContentSize(),
        ) {
            Text(
                if (fromUser) AnnotatedString(text) else markdown(text),
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private val boldSpan = Regex("\\*\\*(.+?)\\*\\*")
private val bulletPrefix = Regex("^\\s*[*-]\\s+")

/** Renders the small Markdown subset the model uses: bullets and bold. */
private fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        val line = raw.replace(bulletPrefix, "• ")
        var last = 0
        boldSpan.findAll(line).forEach { m ->
            append(line.substring(last, m.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
            last = m.range.last + 1
        }
        append(line.substring(last))
    }
}

