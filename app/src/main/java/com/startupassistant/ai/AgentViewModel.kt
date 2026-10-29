package com.startupassistant.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.agent.Agent
import com.google.mlkit.genai.agent.AgentEvent
import com.google.mlkit.genai.agent.AgentRunner
import com.google.mlkit.genai.agent.AgentSession
import com.google.mlkit.genai.agent.AgentTool
import com.google.mlkit.genai.agent.AppFunctionAgentTools
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Content
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Assistant(val text: String) : ChatItem
    data class ToolCall(val name: String, val args: String) : ChatItem
    data class ToolDone(val name: String) : ChatItem
    data class Error(val text: String) : ChatItem
}

/** A write action the agent wants to run. The user must approve it first. */
data class PendingConfirmation(val toolName: String, val args: Map<String, Any>, val answer: CompletableDeferred<Boolean>)

data class AgentUiState(
    val items: List<ChatItem> = emptyList(),
    val streaming: String = "",
    val busy: Boolean = false,
    val confirmation: PendingConfirmation? = null,
)

private const val INSTRUCTIONS = """
You are Startup Assistant, an on-device helper for people who want to start a company.
You help with three things: finding events (people, funding, office space), hiring the
first team, and keeping the books. Always use a tool to get facts. Never invent events,
salaries, candidates or numbers. Keep answers short: at most five bullet points.
If a tool says the data is sample data, say so once.
To write a job post, first call get_salary_range, then draft_job_post.
Only call add_transaction when the user clearly asks to record money in or out.
"""

/** Tools that change data. The user confirms each call before it runs. */
private val WRITE_TOOLS = setOf("add_transaction")

/**
 * Wraps a tool so that the user approves the call first (human in the loop).
 */
private class ConfirmingTool(
    delegate: AgentTool,
    private val confirm: suspend (toolName: String, args: Map<String, Any>) -> Boolean,
) : AgentTool(delegate) {
    override suspend fun execute(args: Map<String, Any>): Map<String, Any?> {
        if (!confirm(name, args)) {
            return mapOf("status" to "CANCELLED_BY_USER", "message" to "The user declined this action.")
        }
        return checkNotNull(delegate).execute(args)
    }
}

/**
 * Runs the in-app agent on Gemini Nano with ML Kit GenAI. The agent's tools are
 * the app's own AppFunctions (see StartupTools), so the same functions serve the
 * in-app agent and the Gemini app.
 */
class AgentViewModel : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var runner: AgentRunner? = null
    private var session: AgentSession? = null

    private suspend fun session(): AgentSession {
        session?.let { return it }
        val tools = AppFunctionAgentTools.discover().map { tool ->
            if (WRITE_TOOLS.any { tool.name.endsWith(it) }) ConfirmingTool(tool, ::askUser) else tool
        }
        val agent = Agent.builder()
            .setName("StartupAssistant")
            .setInstructions(INSTRUCTIONS.trimIndent())
            .addTools(tools)
            .setMaxTurns(6)
            .build()
        return AgentRunner(agent).also { runner = it }
            .getOrCreateSession("founder_chat")
            .also { session = it }
    }

    private suspend fun askUser(toolName: String, args: Map<String, Any>): Boolean {
        val answer = CompletableDeferred<Boolean>()
        _state.update { it.copy(confirmation = PendingConfirmation(toolName, args, answer)) }
        return answer.await().also { _state.update { it.copy(confirmation = null) } }
    }

    fun answerConfirmation(approved: Boolean) {
        _state.value.confirmation?.answer?.complete(approved)
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.busy) return
        _state.update { it.copy(items = it.items + ChatItem.User(text.trim()), streaming = "", busy = true) }

        viewModelScope.launch {
            val content = Content.builder().text(text.trim()).build()
            try {
                session().runAsync(content)
                    .catch { e -> addItem(ChatItem.Error(describe(e))) }
                    .collect { event ->
                        when (event) {
                            is AgentEvent.TextMessage -> _state.update {
                                it.copy(streaming = if (event.isPartial) it.streaming + event.text else event.text.ifEmpty { it.streaming })
                            }
                            is AgentEvent.FunctionCall -> {
                                flushStreaming()
                                addItem(ChatItem.ToolCall(shortName(event.name), event.args.toString()))
                            }
                            is AgentEvent.FunctionResponse -> addItem(ChatItem.ToolDone(shortName(event.name)))
                            else -> Unit
                        }
                    }
            } catch (e: Exception) {
                addItem(ChatItem.Error(describe(e)))
            }
            flushStreaming()
            _state.update { it.copy(busy = false) }
        }
    }

    private fun addItem(item: ChatItem) = _state.update { it.copy(items = it.items + item) }

    private fun flushStreaming() = _state.update {
        if (it.streaming.isBlank()) it else it.copy(items = it.items + ChatItem.Assistant(it.streaming.trim()), streaming = "")
    }

    private fun describe(e: Throwable): String = when ((e as? GenAiException)?.errorCode) {
        GenAiException.ErrorCode.NOT_AVAILABLE ->
            "On-device AI is not available. This feature needs a device with Gemini Nano (for example Pixel 9 or later) in the AICore Developer Preview. The Events, Hiring and Books tabs still work."
        GenAiException.ErrorCode.BUSY -> "On-device AI is busy. Try again in a few seconds."
        GenAiException.ErrorCode.MAX_TURNS_EXCEEDED -> "The agent took too many steps. Try a more specific question."
        else -> e.localizedMessage ?: "Agent error"
    }

    override fun onCleared() {
        _state.value.confirmation?.answer?.complete(false)
        session?.close()
        runner?.close()
    }
}

/** "com.startupassistant.StartupTools#find_events" -> "find_events" */
internal fun shortName(name: String) = name.substringAfterLast('#').substringAfterLast('.')
