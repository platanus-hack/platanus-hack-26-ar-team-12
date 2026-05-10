package com.beto.app.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.flow.filterIsInstance

private val INPUT_HEIGHT = 44.dp

/**
 * Bottom sheet del chat unificado.
 *
 *  - Cortina: ocupa ~85% del alto, deja ver la app target arriba (no full screen).
 *  - Color gris oscuro sutil (BetoTheme dark) — no negro puro.
 *  - Quick actions visibles SOLO cuando el input está vacío (gana espacio al chat al escribir).
 *  - Input bar fino, single-line inicial, auto-grow, placeholder "Chateá con Beto".
 *  - Mic circular cuando el campo está vacío; cambia a Send cuando hay texto.
 *  - Durante grabación: barras finas reactivas al RMS + botón Stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSheet(
    viewModel: CompanionViewModel,
    onDismiss: () -> Unit,
    contactRepository: com.beto.app.contacts.ContactRepository,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isCapturing by viewModel.isCapturingVoice.collectAsState()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current

    // Hoisteamos el estado del input para que el sheet (quick actions) reaccione.
    var inputText by rememberSaveable { mutableStateOf("") }
    val hasText = inputText.isNotBlank()

    // Cortina: deja ~15% de la pantalla visible arriba (la app target queda parcialmente visible).
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetMaxHeight = (screenHeight.value * 0.85f).dp

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            HeaderRow(onForget = viewModel::forgetSession, hasMessages = messages.isNotEmpty())
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 420.dp),
            ) {
                if (messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { msg -> MessageRow(message = msg) }
                    }
                }
            }

            ThinkingIndicator(isThinking)

            // Quick actions: visibles SOLO si el input está vacío y no estamos grabando/pensando.
            // Al escribir, dejan paso al input — el chat se siente más limpio.
            if (!hasText && !isCapturing) {
                QuickActionsColumn(
                    onActionTap = { it.launch(ctx) },
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 6.dp)
                        .widthIn(max = 200.dp),
                )
            }

            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = { out ->
                    viewModel.sendUserText(out)
                    inputText = ""
                },
                onMicTap = viewModel::startVoiceInput,
                onStopTap = viewModel::stopVoiceInput,
                isCapturing = isCapturing,
                isBusy = isThinking,
            )

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun HeaderRow(onForget: () -> Unit, hasMessages: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Hablemos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (hasMessages) {
            TextButton(onClick = onForget) {
                Text(
                    text = "Borrar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Tocá el micrófono o escribí.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun ThinkingIndicator(isThinking: Boolean) {
    if (!isThinking) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Pensándolo…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun MessageRow(message: CompanionMessage) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = message.text, color = textColor, fontSize = 15.sp)
        }
    }
}

/**
 * Input bar:
 *  - Single-line inicial, crece con el contenido (max 5 líneas).
 *  - Mic circular a la derecha cuando el campo está vacío.
 *  - Cuando hay texto, el botón se vuelve "Send" (➤) y el envío queda al frente.
 *  - Mientras `isCapturing == true` reemplazamos el campo por el visualizer reactivo.
 */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onMicTap: () -> Unit,
    onStopTap: () -> Unit,
    isCapturing: Boolean,
    isBusy: Boolean,
) {
    if (isCapturing) {
        VoiceCaptureRow(onStopTap = onStopTap)
        return
    }
    val hasText = text.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = INPUT_HEIGHT),
            placeholder = {
                Text(
                    text = "Chateá con Beto",
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            shape = RoundedCornerShape(22.dp),
            singleLine = false,
            maxLines = 5,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    val out = text.trim()
                    if (out.isNotEmpty()) onSend(out)
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        // Botón circular: send (cuando hay texto) o mic (cuando está vacío).
        Box(
            modifier = Modifier
                .size(INPUT_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(
                    if (hasText) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(50),
                )
                .clickable(enabled = !isBusy) {
                    if (hasText) {
                        onSend(text.trim())
                    } else {
                        onMicTap()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (hasText) {
                Text(text = "➤", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                MicGlyph(color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Glyph del micrófono dibujado en Canvas — outline-only, matchea estética dark. */
@Composable
private fun MicGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val capsuleW = w * 0.45f
        val capsuleH = h * 0.55f
        val capsuleX = (w - capsuleW) / 2f
        val capsuleY = h * 0.05f
        // Capsule (cuerpo del mic)
        drawRoundRect(
            color = color,
            topLeft = Offset(capsuleX, capsuleY),
            size = androidx.compose.ui.geometry.Size(capsuleW, capsuleH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleW / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density),
        )
        // Arco de soporte
        val arcLeft = w * 0.22f
        val arcTop = h * 0.45f
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(arcLeft, arcTop),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.30f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density),
        )
        // Tronco
        val stemX = w / 2f
        drawLine(
            color = color,
            start = Offset(stemX, h * 0.75f),
            end = Offset(stemX, h * 0.90f),
            strokeWidth = 1.5f * density,
            cap = StrokeCap.Round,
        )
        // Base
        drawLine(
            color = color,
            start = Offset(w * 0.30f, h * 0.92f),
            end = Offset(w * 0.70f, h * 0.92f),
            strokeWidth = 1.5f * density,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Fila visible mientras `isCapturing == true`:
 *  - Barras animadas FINAS que reaccionan al RMS real del SpeechRecognizer (vía bus event).
 *  - Botón circular Stop al lado derecho (mismo diametro que el mic).
 */
@Composable
private fun VoiceCaptureRow(onStopTap: () -> Unit) {
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        AgentBus.events
            .filterIsInstance<AgentEvent.SttRmsChanged>()
            .collect { event ->
                // Normalizá al rango 0..1. RMS típico: -2..10 dB. Suavizado simple.
                val normalized = ((event.rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                rmsLevel = rmsLevel * 0.5f + normalized * 0.5f
            }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(INPUT_HEIGHT),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ReactiveBars(amplitude = rmsLevel)
        }

        // Stop button — círculo, mismo diametro que el mic, color rojo discreto.
        Box(
            modifier = Modifier
                .size(INPUT_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(50),
                )
                .clickable(onClick = onStopTap),
            contentAlignment = Alignment.Center,
        ) {
            // Cuadradito chico = stop
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * Visualizer reactivo: barras verticales **finas** y **fijas** cuya altura sigue el RMS.
 * Las barras NO crecen lateralmente — el strokeWidth está fijado en dp y no depende del
 * tamaño del canvas. Solo cambia la altura con la amplitud y un sine offset por barra.
 *
 * `amplitude` viene en 0..1; cada barra tiene un offset de fase para animarse aún cuando el
 * RMS es bajo (idle).
 */
@Composable
private fun ReactiveBars(amplitude: Float) {
    val barColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "bars-idle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val density = LocalDensity.current
    val barStrokePx = with(density) { 2.5.dp.toPx() }
    val gapPx = with(density) { 1.5.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Fijos: width y gap por barra. Calculamos cuántas barras entran centradas.
        val available = size.width
        val perBar = barStrokePx + gapPx
        val barCount = max(8, (available / perBar).toInt())
        val totalUsed = barCount * barStrokePx + (barCount - 1) * gapPx
        val startX = (available - totalUsed) / 2f + barStrokePx / 2f

        val centerY = size.height / 2f
        val maxAmp = size.height * 0.42f
        val idleAmp = 0.16f
        val targetAmp = max(idleAmp, amplitude)

        for (i in 0 until barCount) {
            val phaseOffset = i * 0.45f
            val sineFactor = (sin(phase + phaseOffset) + 1f) / 2f  // 0..1
            val ratio = idleAmp + (targetAmp - idleAmp) * (0.4f + 0.6f * sineFactor)
            val height = maxAmp * min(1f, ratio)
            val x = startX + i * (barStrokePx + gapPx)
            drawLine(
                color = barColor,
                start = Offset(x, centerY - height),
                end = Offset(x, centerY + height),
                strokeWidth = barStrokePx,           // FIJO — no escala con el ancho
                cap = StrokeCap.Round,
            )
        }
    }
}
