package com.beto.app.trust

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI senior-friendly para configurar el contacto de confianza:
 *  - Card grande con el contacto actual (si hay) y opción de cambiarlo / quitarlo.
 *  - Botón gigante "Elegir un contacto" que dispara el system picker (la pantalla padre lo maneja).
 *  - Una vez elegido, el user selecciona "quién es" tocando un chip de relationship.
 *  - Botón "Guardar" decisivo al final.
 *
 * El padre (Activity) hace todo el wiring de Intent.ACTION_PICK y persistencia.
 */
@Composable
fun TrustedContactScreen(
    state: TrustedContactScreenState,
    onChooseFromContacts: () -> Unit,
    onRelationshipSelected: (TrustedContact.Relationship) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    // Column raíz que SIEMPRE muestra los botones abajo (no scrolleables); el contenido
    // de arriba scrollea si no entra. Sin esta estructura, el LazyColumn anterior crasheaba
    // porque estaba dentro de una Column sin altura acotada y los botones nunca renderizaban.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Mi contacto de confianza",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Cuando Beto detecte un mensaje sospechoso, va a poder llamar a esta persona con un solo toque.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(20.dp))

            when (val sel = state.selection) {
                null -> EmptyState(onChooseFromContacts = onChooseFromContacts)
                else -> SelectionCard(
                    displayName = sel.displayName,
                    phone = sel.phoneNumberRaw,
                    onChooseAnother = onChooseFromContacts,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (state.selection != null) {
                Text(
                    text = "¿Quién es?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(10.dp))
                RelationshipPicker(
                    selected = state.relationship,
                    onSelected = onRelationshipSelected,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.savedContact != null) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Quitar contacto de confianza",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = if (state.savedContact == state.selection?.toContactWith(state.relationship)) {
                    "Listo"
                } else {
                    "Guardar"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(text = "Volver", fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmptyState(onChooseFromContacts: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Todavía no elegiste a nadie",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tocá el botón de abajo y elegí a alguien de tu agenda.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onChooseFromContacts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = "Elegir un contacto", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun SelectionCard(
    displayName: String,
    phone: String,
    onChooseAnother: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = phone,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            TextButton(onClick = onChooseAnother) {
                Text(text = "Cambiar", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun RelationshipPicker(
    selected: TrustedContact.Relationship?,
    onSelected: (TrustedContact.Relationship) -> Unit,
) {
    // Column simple en vez de LazyColumn — solo hay 13 items y vivimos dentro de un
    // verticalScroll padre. LazyColumn anidado en Column no acotada explotaba en measure.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrustedContact.Relationship.values().forEach { rel ->
            val isSelected = rel == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surface,
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelected(rel) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = rel.label,
                        fontSize = 17.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Estado controlado por la Activity. Mantenerlo simple: un picked + un relationship.
 */
data class TrustedContactScreenState(
    val savedContact: TrustedContact?,
    val selection: PickedContact?,
    val relationship: TrustedContact.Relationship?,
) {
    val canSave: Boolean
        get() = selection != null && relationship != null

    companion object {
        fun fromSaved(saved: TrustedContact?): TrustedContactScreenState =
            TrustedContactScreenState(
                savedContact = saved,
                selection = saved?.let { PickedContact(it.displayName, it.phoneNumberRaw) },
                relationship = saved?.relationship,
            )
    }
}

/** Lo que devuelve el system picker antes de que el user elija el relationship. */
data class PickedContact(
    val displayName: String,
    val phoneNumberRaw: String,
) {
    fun toContactWith(rel: TrustedContact.Relationship?): TrustedContact? =
        rel?.let {
            TrustedContact(
                displayName = displayName,
                phoneNumberRaw = phoneNumberRaw,
                relationship = it,
            )
        }
}
