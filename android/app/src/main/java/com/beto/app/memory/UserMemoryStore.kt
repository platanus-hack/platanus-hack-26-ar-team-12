package com.beto.app.memory

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

class UserMemoryStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(encryptedPreferences(context))

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserMemory> = _state.asStateFlow()

    fun current(): UserMemory = _state.value

    fun knowsAlias(alias: String): Boolean = current().knowsAlias(alias)

    fun resolveAlias(alias: String): ContactRef? = current().resolveAlias(alias)

    fun preferredChannel(contact: ContactRef): Channel? = current().preferredChannel(contact)

    suspend fun recordAlias(alias: String, contact: ContactRef) = mutex.withLock {
        update("alias") { it.withAlias(alias, contact) }
    }

    suspend fun recordChannel(contact: ContactRef, channel: Channel) = mutex.withLock {
        update("channel") { it.withChannelPreference(contact, channel) }
    }

    suspend fun recordFact(category: String, fact: String) = mutex.withLock {
        update("profile") { it.withFact(category, fact) }
    }

    suspend fun clear() = mutex.withLock {
        _state.value = UserMemory.empty()
        prefs.edit().remove(KEY_USER_MEMORY).commit()
        Timber.tag(LogTags.MEMORY).d("MEMORY_UPDATED key=clear")
    }

    private fun load(): UserMemory =
        runCatching {
            prefs.getString(KEY_USER_MEMORY, null)
                ?.let { json.decodeFromString<UserMemory>(it) }
                ?: UserMemory.empty()
        }.getOrElse { error ->
            Timber.tag(LogTags.MEMORY).w(error, "Corrupted memory, resetting")
            UserMemory.empty()
        }

    private fun update(key: String, transform: (UserMemory) -> UserMemory) {
        val next = transform(_state.value)
        val encoded = json.encodeToString(next)
        val persisted = prefs.edit()
            .putString(KEY_USER_MEMORY, encoded)
            .commit()
        if (persisted) {
            _state.value = next
            Timber.tag(LogTags.MEMORY).d("MEMORY_UPDATED key=%s", key)
        } else {
            Timber.tag(LogTags.MEMORY).w("MEMORY_UPDATE_FAILED key=%s", key)
        }
    }

    companion object {
        const val PREFS_NAME = "beto_user_memory"
        const val KEY_USER_MEMORY = "user_memory_v1"

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
