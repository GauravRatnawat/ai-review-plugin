package com.aireview.plugin.cache

import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ReviewCacheServiceTest {

    private lateinit var cache: ReviewCacheService

    private val finding1 = ReviewFinding(line = 10, severity = Severity.ERROR, message = "Bug found")
    private val finding2 = ReviewFinding(line = 20, severity = Severity.WARNING, message = "Code smell")

    @BeforeEach
    fun setUp() {
        cache = ReviewCacheService()
    }

    @Test
    fun `get returns null on cache miss`() {
        assertNull(cache.get("/path/file.kt", "abc123"))
    }

    @Test
    fun `get returns findings on hash match`() {
        cache.put("/path/file.kt", "abc123", listOf(finding1))
        val result = cache.get("/path/file.kt", "abc123")
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals(finding1, result[0])
    }

    @Test
    fun `get returns null on hash mismatch`() {
        cache.put("/path/file.kt", "abc123", listOf(finding1))
        assertNull(cache.get("/path/file.kt", "different-hash"))
    }

    @Test
    fun `put overwrites existing entry`() {
        cache.put("/path/file.kt", "hash1", listOf(finding1))
        cache.put("/path/file.kt", "hash2", listOf(finding2))

        assertNull(cache.get("/path/file.kt", "hash1"))
        assertEquals(listOf(finding2), cache.get("/path/file.kt", "hash2"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `invalidate removes entry`() {
        cache.put("/path/file.kt", "abc123", listOf(finding1))
        assertEquals(1, cache.size)

        cache.invalidate("/path/file.kt")
        assertEquals(0, cache.size)
        assertNull(cache.get("/path/file.kt", "abc123"))
    }

    @Test
    fun `invalidate on non-existent key does nothing`() {
        cache.invalidate("/non-existent")
        assertEquals(0, cache.size)
    }

    @Test
    fun `clear removes all entries`() {
        cache.put("/path/a.kt", "h1", listOf(finding1))
        cache.put("/path/b.kt", "h2", listOf(finding2))
        assertEquals(2, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun `size reflects number of entries`() {
        assertEquals(0, cache.size)
        cache.put("/a", "h1", emptyList())
        assertEquals(1, cache.size)
        cache.put("/b", "h2", emptyList())
        assertEquals(2, cache.size)
    }

    @Test
    fun `evicts oldest entries when exceeding 200 limit`() {
        // Fill cache to 200
        for (i in 1..200) {
            cache.put("/file$i.kt", "hash$i", listOf(finding1))
        }
        assertEquals(200, cache.size)

        // Add one more — oldest should be evicted
        cache.put("/file201.kt", "hash201", listOf(finding2))
        assertEquals(200, cache.size)

        // First entry should be evicted
        assertNull(cache.get("/file1.kt", "hash1"))
        // Latest entry should exist
        assertNotNull(cache.get("/file201.kt", "hash201"))
        // Second entry should still exist
        assertNotNull(cache.get("/file2.kt", "hash2"))
    }

    @Test
    fun `concurrent put and get do not throw`() {
        val executor = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(100)

        for (i in 1..100) {
            executor.submit {
                try {
                    cache.put("/file$i.kt", "hash$i", listOf(finding1))
                    cache.get("/file$i.kt", "hash$i")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()
        assertTrue(cache.size in 1..200)
    }

    @Test
    fun `caches empty findings list`() {
        cache.put("/file.kt", "hash", emptyList())
        assertEquals(emptyList<ReviewFinding>(), cache.get("/file.kt", "hash"))
    }
}
