package com.aireview.plugin.claude

import com.aireview.plugin.model.Severity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaudeApiClientTest {

    // --- parseJsonFindings() tests ---

    @Test
    fun `parseJsonFindings parses valid JSON array with all severities`() {
        val json = """
            [
                {"line": 10, "severity": "ERROR", "message": "Null pointer", "suggestion": "Add null check"},
                {"line": 20, "severity": "WARNING", "message": "Code smell", "suggestion": null},
                {"line": 30, "severity": "INFO", "message": "Use idiomatic Kotlin", "suggestion": "Replace with let"}
            ]
        """.trimIndent()

        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(3, findings.size)

        assertEquals(10, findings[0].line)
        assertEquals(Severity.ERROR, findings[0].severity)
        assertEquals("Null pointer", findings[0].message)
        assertEquals("Add null check", findings[0].suggestion)

        assertEquals(20, findings[1].line)
        assertEquals(Severity.WARNING, findings[1].severity)
        assertNull(findings[1].suggestion)

        assertEquals(30, findings[2].line)
        assertEquals(Severity.INFO, findings[2].severity)
    }

    @Test
    fun `parseJsonFindings returns empty list for empty array`() {
        assertEquals(emptyList<Any>(), ClaudeApiClient.parseJsonFindings("[]"))
    }

    @Test
    fun `parseJsonFindings strips markdown json code fences`() {
        val json = """```json
            [{"line": 5, "severity": "ERROR", "message": "Bug"}]
            ```""".trimIndent()

        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
        assertEquals(5, findings[0].line)
    }

    @Test
    fun `parseJsonFindings strips plain code fences`() {
        val json = """```
            [{"line": 5, "severity": "WARNING", "message": "Smell"}]
            ```""".trimIndent()

        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
    }

    @Test
    fun `parseJsonFindings skips malformed elements`() {
        val json = """
            [
                {"line": 10, "severity": "ERROR", "message": "Valid"},
                {"bad": "element"},
                {"line": 20, "severity": "INFO", "message": "Also valid"}
            ]
        """.trimIndent()

        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(2, findings.size)
        assertEquals(10, findings[0].line)
        assertEquals(20, findings[1].line)
    }

    @Test
    fun `parseJsonFindings returns empty list for completely invalid JSON`() {
        val findings = ClaudeApiClient.parseJsonFindings("not json at all")
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `parseJsonFindings handles null suggestion gracefully`() {
        val json = """[{"line": 1, "severity": "INFO", "message": "Test", "suggestion": null}]"""
        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
        assertNull(findings[0].suggestion)
    }

    @Test
    fun `parseJsonFindings handles missing suggestion field`() {
        val json = """[{"line": 1, "severity": "INFO", "message": "Test"}]"""
        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
        assertNull(findings[0].suggestion)
    }

    @Test
    fun `parseJsonFindings handles unknown severity as INFO`() {
        val json = """[{"line": 1, "severity": "CRITICAL", "message": "Test"}]"""
        val findings = ClaudeApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
        assertEquals(Severity.INFO, findings[0].severity)
    }

    // --- parseFindings() tests ---

    @Test
    fun `parseFindings parses valid Claude API response`() {
        val response = """
            {
                "id": "msg_123",
                "type": "message",
                "content": [
                    {
                        "type": "text",
                        "text": "[{\"line\": 5, \"severity\": \"ERROR\", \"message\": \"Bug\"}]"
                    }
                ]
            }
        """.trimIndent()

        val findings = ClaudeApiClient.parseFindings(response)
        assertEquals(1, findings.size)
        assertEquals(5, findings[0].line)
        assertEquals(Severity.ERROR, findings[0].severity)
    }

    @Test
    fun `parseFindings returns empty list for empty content array`() {
        val response = """{"id": "msg_123", "content": []}"""
        assertTrue(ClaudeApiClient.parseFindings(response).isEmpty())
    }

    @Test
    fun `parseFindings returns empty list for null content`() {
        val response = """{"id": "msg_123"}"""
        assertTrue(ClaudeApiClient.parseFindings(response).isEmpty())
    }

    @Test
    fun `parseFindings returns empty list when no text block exists`() {
        val response = """
            {
                "content": [
                    {"type": "image", "source": {"type": "base64"}}
                ]
            }
        """.trimIndent()

        assertTrue(ClaudeApiClient.parseFindings(response).isEmpty())
    }

    @Test
    fun `parseFindings returns empty list for malformed response`() {
        assertTrue(ClaudeApiClient.parseFindings("not json").isEmpty())
    }
}
