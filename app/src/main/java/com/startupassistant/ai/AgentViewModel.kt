package com.startupassistant.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.agent.Agent
import com.google.mlkit.genai.agent.AgentEvent
import com.google.mlkit.genai.agent.AgentRunner
import com.google.mlkit.genai.agent.AgentSession
import com.google.mlkit.genai.agent.AgentTool
import com.google.mlkit.genai.agent.AppFunctionAgentTools
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import android.util.Log
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    /** Progress note shown while busy, for example a model download. */
    val status: String? = null,
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
        return AgentRunner(agent, functionCallingModel()).also { runner = it }
            .getOrCreateSession("founder_chat")
            .also { session = it }
    }

    /**
     * The default Gemini Nano model may not support tools ("Function calling
     * requested but feature parameters do not support capability"), so pick the
     * first model on this device that reports FUNCTION_CALLING. Preview models
     * need the AICore Developer Preview and may still have to download; AICore
     * doesn't answer feature queries for a model until it is on the device, so
     * download it first and show progress.
     */
    private suspend fun functionCallingModel(): GenerativeModel {
        val candidates = listOf(
            ModelReleaseStage.STABLE to ModelPreference.FULL,
            ModelReleaseStage.STABLE to ModelPreference.FAST,
            ModelReleaseStage.PREVIEW to ModelPreference.FULL,
            ModelReleaseStage.PREVIEW to ModelPreference.FAST,
        )
        for ((stage, preference) in candidates) {
            val model = Generation.getClient(
                generationConfig {
                    modelConfig = modelConfig {
                        releaseStage = stage
                        this.preference = preference
                    }
                },
            )
            val label = "stage=$stage preference=$preference"
            val features = try {
                val status = withTimeoutOrNull(PROBE_TIMEOUT_MS) { model.checkStatus() }
                Log.i(TAG, "Model $label status=$status")
                when (status) {
                    FeatureStatus.AVAILABLE -> withTimeoutOrNull(PROBE_TIMEOUT_MS) { model.getAvailableFeatures() }
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                        if (download(model) && waitUntilAvailable(model)) {
                            withTimeoutOrNull(PROBE_TIMEOUT_MS) { model.getAvailableFeatures() }
                        } else {
                            null
                        }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model $label unavailable", e)
                null
            }
            Log.i(TAG, "Model $label features=$features")
            if (features != null && Feature.FUNCTION_CALLING in features) return model
            model.close()
        }
        throw NoFunctionCallingModel()
    }

    /** Downloads [model] (or waits for a running download), reporting progress in the UI. */
    private suspend fun download(model: GenerativeModel): Boolean {
        var total = 0L
        setStatus("Downloading the Gemini Nano model. This happens once.")
        try {
            model.download().collect { status ->
                Log.i(TAG, "Download $status")
                when (status) {
                    is DownloadStatus.DownloadStarted -> total = status.bytesToDownload
                    is DownloadStatus.DownloadProgress -> setStatus(
                        if (total > 0) "Downloading the Gemini Nano model: ${status.totalBytesDownloaded * 100 / total}%"
                        else "Downloading the Gemini Nano model: ${status.totalBytesDownloaded / 1_000_000} MB",
                    )
                    is DownloadStatus.DownloadFailed -> throw status.e
                    is DownloadStatus.DownloadCompleted -> Unit
                }
            }
            return true
        } finally {
            setStatus(null)
        }
    }

    /**
     * download() can finish while AICore is still fetching the model in the
     * background (it returns right away when a download is already running),
     * so poll until the model is ready.
     */
    private suspend fun waitUntilAvailable(model: GenerativeModel): Boolean {
        setStatus("Waiting for Gemini Nano to finish downloading. Keep the phone on Wi-Fi.")
        try {
            return withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                while (true) {
                    val status = model.checkStatus()
                    Log.i(TAG, "Waiting for model, status=$status")
                    if (status == FeatureStatus.AVAILABLE) return@withTimeoutOrNull true
                    if (status == FeatureStatus.UNAVAILABLE) return@withTimeoutOrNull false
                    delay(POLL_INTERVAL_MS)
                }
                @Suppress("UNREACHABLE_CODE") false
            } ?: false
        } finally {
            setStatus(null)
        }
    }

    private fun setStatus(text: String?) = _state.update { it.copy(status = text) }

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

    private fun describe(e: Throwable): String = if (e is NoFunctionCallingModel) {
        "The Gemini Nano model on this device does not support tools yet. Join the AICore Developer Preview to get a model that does. The Events, Hiring and Books tabs still work."
    } else when ((e as? GenAiException)?.errorCode) {
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

private const val TAG = "AgentViewModel"
private const val PROBE_TIMEOUT_MS = 10_000L
private const val POLL_INTERVAL_MS = 3_000L
private const val DOWNLOAD_TIMEOUT_MS = 30 * 60_000L

private class NoFunctionCallingModel : Exception("No on-device model supports function calling")

/** "com.startupassistant.StartupTools#find_events" -> "find_events" */
internal fun shortName(name: String) = name.substringAfterLast('#').substringAfterLast('.')
