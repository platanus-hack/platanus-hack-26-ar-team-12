package com.beto.app.onboarding

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/**
 * Pantalla de onboarding: lista todos los permisos que Beto necesita con su estado actual
 * (✓ verde si está, ✕ ámbar si falta) y un botón "Activar" que abre directamente la
 * pantalla de Settings correspondiente.
 *
 * El usuario puede revisar de un vistazo qué falta. "Continuar" se habilita cuando todos
 * los permisos críticos están otorgados (los opcionales se pueden saltar).
 */
@Composable
fun OnboardingScreen(
    statuses: Map<String, Boolean>,
    onActivate: (PermissionItem) -> Unit,
    onContinue: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
) {
    val context = LocalContext.current
    val items = remember { BetoPermissions.items }
    val criticalAllOk = items
        .filter { it.critical }
        .all { statuses[it.id] == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Text(
            text = "Hola, soy Beto",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Para ayudarte necesito que actives estos permisos. Tocá \"Activar\" en cada uno.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PermissionRow(
                    item = item,
                    granted = statuses[item.id] == true,
                    onActivate = { onActivate(item) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = onOpenVoiceSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Configurar voz de Beto",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            enabled = criticalAllOk,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = if (criticalAllOk) "Continuar" else "Faltan permisos críticos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
    onActivate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            StatusBadge(granted)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!item.critical) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(opcional)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text(
                    text = "Listo",
                    color = Color(0xFF388E3C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Button(
                    onClick = onActivate,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Text(text = "Activar", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(granted: Boolean) {
    val bg = if (granted) Color(0xFF388E3C) else Color(0xFFFB8C00)
    val symbol = if (granted) "✓" else "!"
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
