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
 * sale en Dispatchers.IO.
 *
 * Estrategia de modelo (revisada 2026-05-10):
 *  - **Haiku 4.5** por DEFAULT — sub-2s, ideal para `interpret()` que está en el path
 *    crítico de un comando de voz. Sonnet 4.6 (~3-5s) hacía que el bubble pareciera
 *    "no responder" y rompía la UX de demo.
 *  - **Sonnet 4.6** opcional vía parámetro `model` — para usos asincrónicos con
 *    fallback (ScamExplainer, donde el timeout es 1.5s y hay frase canned siempre).
 *
 * Para mejorar resolución de contactos con nombre+apellido (el bug "¿quién es tu Fran
 * Iturain?") la solución NO es el modelo — es el prompt + la red defensiva en el
 * dispatcher (ya implementada).
 */
object AnthropicClientHolder {
    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(BuildConfig.ANTHROPIC_API_KEY)
            .build()
    }

    /** Modelo rápido por default — usado por todo lo que está en el path crítico. */
    val DEFAULT_MODEL: Model = Model.CLAUDE_HAIKU_4_5

    /** Modelo de mayor calidad para tareas asincrónicas tolerantes a latencia. */
    val QUALITY_MODEL: Model = Model.CLAUDE_SONNET_4_6

    suspend fun complete(
        prompt: String,
        system: String? = null,
        maxTokens: Long = 512,
        model: Model = DEFAULT_MODEL,
    ): String = withContext(Dispatchers.IO) {
        val builder = MessageCreateParams.builder()
            .model(model)
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
