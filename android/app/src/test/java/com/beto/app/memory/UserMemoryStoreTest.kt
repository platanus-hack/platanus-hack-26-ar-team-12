package com.beto.app.memory

import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryStoreTest {

    @Test
    fun loadsEmptyWhenFirstRun() {
        val store = UserMemoryStore(FakeSharedPreferences())

        assertEquals(UserMemory.empty(), store.current())
    }

    @Test
    fun persistsAndReloadsAliases() = runBlocking {
        val prefs = FakeSharedPreferences()
        val store = UserMemoryStore(prefs)

        store.recordAlias("nieto", contact(1))
        val reloaded = UserMemoryStore(prefs)

        assertEquals(contact(1), reloaded.resolveAlias("nieto"))
    }

    @Test
    fun concurrentRecordAliasDoesNotLoseUpdates() = runBlocking {
        val store = UserMemoryStore(FakeSharedPreferences())

        (0 until 50).map { index ->
            async {
                store.recordAlias("alias-$index", contact(index.toLong()))
            }
        }.awaitAll()

        assertEquals(50, store.current().aliases.size)
    }

    @Test
    fun clearResetsToEmpty() = runBlocking {
        val store = UserMemoryStore(FakeSharedPreferences())

        store.recordAlias("nieto", contact(1))
        store.clear()

        assertEquals(UserMemory.empty(), store.current())
        assertFalse(store.knowsAlias("nieto"))
    }

    @Test
    fun corruptedJsonFallsBackToEmpty() {
        val prefs = FakeSharedPreferences(
            initial = mapOf(UserMemoryStore.KEY_USER_MEMORY to "{bad-json"),
        )

        val store = UserMemoryStore(prefs)

        assertEquals(UserMemory.empty(), store.current())
    }

    @Test
    fun recordsChannelAndFact() = runBlocking {
        val store = UserMemoryStore(FakeSharedPreferences())
        val contact = contact(1)

        store.recordChannel(contact, Channel.SMS)
        store.recordFact("ciudad", "vive en Buenos Aires")

        assertEquals(Channel.SMS, store.preferredChannel(contact))
        assertTrue(store.current().profile["ciudad"].orEmpty().contains("vive en Buenos Aires"))
    }

    private fun contact(id: Long): ContactRef =
        ContactRef(id = id, displayName = "Juan $id", phoneE164 = "+541100000$id")
}

private class FakeSharedPreferences(
    initial: Map<String, String> = emptyMap(),
) : SharedPreferences {
    private val values = initial.toMutableMap()

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
    private var clearRequested = false

    override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
        pending[key] = value
    }

    override fun remove(key: String): SharedPreferences.Editor = apply {
        pending[key] = null
    }

    override fun clear(): SharedPreferences.Editor = apply {
        clearRequested = true
    }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        if (clearRequested) values.clear()
        pending.forEach { (key, value) ->
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this

    override fun putInt(key: String, value: Int): SharedPreferences.Editor = this

    override fun putLong(key: String, value: Long): SharedPreferences.Editor = this

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
}
