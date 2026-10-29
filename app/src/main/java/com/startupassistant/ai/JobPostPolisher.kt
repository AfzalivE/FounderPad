package com.startupassistant.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Rewrites job post text on device with the ML Kit GenAI Rewriting API
 * (Gemini Nano). The text never leaves the phone.
 */
class JobPostPolisher(context: Context) : AutoCloseable {

    enum class Style(val label: String, val outputType: Int) {
        PROFESSIONAL("Professional", RewriterOptions.OutputType.PROFESSIONAL),
        FRIENDLY("Friendly", RewriterOptions.OutputType.FRIENDLY),
        SHORTEN("Shorter", RewriterOptions.OutputType.SHORTEN),
    }

    private val appContext = context.applicationContext
    private val clients = mutableMapOf<Style, Rewriter>()

    private fun client(style: Style) = clients.getOrPut(style) {
        Rewriting.getClient(
            RewriterOptions.builder(appContext)
                .setOutputType(style.outputType)
                .setLanguage(RewriterOptions.Language.ENGLISH)
                .build(),
        )
    }

    /** Returns the rewritten text. Throws [GenAiException] if the feature is unavailable. */
    suspend fun polish(text: String, style: Style): String {
        val rewriter = client(style)
        when (rewriter.checkFeatureStatus().await()) {
            FeatureStatus.UNAVAILABLE -> throw IllegalStateException("Rewriting is not available on this device.")
            FeatureStatus.DOWNLOADABLE -> rewriter.download()
            else -> Unit // DOWNLOADING or AVAILABLE: the request waits for the model.
        }
        val results = rewriter.runInference(RewritingRequest.builder(text).build()).await().results
        return results.firstOrNull()?.text ?: text
    }

    private suspend fun Rewriter.download() = suspendCancellableCoroutine { cont ->
        downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) = Unit
            override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
            override fun onDownloadCompleted() = cont.resume(Unit)
            override fun onDownloadFailed(e: GenAiException) = cont.resumeWithException(e)
        })
    }

    override fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
