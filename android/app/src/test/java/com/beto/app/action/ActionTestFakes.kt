package com.beto.app.action

import android.content.SharedPreferences
import com.beto.app.contacts.ContactDataSource
import com.beto.app.contacts.ContactRepository
import com.beto.app.contacts.ContactRow
import com.beto.app.contacts.PhoneNumber
import com.beto.app.contacts.PhoneType
import com.beto.app.contacts.toE164
import com.beto.app.memory.UserMemoryStore

internal class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) {
        spoken += text
    }
}

internal class QueueVoiceCapture(
    responses: List<String?>,
) : SuspendableVoiceCapture {
    private val queue = ArrayDeque(responses)
    override suspend fun captureOnce(timeoutMs: Long): String? =
        if (queue.isEmpty()) null else queue.removeFirst()
}

internal fun fakeContactRepository(
    contacts: List<ContactRow>,
): ContactRepository =
    ContactRepository(
        dataSource = object : ContactDataSource {
            override fun searchByName(name: String): List<ContactRow> =
                contacts.filter { it.displayName.contains(name, ignoreCase = true) }

            override fun listContacts(limit: Int): List<ContactRow> = contacts.take(limit)

            override fun findByPhone(phone: String): ContactRow? = null

            override fun loadPhoneNumbers(contactId: Long): List<PhoneNumber> =
                listOf(PhoneNumber("+54 11 0000-$contactId", "+54110000$contactId".toE164(), PhoneType.MOBILE))

            override fun hasWhatsApp(contactId: Long): Boolean = true

            override fun hasEmail(contactId: Long): Boolean = false
        },
        permissionChecker = { true },
    )

internal fun memoryStore(): UserMemoryStore = UserMemoryStore(FakeSharedPreferences())

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue
    override fun edit(): SharedPreferences.Editor = FakeEditor(values)
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}

private class FakeEditor(
    private val values: MutableMap<String, String>,
) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, String?>()
    override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
    override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
    override fun clear(): SharedPreferences.Editor = apply { values.clear() }
    override fun commit(): Boolean {
        apply()
        return true
    }
    override fun apply() {
        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
    }
    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
    override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
    override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
}
