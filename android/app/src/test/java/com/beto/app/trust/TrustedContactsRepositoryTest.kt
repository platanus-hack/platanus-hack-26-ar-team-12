package com.beto.app.trust

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedContactsRepositoryTest {

    @Test
    fun `current is null on fresh repo`() = runTest {
        val repo = TrustedContactsRepository(FakeSharedPreferences())
        assertNull(repo.current())
        assertFalse(repo.isConfigured())
    }

    @Test
    fun `save persists and exposes via current`() = runTest {
        val prefs = FakeSharedPreferences()
        val repo = TrustedContactsRepository(prefs)

        val contact = TrustedContact(
            displayName = "Fran Iturain",
            phoneNumberRaw = "+54 9 11 1234-5678",
            relationship = TrustedContact.Relationship.NIETO,
        )
        repo.save(contact)

        assertEquals(contact, repo.current())
        assertTrue(repo.isConfigured())
    }

    @Test
    fun `save survives a second repo instance backed by same prefs`() = runTest {
        val prefs = FakeSharedPreferences()
        val first = TrustedContactsRepository(prefs)
        first.save(
            TrustedContact(
                displayName = "Carla",
                phoneNumberRaw = "1133334444",
                relationship = TrustedContact.Relationship.HIJA,
            ),
        )

        val second = TrustedContactsRepository(prefs)
        assertEquals("Carla", second.current()?.displayName)
        assertEquals(TrustedContact.Relationship.HIJA, second.current()?.relationship)
    }

    @Test
    fun `clear removes the stored contact`() = runTest {
        val repo = TrustedContactsRepository(FakeSharedPreferences())
        repo.save(
            TrustedContact("X", "1", TrustedContact.Relationship.OTRO),
        )
        repo.clear()
        assertNull(repo.current())
        assertFalse(repo.isConfigured())
    }

    @Test
    fun `corrupted prefs payload yields null instead of crashing`() = runTest {
        val prefs = FakeSharedPreferences().apply {
            edit().putString(TrustedContactsRepository.KEY_CONTACT, "not-json").commit()
        }
        val repo = TrustedContactsRepository(prefs)
        assertNull(repo.current())
    }

    @Test
    fun `state flow reflects save and clear`() = runTest {
        val repo = TrustedContactsRepository(FakeSharedPreferences())
        assertNull(repo.state.value)

        val contact = TrustedContact("Lu", "555", TrustedContact.Relationship.NIETA)
        repo.save(contact)
        assertEquals(contact, repo.state.value)

        repo.clear()
        assertNull(repo.state.value)
    }

    @Test
    fun `callLabel formats relationship lowercase capitalized`() {
        val contact = TrustedContact("Fran", "1", TrustedContact.Relationship.NIETO)
        assertEquals("Llamar a Mi nieto", contact.callLabel)
    }
}

/**
 * Implementación in-memory de SharedPreferences. Implementa solo lo que el repo
 * necesita: getString, edit + putString/remove + commit. El resto rota por defaults
 * o lanza UnsupportedOperationException si llegamos a usarlo (señal de drift).
 */
private class FakeSharedPreferences : SharedPreferences {
    private val map = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?): String? =
        (map[key] as? String) ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        defValues
    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)

    private class FakeEditor(
        private val backing: HashMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { removals += key }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) backing.clear()
            removals.forEach { backing.remove(it) }
            pending.forEach { (k, v) ->
                if (v == null) backing.remove(k) else backing[k] = v
            }
            return true
        }

        override fun apply() { commit() }
    }
}
