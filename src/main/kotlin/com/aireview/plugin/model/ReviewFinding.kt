package com.aireview.plugin.model

/**
 * Represents a single AI review finding attached to a specific line in a file.
 */
data class ReviewFinding(
    val line: Int,
    val severity: Severity,
    val message: String,
    val suggestion: String? = null
)

enum class Severity {
    ERROR,   // Red underline — bugs, critical issues
    WARNING, // Yellow underline — code smells, potential problems
    INFO     // Blue underline — style, suggestions, improvements
}
