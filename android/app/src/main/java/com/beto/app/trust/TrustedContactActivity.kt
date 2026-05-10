package com.beto.app.trust

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.beto.app.BetoApplication
import com.beto.app.ui.BetoTheme
import com.beto.app.util.LogTags
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Activity que orquesta el picker de contacto de confianza.
 *
 * Flujo:
 *  1. Muestra `TrustedContactScreen` con el estado actual (saved del repo).
 *  2. "Elegir contacto" → `Intent.ACTION_PICK` con `CommonDataKinds.Phone.CONTENT_URI`
 *     (el system picker filtra solo contactos con teléfono — ya nos da número directo).
 *  3. El callback lee `displayName` y `number` del cursor del URI devuelto.
 *  4. User elige relationship + tap "Guardar" → repo.save → `setResult(OK)` + `finish`.
 *
 * Si el system picker falla por algún motivo (raro), el botón sigue siendo idempotente:
 * el user puede reintentar.
 */
class TrustedContactActivity : ComponentActivity() {

    private val repo by lazy { BetoApplication.trustedContactsRepository }

    private var currentSelection by mutableStateOf<PickedContact?>(null)
    private var currentRelationship by mutableStateOf<TrustedContact.Relationship?>(null)

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Timber.tag(LogTags.INTENT).d("TRUSTED_PICK cancelled or failed code=%d", result.resultCode)
            return@registerForActivityResult
        }
        val uri = result.data?.data ?: return@registerForActivityResult
        val picked = readPickedContact(uri)
        if (picked != null) {
            currentSelection = picked
            // Si todavía no había relationship, default razonable: el user ya viene de un flow
            // anti-fraude → "Mi nieto" es la relación más asociada al caso del pitch. Es un
            // hint, no un lock — sigue siendo editable.
            if (currentRelationship == null) {
                currentRelationship = TrustedContact.Relationship.NIETO
            }
            Timber.tag(LogTags.INTENT).i("TRUSTED_PICK ok name=%s", picked.displayName)
        } else {
            Timber.tag(LogTags.INTENT).w("TRUSTED_PICK uri sin teléfono utilizable")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos los estados desde el repo (si ya había contacto).
        val saved = repo.current()
        currentSelection = saved?.let { PickedContact(it.displayName, it.phoneNumberRaw) }
        currentRelationship = saved?.relationship

        setContent {
            BetoTheme(darkTheme = false) {
                val savedState by repo.state.collectAsState()
                val state = remember(currentSelection, currentRelationship, savedState) {
                    TrustedContactScreenState(
                        savedContact = savedState,
                        selection = currentSelection,
                        relationship = currentRelationship,
                    )
                }
                TrustedContactScreen(
                    state = state,
                    onChooseFromContacts = { launchSystemPicker() },
                    onRelationshipSelected = { rel -> currentRelationship = rel },
                    onSave = { handleSave() },
                    onClear = { handleClear() },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun launchSystemPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        try {
            pickContactLauncher.launch(intent)
        } catch (t: Throwable) {
            Timber.tag(LogTags.INTENT).e(t, "TRUSTED_PICK no se pudo abrir el system picker")
        }
    }

    private fun handleSave() {
        val picked = currentSelection ?: return
        val rel = currentRelationship ?: return
        val contact = TrustedContact(
            displayName = picked.displayName,
            phoneNumberRaw = picked.phoneNumberRaw,
            relationship = rel,
        )
        lifecycleScope.launch {
            repo.save(contact)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun handleClear() {
        lifecycleScope.launch {
            repo.clear()
            currentSelection = null
            currentRelationship = null
        }
    }

    private fun readPickedContact(uri: Uri): PickedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getStringOrEmpty(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phone = cursor.getStringOrEmpty(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (phone.isBlank()) return@use null
            PickedContact(
                displayName = name.ifBlank { phone },
                phoneNumberRaw = phone,
            )
        }
    }

    private fun Cursor.getStringOrEmpty(column: String): String {
        val idx = getColumnIndex(column)
        if (idx < 0) return ""
        return getString(idx).orEmpty()
    }
}
