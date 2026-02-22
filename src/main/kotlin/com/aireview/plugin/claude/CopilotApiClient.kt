package com.aireview.plugin.claude

import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import com.aireview.plugin.settings.AiReviewSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import java.net.HttpURLConnection
import java.net.URI

/**
 * GitHub Models API client (OpenAI-compatible) for Copilot providers.
 * Uses the user's GitHub PAT to call models via https://models.github.ai.
 */
object CopilotApiClient {

    private val LOG = Logger.getInstance(CopilotApiClient::class.java)
    private val gson = Gson()
    private const val API_URL = "https://models.github.ai/inference/chat/completions"

    /**
     * Sends a diff to a GitHub-hosted model for review and returns a list of findings.
     * Returns an empty list on failure (never throws).
     */
    fun reviewDiff(
        filePath: String,
        fileContent: String,
        diff: String,
        maxDiffLines: Int
    ): List<ReviewFinding> {
        val settings = AiReviewSettings.getInstance()
        val token = settings.apiKey

        if (token.isBlank()) {
            LOG.warn("GitHub token not configured. Go to Settings > Tools > AI Code Review.")
            return emptyList()
        }

        val truncatedDiff = truncateDiff(diff, maxDiffLines)
        val prompt = buildPrompt(filePath, fileContent, truncatedDiff)

        return try {
            val responseBody = callApi(token, settings.modelName, prompt)
            parseFindings(responseBody)
        } catch (e: Exception) {
            LOG.warn("GitHub Models API call failed for $filePath", e)
            emptyList()
        }
    }

    private fun buildPrompt(filePath: String, fileContent: String, diff: String): String {
        val totalLines = fileContent.lines().size
        return """
You are a senior code reviewer. Review the following git diff for the file "$filePath" ($totalLines lines total).

Focus on:
1. **Bugs**: Logic errors, null safety issues, race conditions, resource leaks
2. **Warnings**: Code smells, potential performance issues, missing error handling, DDD violations
3. **Info**: Style improvements, naming suggestions, better Kotlin idioms

IMPORTANT: Only report findings for lines that are CHANGED in the diff (lines starting with +).
Map each finding to the actual line number in the full file.

Respond ONLY with a JSON array. No markdown, no explanation, no code fences. Just the raw JSON array.
Each element must have:
- "line": integer (1-based line number in the full file)
- "severity": "ERROR" | "WARNING" | "INFO"
- "message": string (concise description of the issue)
- "suggestion": string or null (how to fix it)

If there are no findings, respond with: []

Current file content:
```
${fileContent.lines().mapIndexed { i, l -> "${i + 1}: $l" }.joinToString("\n")}
```

Git diff:
```
$diff
```
""".trimIndent()
    }

    private fun callApi(token: String, model: String, prompt: String): String {
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "max_tokens" to 4096,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to prompt)
                )
            )
        )

        val connection = URI(API_URL).toURL().openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
        }

        connection.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            LOG.warn("GitHub Models API returned $responseCode: $error")
            throw RuntimeException("GitHub Models API error $responseCode: $error")
        }

        return responseBody
    }

    internal fun parseFindings(responseBody: String): List<ReviewFinding> {
        return try {
            val root = JsonParser.parseString(responseBody).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) return emptyList()

            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val text = message.get("content").asString.trim()
            LOG.info("API response text: ${text.take(500)}")

            parseJsonFindings(text)
        } catch (e: Exception) {
            LOG.warn("Failed to parse GitHub Models response", e)
            emptyList()
        }
    }

    internal fun parseJsonFindings(text: String): List<ReviewFinding> {
        val cleaned = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleaned == "[]") return emptyList()

        return try {
            val array = JsonParser.parseString(cleaned).asJsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    ReviewFinding(
                        line = obj.get("line").asInt,
                        severity = parseSeverity(obj.get("severity").asString),
                        message = obj.get("message").asString,
                        suggestion = obj.get("suggestion")?.takeIf { !it.isJsonNull }?.asString
                    )
                } catch (e: Exception) {
                    LOG.debug("Skipping malformed finding: $element")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse findings JSON: ${cleaned.take(200)}", e)
            emptyList()
        }
    }

    private fun parseSeverity(value: String): Severity {
        return when (value.uppercase()) {
            "ERROR" -> Severity.ERROR
            "WARNING" -> Severity.WARNING
            "INFO" -> Severity.INFO
            else -> Severity.INFO
        }
    }

    private fun truncateDiff(diff: String, maxLines: Int): String {
        val lines = diff.lines()
        return if (lines.size > maxLines) {
            lines.take(maxLines).joinToString("\n") + "\n... (truncated, ${lines.size - maxLines} lines omitted)"
        } else diff
    }
}
