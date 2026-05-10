package com.beto.app.scam

/**
 * Catálogo curado de detectors. Operan sobre texto YA normalizado por el engine
 * (lowercase + sin tildes), por eso los patterns asumen "cambie" no "cambié".
 *
 * Reglas de diseño:
 * - Conservador antes que agresivo: una señal espuria genera ruido y rompe la calibración
 *   de "1 ruido / 2 sospechoso / 3 patrón" del pitch.
 * - Word-boundary `\b` siempre que aplique, para no matchear substrings.
 * - Cada Signal puede tener varias regex en OR; basta una para emitir el hit.
 */
internal object SignalDetectors {

    private fun rx(vararg patterns: String): List<Regex> = patterns.map { Regex(it) }

    val DEFAULT: List<SignalDetector> = listOf(
        RegexSignalDetector(
            Signal.URGENCY,
            rx(
                "\\burgent[ae]s?\\b",
                "\\burge\\b",
                "\\bapurate\\b",
                "\\bya mismo\\b",
                "\\bahora mismo\\b",
                "\\bcuanto antes\\b",
                "\\bantes del? (hoy|manana|viernes|lunes|martes|miercoles|jueves|sabado|domingo|fin de mes|que cierre|cierre)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.MONEY_REQUEST,
            rx(
                "\\btransfer[a-z]+\\b",
                "\\b(pasa|manda)me? (la )?plata\\b",
                "\\bnecesito (la )?plata\\b",
                "\\bdepositame?\\b",
                "\\bcbu\\b",
                "\\bcvu\\b",
                "\\balias\\b",
                "\\bmercado ?pago\\b",
                "\\$\\s*\\d+",
                "\\b\\d{1,4} ?(mil|lucas|gambas|palos)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.CODE_REQUEST,
            rx(
                "\\b(pasa|manda|deci)me (el )?(codigo|sms|pin|otp|token)\\b",
                "\\bcodigo de (verificacion|seguridad|confirmacion)\\b",
                "\\b(el )?(codigo|sms|pin) que (te|me) lleg[oa]\\b",
                "\\bclave de (un solo uso|seguridad)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.NEW_NUMBER,
            rx(
                "\\bcambie de numero\\b",
                "\\bcambie mi numero\\b",
                "\\beste es mi (nuevo )?numero\\b",
                "\\bguarda(me)? (este |el )?numero (nuevo)?\\b",
                "\\bmi numero (nuevo|actualizado)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.SECRECY,
            rx(
                "\\bno le (digas|cuentes|avises)\\b",
                "\\bque no se entere\\b",
                "\\bes (un )?secreto\\b",
                "\\bsin que (sepa|se entere)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.REMOTE_CONTROL,
            rx(
                "\\banydesk\\b",
                "\\bteamviewer\\b",
                "\\bquick ?support\\b",
                "\\bcontrol remoto\\b",
                "\\binstal[a-z]* (la |esta )?app de (soporte|asistencia|control)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.SUSPICIOUS_LINK,
            rx(
                "\\bbit\\.ly/\\S+",
                "\\btinyurl\\.com/\\S+",
                "\\bcutt\\.ly/\\S+",
                "\\bow\\.ly/\\S+",
                "\\bt\\.co/\\S+",
                "\\bgoo\\.gl/\\S+",
                "\\brebrand\\.ly/\\S+",
                "\\bhttp://\\S+",
            ),
        ),
        RegexSignalDetector(
            Signal.IMPERSONATION_FAMILY,
            rx(
                "\\bsoy yo\\b",
                "\\bsoy tu (nieto|nieta|hijo|hija|sobrino|sobrina|primo|prima)\\b",
                "\\bno me reconoc[ei]s\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.AUTHORITY_IMPERSONATION,
            rx(
                "\\bafip\\b",
                "\\banses\\b",
                "\\bbanco central\\b",
                "\\bbcra\\b",
                "\\bministerio de\\b",
                "\\bpoder judicial\\b",
                "\\btribunal\\b",
                "\\bpolicia (federal|nacional)\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.PRIZE_BAIT,
            rx(
                "\\bganaste un\\b",
                "\\b(un )?premio (de|para)\\b",
                "\\bsorteo (de|ganador|ganaste)\\b",
                "\\bganador del? sorteo\\b",
                "\\bloteria nacional\\b",
            ),
        ),
        RegexSignalDetector(
            Signal.THREAT,
            rx(
                "\\bembargo\\b",
                "\\bsera suspendid[ao]\\b",
                "\\bsuspension de (tu |la )?cuenta\\b",
                "\\bdenuncia (penal|por)\\b",
                "\\bvas a perder (tu|la|el)\\b",
                "\\bbloqueo de (tu |la |el )?cuenta\\b",
            ),
        ),
    )
}
