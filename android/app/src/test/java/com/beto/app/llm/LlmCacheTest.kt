package com.beto.app.llm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmCacheTest {

    @Test
    fun putAndGetReturnsSameDecision() = runBlocking {
        val cache = LlmCache()
        val decision = Decision.ToolCall(
            tool = ToolDescriptors.MAKE_CALL,
            args = mapOf("contact" to "Juan"),
        )

        cache.put("llama a juan", decision)

        assertEquals(decision, cache.get("llama a juan"))
    }

    @Test
    fun expiredEntriesReturnNull() = runBlocking {
        var now = 1_000L
        val cache = LlmCache(ttlMs = 100L, nowMs = { now })

        cache.put("input", Decision.Unknown)
        now = 1_101L

        assertNull(cache.get("input"))
    }

    @Test
    fun evictsLeastRecentlyUsedPastMaxEntries() = runBlocking {
        val cache = LlmCache(maxEntries = 2)

        cache.put("one", Decision.Unknown)
        cache.put("two", Decision.Unknown)
        cache.get("one")
        cache.put("three", Decision.Unknown)

        assertEquals(Decision.Unknown, cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals(Decision.Unknown, cache.get("three"))
    }

    @Test
    fun concurrentAccessDoesNotCorrupt() = runBlocking {
        val cache = LlmCache(maxEntries = 50)

        (0 until 100).map { index ->
            async {
                val key = "input-$index"
                cache.put(key, Decision.Unknown)
                cache.get(key)
            }
        }.awaitAll()

        assertEquals(Decision.Unknown, cache.get("input-99"))
    }
}
