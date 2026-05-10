package com.beto.app.companion

import com.beto.app.action.SuspendableVoiceCapture
import com.beto.app.memory.UserMemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChatClient(
        private val reply: String = "Estoy bien, ¿y vos?",
        private val fact: ProfileFact? = null,
    ) : CompanionChatClient {
        var chatCalls = 0
        var factCalls = 0
        override suspend fun chat(history: List<CompanionMessage>): String {
            chatCalls++
            return reply
        }
        override suspend fun extractProfileFact(userText: String): ProfileFact? {
            factCalls++
            return fact
        }
    }

    private class FakeTtsSink : CompanionTtsSink {
        val spoken = mutableListOf<String>()
        override suspend fun speak(text: String) { spoken.add(text) }
    }

    private class FakeVoiceCapture(private val response: String?) : SuspendableVoiceCapture {
        override suspend fun captureOnce(timeoutMs: Long): String? = response
    }

    // Para no pelear con EncryptedSharedPreferences, usamos un fake SharedPreferences mínimo
    // y construimos el UserMemoryStore real con `internal constructor(prefs)`.
    private class FakePrefs : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(p: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(p: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { if (key != null) map[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) map[key] = values }
        override fun putInt(key: String?, value: Int) = apply { if (key != null) map[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) map[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) map[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) map[key] = value }
        override fun remove(key: String?) = apply { if (key != null) map.remove(key) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }

    private fun newStore(): UserMemoryStore = UserMemoryStore(FakePrefs())

    @Test
    fun `sendUserText appends user and beto messages`() = runTest {
        val llm = FakeChatClient(reply = "Hola, ¿cómo andás?")
        val tts = FakeTtsSink()
        val vm = CompanionViewModel(
            llm = llm,
            tts = tts,
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )

        vm.sendUserText("Hola Beto")

        val messages = vm.messages.value
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("Hola Beto", messages[0].text)
        assertEquals(Role.BETO, messages[1].role)
        assertEquals("Hola, ¿cómo andás?", messages[1].text)
        assertEquals(1, tts.spoken.size)
        assertEquals("Hola, ¿cómo andás?", tts.spoken.first())
    }

    @Test
    fun `extractProfileFact null does not show confirmation card`() = runTest {
        val llm = FakeChatClient(fact = null)
        val vm = CompanionViewModel(
            llm = llm,
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )

        vm.sendUserText("Hola Beto")

        val betoMsg = vm.messages.value.last()
        assertNull(betoMsg.pendingFactConfirmation)
    }

    @Test
    fun `extractProfileFact non-null shows confirmation card`() = runTest {
        val fact = ProfileFact(category = "hobby", fact = "tango")
        val llm = FakeChatClient(fact = fact)
        val vm = CompanionViewModel(
            llm = llm,
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )

        vm.sendUserText("Me gusta el tango")

        val betoMsg = vm.messages.value.last()
        assertEquals(fact, betoMsg.pendingFactConfirmation)
    }

    @Test
    fun `confirmFact yes saves to memory and clears card`() = runTest {
        val fact = ProfileFact(category = "hobby", fact = "tango")
        val llm = FakeChatClient(fact = fact)
        val store = newStore()
        val vm = CompanionViewModel(
            llm = llm,
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = store,
        )

        vm.sendUserText("Me gusta el tango")
        val betoMsg = vm.messages.value.last()
        vm.confirmFact(betoMsg.id, fact, confirmed = true)

        // El mensaje ya no tiene pending fact
        val updated = vm.messages.value.last()
        assertNull(updated.pendingFactConfirmation)

        // Y el fact quedó en la memoria
        val saved = store.current().profile["hobby"].orEmpty()
        assertTrue("expected tango in saved facts but was $saved", "tango" in saved)
    }

    @Test
    fun `confirmFact no clears card without saving`() = runTest {
        val fact = ProfileFact(category = "hobby", fact = "tango")
        val llm = FakeChatClient(fact = fact)
        val store = newStore()
        val vm = CompanionViewModel(
            llm = llm,
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = store,
        )

        vm.sendUserText("Me gusta el tango")
        val betoMsg = vm.messages.value.last()
        vm.confirmFact(betoMsg.id, fact, confirmed = false)

        assertNull(vm.messages.value.last().pendingFactConfirmation)
        assertTrue("expected no facts saved", store.current().profile.isEmpty())
    }

    @Test
    fun `forgetSession clears messages`() = runTest {
        val vm = CompanionViewModel(
            llm = FakeChatClient(),
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )
        vm.sendUserText("Hola")
        assertTrue(vm.messages.value.isNotEmpty())

        vm.forgetSession()

        assertTrue(vm.messages.value.isEmpty())
    }

    @Test
    fun `history capped at 20`() = runTest {
        val vm = CompanionViewModel(
            llm = FakeChatClient(),
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )

        // 11 user texts → 22 messages — debería cap a 20
        repeat(11) { vm.sendUserText("msg $it") }

        assertTrue("expected ≤ 20 messages, got ${vm.messages.value.size}", vm.messages.value.size <= 20)
    }

    @Test
    fun `startVoiceInput with captured text appends user message`() = runTest {
        val llm = FakeChatClient()
        val vm = CompanionViewModel(
            llm = llm,
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture("hola desde voz"),
            memory = newStore(),
        )

        vm.startVoiceInput()

        val first = vm.messages.value.first()
        assertEquals(Role.USER, first.role)
        assertEquals("hola desde voz", first.text)
    }

    @Test
    fun `startVoiceInput with no capture does nothing`() = runTest {
        val vm = CompanionViewModel(
            llm = FakeChatClient(),
            tts = FakeTtsSink(),
            voiceCapture = FakeVoiceCapture(null),
            memory = newStore(),
        )

        vm.startVoiceInput()

        assertTrue(vm.messages.value.isEmpty())
    }
}
