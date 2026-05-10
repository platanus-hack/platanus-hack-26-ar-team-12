package com.beto.app.guide

/**
 * Scripts curados para Modo Guía (GUIDE-01 / GUIDE-03).
 *
 * Cada `GuideAction` define una secuencia de pasos donde Beto:
 *  1. Localiza el View target en la pantalla (por texto o contentDescription).
 *  2. Dibuja una flecha animada apuntando a sus coordenadas.
 *  3. Lee el paso por TTS.
 *  4. Espera ~3s y pasa al siguiente paso.
 *
 * **Importante:** los selectores son específicos por versión de app. Cuando la app target
 * cambia layout (ej. WhatsApp UI redesign), estos selectores deben actualizarse.
 *
 * Versiones testeadas (al 2026-05-10 / Phase 4-04):
 *  - WhatsApp: cumple con `contentDescription="Mensaje de voz"` en es-AR.
 *  - Cámara/contactos/volumen: usan settings panels nativos del sistema.
 */
enum class GuideAction {
    SEND_WHATSAPP_AUDIO,
    MAKE_VIDEO_CALL,
    ADD_CONTACT,
    INCREASE_VOLUME,
    OPEN_CAMERA,
}

sealed class TargetSelector {
    data class ByText(val text: String) : TargetSelector()
    data class ByContentDescription(val description: String) : TargetSelector()
}

data class GuideStep(
    val target: TargetSelector,
    val voiceText: String,
)

data class GuideScript(
    val action: GuideAction,
    val appPackage: String?,
    val intentKind: IntentKind,
    val steps: List<GuideStep>,
) {
    init {
        require(steps.isNotEmpty()) { "GuideScript $action must have at least one step" }
        steps.forEach { step ->
            require(step.voiceText.isNotBlank()) { "Step voiceText cannot be blank" }
            require(step.voiceText.split(' ').size <= 25) { "Step voiceText too long (>25 words)" }
        }
    }
}

enum class IntentKind {
    /** No abre nada — asume que el user ya está en la app target. */
    NONE,
    /** Abre la app target via Intent.ACTION_MAIN antes del primer step. */
    OPEN_APP,
    /** Abre system settings para el caso de volumen / contactos. */
    OPEN_SYSTEM,
}

object GuideScripts {

    fun forAction(action: GuideAction): GuideScript = SCRIPTS.getValue(action)

    fun all(): Collection<GuideScript> = SCRIPTS.values

    private val SCRIPTS: Map<GuideAction, GuideScript> = mapOf(
        GuideAction.SEND_WHATSAPP_AUDIO to GuideScript(
            action = GuideAction.SEND_WHATSAPP_AUDIO,
            appPackage = "com.whatsapp",
            intentKind = IntentKind.NONE, // asume que el user está en un chat
            steps = listOf(
                GuideStep(
                    target = TargetSelector.ByContentDescription("Mensaje de voz"),
                    voiceText = "Mantené apretado este botón mientras hablás.",
                ),
                GuideStep(
                    target = TargetSelector.ByContentDescription("Mensaje de voz"),
                    voiceText = "Cuando soltés, el audio se manda solo. Probá vos.",
                ),
            ),
        ),
        GuideAction.MAKE_VIDEO_CALL to GuideScript(
            action = GuideAction.MAKE_VIDEO_CALL,
            appPackage = "com.whatsapp",
            intentKind = IntentKind.NONE,
            steps = listOf(
                GuideStep(
                    target = TargetSelector.ByContentDescription("Videollamada"),
                    voiceText = "Tocá este botón para empezar la videollamada.",
                ),
            ),
        ),
        GuideAction.ADD_CONTACT to GuideScript(
            action = GuideAction.ADD_CONTACT,
            appPackage = "com.android.contacts",
            intentKind = IntentKind.OPEN_APP,
            steps = listOf(
                GuideStep(
                    target = TargetSelector.ByText("Crear contacto"),
                    voiceText = "Tocá acá para agregar un contacto nuevo.",
                ),
                GuideStep(
                    target = TargetSelector.ByText("Guardar"),
                    voiceText = "Cuando termines de cargar los datos, tocá Guardar.",
                ),
            ),
        ),
        GuideAction.INCREASE_VOLUME to GuideScript(
            action = GuideAction.INCREASE_VOLUME,
            appPackage = null,
            intentKind = IntentKind.NONE,
            steps = listOf(
                GuideStep(
                    target = TargetSelector.ByContentDescription("Subir volumen"),
                    voiceText = "Apretá el botón de arriba del costado para subir el volumen.",
                ),
            ),
        ),
        GuideAction.OPEN_CAMERA to GuideScript(
            action = GuideAction.OPEN_CAMERA,
            appPackage = "com.android.camera",
            intentKind = IntentKind.OPEN_APP,
            steps = listOf(
                GuideStep(
                    target = TargetSelector.ByContentDescription("Obturador"),
                    voiceText = "Tocá este círculo para sacar la foto.",
                ),
            ),
        ),
    )
}
