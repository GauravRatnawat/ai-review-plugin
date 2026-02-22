package com.aireview.plugin.claude

import com.aireview.plugin.model.Severity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CopilotApiClientTest {

    // --- parseJsonFindings() tests ---

    @Test
    fun `parseJsonFindings parses valid JSON array`() {
        val json = """
            [
                {"line": 10, "severity": "ERROR", "message": "Null pointer", "suggestion": "Fix it"},
                {"line": 20, "severity": "WARNING", "message": "Code smell"}
            ]
        """.trimIndent()

        val findings = CopilotApiClient.parseJsonFindings(json)
        assertEquals(2, findings.size)
        assertEquals(Severity.ERROR, findings[0].severity)
        assertEquals(Severity.WARNING, findings[1].severity)
    }

    @Test
    fun `parseJsonFindings returns empty list for empty array`() {
        assertTrue(CopilotApiClient.parseJsonFindings("[]").isEmpty())
    }

    @Test
    fun `parseJsonFindings strips code fences`() {
        val json = "```json\n[{\"line\": 1, \"severity\": \"INFO\", \"message\": \"Style\"}]\n```"
        val findings = CopilotApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
    }

    @Test
    fun `parseJsonFindings skips malformed elements`() {
        val json = """[{"line": 1, "severity": "ERROR", "message": "OK"}, {"invalid": true}]"""
        val findings = CopilotApiClient.parseJsonFindings(json)
        assertEquals(1, findings.size)
    }

    @Test
    fun `parseJsonFindings returns empty for invalid JSON`() {
        assertTrue(CopilotApiClient.parseJsonFindings("garbage").isEmpty())
    }

    // --- parseFindings() tests ---

    @Test
    fun `parseFindings parses valid GitHub Models response`() {
        val response = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "[{\"line\": 5, \"severity\": \"WARNING\", \"message\": \"Smell\"}]"
                        }
                    }
                ]
            }
        """.trimIndent()

        val findings = CopilotApiClient.parseFindings(response)
        assertEquals(1, findings.size)
        assertEquals(5, findings[0].line)
        assertEquals(Severity.WARNING, findings[0].severity)
    }

    @Test
    fun `parseFindings returns empty list for empty choices`() {
        val response = """{"choices": []}"""
        assertTrue(CopilotApiClient.parseFindings(response).isEmpty())
    }

    @Test
    fun `parseFindings returns empty list for null choices`() {
        val response = """{"id": "123"}"""
        assertTrue(CopilotApiClient.parseFindings(response).isEmpty())
    }

    @Test
    fun `parseFindings returns empty list for malformed response`() {
        assertTrue(CopilotApiClient.parseFindings("not json").isEmpty())
    }

    @Test
    fun `parseFindings handles response with code fences in content`() {
        val response = """
            {
                "choices": [{
                    "message": {
                        "content": "```json\n[{\"line\": 1, \"severity\": \"INFO\", \"message\": \"Tip\"}]\n```"
                    }
                }]
            }
        """.trimIndent()

        val findings = CopilotApiClient.parseFindings(response)
        assertEquals(1, findings.size)
    }
}
