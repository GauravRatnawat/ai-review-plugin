package com.aireview.plugin.annotator

import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class AiReviewExternalAnnotatorTest {

    private lateinit var annotator: AiReviewExternalAnnotator

    @BeforeEach
    fun setUp() {
        annotator = AiReviewExternalAnnotator()
    }

    // --- computeHash() ---

    @Test
    fun `computeHash produces deterministic SHA-256`() {
        val hash1 = invokePrivate<String>("computeHash", "hello world")
        val hash2 = invokePrivate<String>("computeHash", "hello world")
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex = 64 chars
    }

    @Test
    fun `computeHash produces different hashes for different content`() {
        val hash1 = invokePrivate<String>("computeHash", "content A")
        val hash2 = invokePrivate<String>("computeHash", "content B")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeHash handles empty string`() {
        val hash = invokePrivate<String>("computeHash", "")
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    // --- escapeHtml() ---

    @Test
    fun `escapeHtml escapes ampersand`() {
        assertEquals("a &amp; b", invokePrivate<String>("escapeHtml", "a & b"))
    }

    @Test
    fun `escapeHtml escapes angle brackets`() {
        assertEquals("&lt;div&gt;", invokePrivate<String>("escapeHtml", "<div>"))
    }

    @Test
    fun `escapeHtml escapes quotes`() {
        assertEquals("&quot;hello&quot;", invokePrivate<String>("escapeHtml", "\"hello\""))
    }

    @Test
    fun `escapeHtml converts newlines to br tags`() {
        assertEquals("line1<br/>line2", invokePrivate<String>("escapeHtml", "line1\nline2"))
    }

    @Test
    fun `escapeHtml handles empty string`() {
        assertEquals("", invokePrivate<String>("escapeHtml", ""))
    }

    // --- buildTooltip() ---

    @Test
    fun `buildTooltip includes severity and message`() {
        val finding = ReviewFinding(line = 1, severity = Severity.ERROR, message = "Bug found")
        val tooltip = invokePrivate<String>("buildTooltip", finding)

        assertTrue(tooltip.contains("AI Code Review"))
        assertTrue(tooltip.contains("ERROR"))
        assertTrue(tooltip.contains("Bug found"))
    }

    @Test
    fun `buildTooltip includes suggestion when present`() {
        val finding = ReviewFinding(line = 1, severity = Severity.WARNING, message = "Issue", suggestion = "Fix this")
        val tooltip = invokePrivate<String>("buildTooltip", finding)

        assertTrue(tooltip.contains("Suggestion:"))
        assertTrue(tooltip.contains("Fix this"))
    }

    @Test
    fun `buildTooltip omits suggestion section when null`() {
        val finding = ReviewFinding(line = 1, severity = Severity.INFO, message = "Style issue")
        val tooltip = invokePrivate<String>("buildTooltip", finding)

        assertFalse(tooltip.contains("Suggestion:"))
    }

    @Test
    fun `buildTooltip omits suggestion section when blank`() {
        val finding = ReviewFinding(line = 1, severity = Severity.INFO, message = "Style", suggestion = "  ")
        val tooltip = invokePrivate<String>("buildTooltip", finding)

        assertFalse(tooltip.contains("Suggestion:"))
    }

    // --- isReviewableFile() ---

    @Test
    fun `isReviewableFile accepts Kotlin files`() {
        val project = mockProject("/project")
        assertTrue(invokePrivate<Boolean>("isReviewableFile", "/project/src/Main.kt", project))
    }

    @Test
    fun `isReviewableFile accepts Java files`() {
        val project = mockProject("/project")
        assertTrue(invokePrivate<Boolean>("isReviewableFile", "/project/src/Main.java", project))
    }

    @Test
    fun `isReviewableFile accepts TypeScript files`() {
        val project = mockProject("/project")
        assertTrue(invokePrivate<Boolean>("isReviewableFile", "/project/src/app.tsx", project))
    }

    @Test
    fun `isReviewableFile accepts Python files`() {
        val project = mockProject("/project")
        assertTrue(invokePrivate<Boolean>("isReviewableFile", "/project/src/main.py", project))
    }

    @Test
    fun `isReviewableFile rejects files in build directory`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/project/build/classes/Main.kt", project))
    }

    @Test
    fun `isReviewableFile rejects files in gradle directory`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/project/.gradle/cache/file.kt", project))
    }

    @Test
    fun `isReviewableFile rejects files in node_modules`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/project/node_modules/pkg/index.js", project))
    }

    @Test
    fun `isReviewableFile rejects files outside project`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/other/src/Main.kt", project))
    }

    @Test
    fun `isReviewableFile rejects unknown extensions`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/project/src/image.png", project))
    }

    @Test
    fun `isReviewableFile rejects binary files`() {
        val project = mockProject("/project")
        assertFalse(invokePrivate<Boolean>("isReviewableFile", "/project/lib/app.jar", project))
    }

    // --- mapSeverity() ---

    @Test
    fun `mapSeverity maps ERROR to HighlightSeverity ERROR`() {
        assertEquals(HighlightSeverity.ERROR, invokePrivate("mapSeverity", Severity.ERROR))
    }

    @Test
    fun `mapSeverity maps WARNING to HighlightSeverity WARNING`() {
        assertEquals(HighlightSeverity.WARNING, invokePrivate("mapSeverity", Severity.WARNING))
    }

    @Test
    fun `mapSeverity maps INFO to HighlightSeverity WEAK_WARNING`() {
        assertEquals(HighlightSeverity.WEAK_WARNING, invokePrivate("mapSeverity", Severity.INFO))
    }

    // --- mapHighlightType() ---

    @Test
    fun `mapHighlightType maps ERROR to GENERIC_ERROR`() {
        assertEquals(ProblemHighlightType.GENERIC_ERROR, invokePrivate("mapHighlightType", Severity.ERROR))
    }

    @Test
    fun `mapHighlightType maps WARNING to WARNING`() {
        assertEquals(ProblemHighlightType.WARNING, invokePrivate("mapHighlightType", Severity.WARNING))
    }

    @Test
    fun `mapHighlightType maps INFO to WEAK_WARNING`() {
        assertEquals(ProblemHighlightType.WEAK_WARNING, invokePrivate("mapHighlightType", Severity.INFO))
    }

    // --- Helpers ---

    private fun mockProject(basePath: String): Project {
        val project = mockk<Project>()
        every { project.basePath } returns basePath
        return project
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokePrivate(methodName: String, vararg args: Any): T {
        val method = findMethod(methodName, args)
        method.isAccessible = true
        return method.invoke(annotator, *args) as T
    }

    private fun findMethod(name: String, args: Array<out Any>): Method {
        return annotator::class.java.declaredMethods
            .filter { it.name == name }
            .first { it.parameterCount == args.size }
    }
}
