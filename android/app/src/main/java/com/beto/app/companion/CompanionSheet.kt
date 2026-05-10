package com.beto.app.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState

/**
 * Bottom sheet del Modo Compañero.
 *
 * Layout (UX-01 — todos los textos ≥22sp por BetoTheme.bodyLarge):
 *  - Título "Estoy acá para charlar"
 *  - Lista de mensajes (USER alineados a la derecha, BETO a la izquierda)
 *    + tarjeta inline de confirmación de fact si aplica
 *  - Indicador "pensando..." cuando isThinking
 *  - Botones grandes: "Hablar" (mic), "Olvidar charla" (clear)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSheet(
    viewModel: CompanionViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isCapturing by viewModel.isCapturingVoice.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Estoy acá para charlar",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                if (messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageRow(
                                message = msg,
                                onConfirmFact = { confirmed ->
                                    msg.pendingFactConfirmation?.let {
                                        viewModel.confirmFact(msg.id, it, confirmed)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            ThinkingIndicator(isThinking)

            Spacer(Modifier.height(12.dp))

            ControlsRow(
                onSpeak = viewModel::startVoiceInput,
                onForget = viewModel::forgetSession,
                speakEnabled = !isCapturing && !isThinking,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tocá Hablar y contame algo, dale.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThinkingIndicator(isThinking: Boolean) {
    if (!isThinking) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Pensándolo...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageRow(
    message: CompanionMessage,
    onConfirmFact: (Boolean) -> Unit,
) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = alignment,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
            }
        }

        message.pendingFactConfirmation?.let { fact ->
            Spacer(Modifier.height(6.dp))
            FactConfirmationCard(fact = fact, onConfirmFact = onConfirmFact)
        }
    }
}

@Composable
private fun FactConfirmationCard(
    fact: ProfileFact,
    onConfirmFact: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "¿Querés que me acuerde?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${fact.category}: ${fact.fact}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirmFact(true) },
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("Sí, recordá") }
                OutlinedButton(onClick = { onConfirmFact(false) }) {
                    Text("No hace falta")
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(
    onSpeak: () -> Unit,
    onForget: () -> Unit,
    speakEnabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onSpeak,
            enabled = speakEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Hablar",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedButton(
            onClick = onForget,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Olvidar charla",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

