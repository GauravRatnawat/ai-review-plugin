package com.aireview.plugin.model

/**
 * Holds the collected context needed for the external annotator's background phase.
 */
data class AiReviewInfo(
    val filePath: String,
    val fileContent: String,
    val gitDiff: String?,
    val contentHash: String
)
