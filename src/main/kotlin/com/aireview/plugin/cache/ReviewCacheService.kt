package com.aireview.plugin.cache

import com.aireview.plugin.model.ReviewFinding
import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Thread-safe content-hash cache to avoid redundant API calls for unchanged files.
 * Bounded to [MAX_ENTRIES] to prevent unbounded memory growth in long IDE sessions.
 */
@Service
class ReviewCacheService {

    companion object {
        private const val MAX_ENTRIES = 200
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val insertionOrder = ConcurrentLinkedDeque<String>()

    data class CacheEntry(
        val contentHash: String,
        val findings: List<ReviewFinding>,
    )

    fun get(filePath: String, contentHash: String): List<ReviewFinding>? {
        val entry = cache[filePath] ?: return null
        return if (entry.contentHash == contentHash) entry.findings else null
    }

    fun put(filePath: String, contentHash: String, findings: List<ReviewFinding>) {
        cache[filePath] = CacheEntry(contentHash, findings)
        insertionOrder.remove(filePath)
        insertionOrder.addLast(filePath)
        evictIfNeeded()
    }

    fun invalidate(filePath: String) {
        cache.remove(filePath)
        insertionOrder.remove(filePath)
    }

    fun clear() {
        cache.clear()
        insertionOrder.clear()
    }

    val size: Int get() = cache.size

    private fun evictIfNeeded() {
        while (cache.size > MAX_ENTRIES) {
            val oldest = insertionOrder.pollFirst() ?: break
            cache.remove(oldest)
        }
    }
}
