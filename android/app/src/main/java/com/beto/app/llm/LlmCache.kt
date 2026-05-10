package com.beto.app.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class LlmCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private val entries = object : LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > maxEntries
    }

    suspend fun get(input: String): Decision? = mutex.withLock {
        val key = input.sha256()
        val entry = entries[key] ?: return@withLock null
        if (nowMs() - entry.createdAtMs > ttlMs) {
            entries.remove(key)
            null
        } else {
            entry.decision
        }
    }

    suspend fun put(input: String, decision: Decision) {
        mutex.withLock {
            entries[input.sha256()] = CacheEntry(decision = decision, createdAtMs = nowMs())
        }
    }

    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }

    private data class CacheEntry(
        val decision: Decision,
        val createdAtMs: Long,
    )

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 50
        const val DEFAULT_TTL_MS = 30 * 60 * 1_000L
    }
}
