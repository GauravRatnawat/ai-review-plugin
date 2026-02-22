package com.aireview.plugin.cache

import com.aireview.plugin.model.ReviewFinding
import com.intellij.openapi.components.Service

/**
 * Content-hash based cache to avoid redundant API calls for unchanged files.
 * Key: file path, Value: pair of content hash and cached findings.
 */
@Service
class ReviewCacheService {

    private val cache = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(
        val contentHash: String,
        val findings: List<ReviewFinding>
    )

    fun get(filePath: String, contentHash: String): List<ReviewFinding>? {
        val entry = cache[filePath] ?: return null
        return if (entry.contentHash == contentHash) entry.findings else null
    }

    fun put(filePath: String, contentHash: String, findings: List<ReviewFinding>) {
        cache[filePath] = CacheEntry(contentHash, findings)
    }

    fun invalidate(filePath: String) {
        cache.remove(filePath)
    }

    fun clear() {
        cache.clear()
    }

    val size: Int get() = cache.size
}
