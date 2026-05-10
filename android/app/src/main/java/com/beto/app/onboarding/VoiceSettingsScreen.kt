package com.beto.app.onboarding

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beto.app.voice.TtsManager
import com.beto.app.voice.UserVoicePreferences
import com.beto.app.voice.VoiceSelector

/**
 * UI de selección de voz. Muestra:
 *  - La voz actual seleccionada por Beto (auto o manual override).
 *  - Todas las voces es disponibles con score de género (masculino/femenino) y neural.
 *  - Botón "Probar" por voz (habla una frase corta con esa voz).
 *  - Botón "Elegir" — persiste el `name` de la voz como override del usuario.
 *  - Botón "Volver al automático" — borra el override y deja que VoiceSelector elija.
 */
@Composable
fun VoiceSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var currentSelectionName by remember { mutableStateOf<String?>(UserVoicePreferences.savedVoiceName(context)) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        voices = TtsManager.allSpanishVoices()
            .sortedWith(
                compareByDescending<Voice> { VoiceSelector.scoreGender(it.name) }
                    .thenByDescending {
                        VoiceSelector.isNeural(it.name, it.features.orEmpty())
                    }
                    .thenBy { it.name },
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Voz de Beto",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text(text = "Cerrar", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Probá distintas voces y elegí la que más te guste. Beto va a usar esa de acá en adelante.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        if (currentSelectionName != null) {
            CurrentSelectionRow(
                name = currentSelectionName!!,
                onClear = {
                    UserVoicePreferences.clear(context)
                    currentSelectionName = null
                    TtsManager.applyUserPreferenceOrAuto(context)
                    refreshTick++
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        if (voices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No encontré voces en español.",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Instalá una desde Ajustes → Idioma → Voz.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(voices, key = { it.name }) { voice ->
                VoiceRow(
                    voice = voice,
                    isSelected = currentSelectionName == voice.name,
                    onTry = { TtsManager.previewVoice(voice, "Hola, soy Beto. Estoy acá para ayudarte.") },
                    onChoose = {
                        UserVoicePreferences.save(context, voice.name)
                        currentSelectionName = voice.name
                        TtsManager.applyUserPreferenceOrAuto(context)
                    },
                )
            }
        }
    }
}

@Composable
private fun CurrentSelectionRow(name: String, onClear: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voz elegida",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onClear) {
                Text(text = "Volver al auto", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: Voice,
    isSelected: Boolean,
    onTry: () -> Unit,
    onChoose: () -> Unit,
) {
    val genderScore = VoiceSelector.scoreGender(voice.name)
    val isNeural = VoiceSelector.isNeural(voice.name, voice.features.orEmpty())
    val genderLabel = when {
        genderScore >= 80 -> "Masculina"
        genderScore <= 10 -> "Femenina"
        else -> "—"
    }
    val genderColor = when {
        genderScore >= 80 -> Color(0xFF1565C0)
        genderScore <= 10 -> Color(0xFFAD1457)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp),
                    )
                } else Modifier,
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(text = voice.locale?.toLanguageTag() ?: "—",
                            background = MaterialTheme.colorScheme.surfaceVariant,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Tag(text = genderLabel, background = genderColor.copy(alpha = 0.15f), textColor = genderColor)
                        if (isNeural) {
                            Spacer(Modifier.width(6.dp))
                            Tag(text = "Neural", background = Color(0xFF388E3C).copy(alpha = 0.15f), textColor = Color(0xFF1B5E20))
                        }
                        if (voice.isNetworkConnectionRequired) {
                            Spacer(Modifier.width(6.dp))
                            Tag(text = "Cloud", background = Color(0xFF8E24AA).copy(alpha = 0.15f), textColor = Color(0xFF6A1B9A))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTry, modifier = Modifier.weight(1f)) {
                    Text(text = "Probar", fontSize = 14.sp)
                }
                Button(
                    onClick = onChoose,
                    modifier = Modifier.weight(1f),
                    enabled = !isSelected,
                ) {
                    Text(text = if (isSelected) "Elegida" else "Elegir", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}
