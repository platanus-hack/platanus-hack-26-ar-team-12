package com.beto.app.llm

import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Router que conecta con Gemini (via Firebase AI Logic) para clasificación de intenciones.
 * (LLM-01) Phase 3 implementará la lógica real del SDK.
 */
class LLMRouter {

    /**
     * Clasifica el texto usando el modelo remoto.
     * Retorna el tool name y sus argumentos.
     */
    suspend fun route(text: String): OfflineIntentClassifier.ClassifiedIntent? {
        Timber.tag(LogTags.LLM).i("Ruteando con LLM: %s", text)
        
        // TODO: Integrar con Firebase AI Logic SDK / Gemini 2.5 Flash.
        // Por ahora, como es un MVP y el OfflineClassifier ya cubre lo básico,
        // esto retorna null para que el sistema sepa que no pudo resolverlo remotamente tampoco.
        
        return null
    }
}
