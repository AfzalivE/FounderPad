package com.startupassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.startupassistant.ai.AgentViewModel
import com.startupassistant.ai.ChatItem

private val suggestions = listOf(
    "Find free funding events online",
    "What should I pay a founding engineer in Toronto?",
    "Write a job post for a designer at Acme",
    "How much runway do I have?",
    "Log a $49 Figma bill",
)

@Composable
fun AskScreen(modifier: Modifier = Modifier, vm: AgentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.items.size, state.streaming) {
        val count = state.items.size + if (state.streaming.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize().padding(16.dp).imePadding()) {
        ScreenHeader("Ask", "An agent that runs on your phone with Gemini Nano. It uses the app's own tools.")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.items.isEmpty()) {
                item { Text("Try one of these:", style = MaterialTheme.typography.labelLarge) }
                items(suggestions) { s -> SuggestionChip(onClick = { vm.send(s) }, label = { Text(s) }) }
            }
            items(state.items) { item -> ChatRow(item) }
            if (state.streaming.isNotEmpty()) item { Bubble(state.streaming, fromUser = false) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about events, hiring or money") },
                maxLines = 4,
            )
            IconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !state.busy,
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }

    state.confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirmation(false) },
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
private fun ChatRow(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Bubble(item.text, fromUser = true)
        is ChatItem.Assistant -> Bubble(item.text, fromUser = false)
        is ChatItem.ToolCall -> AssistChip(
            onClick = {},
            label = { Text("Using ${item.name}") },
            leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null, Modifier.padding(2.dp)) },
        )
        is ChatItem.ToolDone -> AssistChip(
            onClick = {},
            label = { Text("Got results from ${item.name}") },
            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
            colors = AssistChipDefaults.assistChipColors(),
        )
        is ChatItem.Error -> Text(item.text, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = if (fromUser) 48.dp else 0.dp, end = if (fromUser) 0.dp else 48.dp),
    ) {
        Text(text, Modifier.padding(12.dp))
    }
}
