package com.beto.app.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.beto.app.BuildConfig
import com.beto.app.util.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Holder único del cliente Anthropic. La SDK java es blocking — toda llamada
 * sale en Dispatchers.IO. Modelo fijo: claude-haiku-4-5 (sub-2s, vision + tools).
 */
object AnthropicClientHolder {
    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(BuildConfig.ANTHROPIC_API_KEY)
            .build()
    }

    suspend fun complete(
        prompt: String,
        system: String? = null,
        maxTokens: Long = 512,
    ): String = withContext(Dispatchers.IO) {
        // Nota: Haiku 4.5 ignora temperature (rechaza valores != 1.0 con 400).
        // Variabilidad por design: el caller varía via system/prompt, no temperature.
        val builder = MessageCreateParams.builder()
            .model(Model.CLAUDE_HAIKU_4_5)
            .maxTokens(maxTokens)
            .addUserMessage(prompt)
        if (!system.isNullOrBlank()) builder.system(system)

        runCatching { client.messages().create(builder.build()) }
            .map { msg ->
                msg.content()
                    .firstOrNull { it.text().isPresent }
                    ?.text()
                    ?.get()
                    ?.text()
                    .orEmpty()
            }
            .getOrElse {
                Timber.tag(LogTags.LLM).w(it, "ANTHROPIC_CALL_FAILED")
                ""
            }
    }
}
