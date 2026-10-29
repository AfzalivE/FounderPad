package com.startupassistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val UserBubble = RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp)
private val AgentBubble = RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AskScreen(modifier: Modifier = Modifier, vm: AgentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val empty = state.items.isEmpty() && state.streaming.isEmpty()
    val thinking = state.busy && state.streaming.isEmpty()

    LaunchedEffect(state.items.size, state.streaming, thinking) {
        val count = state.items.size + (if (state.streaming.isNotEmpty()) 1 else 0) + (if (thinking) 1 else 0)
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (empty) {
                item(key = "hero") { Hero(Modifier.animateItem()) }
                itemsIndexed(suggestions, key = { _, s -> s.text }) { i, s ->
                    SuggestionCard(s, onClick = { send(s.text) }, modifier = Modifier.animateItem().enterStagger(i + 2))
                }
            }
            itemsIndexed(state.items) { _, item ->
                ChatRow(item, Modifier.animateItem().enterStagger(0))
            }
            if (state.streaming.isNotEmpty()) {
                item(key = "streaming") {
                    Bubble(state.streaming, fromUser = false, modifier = Modifier.animateItem())
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

        Composer(
            value = input,
            onValueChange = { input = it },
            canSend = input.isNotBlank() && !state.busy,
            onSend = { send(input) },
        )
    }

    state.confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirmation(false) },
            icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
            title = { Text("Record this transaction?") },
            text = {
                Text(
                    pending.args.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                )
            },
            confirmButton = { TextButton(onClick = { vm.answerConfirmation(true) }) { Text("Record") } },
            dismissButton = { TextButton(onClick = { vm.answerConfirmation(false) }) { Text("Cancel") } },
        )
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
private fun SuggestionCard(s: Suggestion, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
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
        is ChatItem.ToolCall -> ToolPill(Icons.Filled.Build, "Using ${item.name}", done = false, modifier = modifier)
        is ChatItem.ToolDone -> ToolPill(Icons.Filled.CheckCircle, "Got results from ${item.name}", done = true, modifier = modifier)
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

@Composable
private fun ToolPill(icon: ImageVector, text: String, done: Boolean, modifier: Modifier = Modifier) {
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
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
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
