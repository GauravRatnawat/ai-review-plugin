package com.aireview.plugin.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReviewFindingTest {

    @Test
    fun `data class construction with all fields`() {
        val finding = ReviewFinding(
            line = 42,
            severity = Severity.ERROR,
            message = "Null pointer dereference",
            suggestion = "Add null check before access",
        )

        assertEquals(42, finding.line)
        assertEquals(Severity.ERROR, finding.severity)
        assertEquals("Null pointer dereference", finding.message)
        assertEquals("Add null check before access", finding.suggestion)
    }

    @Test
    fun `default suggestion is null`() {
        val finding = ReviewFinding(line = 1, severity = Severity.INFO, message = "Test")
        assertNull(finding.suggestion)
    }

    @Test
    fun `data class equality`() {
        val a = ReviewFinding(line = 10, severity = Severity.WARNING, message = "Smell")
        val b = ReviewFinding(line = 10, severity = Severity.WARNING, message = "Smell")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data class inequality on different line`() {
        val a = ReviewFinding(line = 10, severity = Severity.ERROR, message = "Bug")
        val b = ReviewFinding(line = 20, severity = Severity.ERROR, message = "Bug")
        assertNotEquals(a, b)
    }

    @Test
    fun `data class copy`() {
        val original = ReviewFinding(line = 5, severity = Severity.ERROR, message = "Bug")
        val copy = original.copy(severity = Severity.WARNING)

        assertEquals(5, copy.line)
        assertEquals(Severity.WARNING, copy.severity)
        assertEquals("Bug", copy.message)
    }

    @Test
    fun `Severity enum has exactly three values`() {
        assertEquals(3, Severity.entries.size)
        assertTrue(Severity.entries.contains(Severity.ERROR))
        assertTrue(Severity.entries.contains(Severity.WARNING))
        assertTrue(Severity.entries.contains(Severity.INFO))
    }

    @Test
    fun `AiReviewInfo construction`() {
        val info = AiReviewInfo(
            filePath = "/project/src/Main.kt",
            fileContent = "fun main() {}",
            gitDiff = "+ fun main() {}",
            contentHash = "abc123",
        )

        assertEquals("/project/src/Main.kt", info.filePath)
        assertEquals("fun main() {}", info.fileContent)
        assertEquals("+ fun main() {}", info.gitDiff)
        assertEquals("abc123", info.contentHash)
    }

    @Test
    fun `AiReviewInfo allows null gitDiff`() {
        val info = AiReviewInfo(
            filePath = "/file.kt",
            fileContent = "",
            gitDiff = null,
            contentHash = "hash",
        )
        assertNull(info.gitDiff)
    }
}
