package com.beto.app.trust

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beto.app.util.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persistencia del único contacto de confianza configurado por el user.
 *
 * Patrón espejo de `UserMemoryStore`:
 *  - Constructor público con `Context` arma EncryptedSharedPreferences (datos sensibles: telefono).
 *  - Constructor `internal` con `SharedPreferences` para tests con un fake in-memory.
 *  - StateFlow para que la UI Compose observe cambios sin re-leer prefs.
 */
class TrustedContactsRepository internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(encryptedPreferences(context))

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TrustedContact?> = _state.asStateFlow()

    fun current(): TrustedContact? = _state.value

    fun isConfigured(): Boolean = current() != null

    suspend fun save(contact: TrustedContact) = mutex.withLock {
        val encoded = json.encodeToString(contact)
        val ok = prefs.edit().putString(KEY_CONTACT, encoded).commit()
        if (ok) {
            _state.value = contact
            Timber.tag(LogTags.MEMORY).d("TRUSTED_CONTACT_SAVED rel=%s", contact.relationship.name)
        } else {
            Timber.tag(LogTags.MEMORY).w("TRUSTED_CONTACT_SAVE_FAILED")
        }
    }

    suspend fun clear() = mutex.withLock {
        prefs.edit().remove(KEY_CONTACT).commit()
        _state.value = null
        Timber.tag(LogTags.MEMORY).d("TRUSTED_CONTACT_CLEARED")
    }

    private fun load(): TrustedContact? = runCatching {
        prefs.getString(KEY_CONTACT, null)?.let { json.decodeFromString<TrustedContact>(it) }
    }.getOrElse { error ->
        Timber.tag(LogTags.MEMORY).w(error, "Corrupted trusted contact, ignoring")
        null
    }

    companion object {
        const val PREFS_NAME = "beto_trusted_contacts"
        const val KEY_CONTACT = "trusted_contact_v1"

        private fun encryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
