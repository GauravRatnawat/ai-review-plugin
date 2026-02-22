package com.aireview.plugin.annotator

import com.aireview.plugin.cache.ReviewCacheService
import com.aireview.plugin.claude.ClaudeApiClient
import com.aireview.plugin.claude.CopilotApiClient
import com.aireview.plugin.git.GitDiffService
import com.aireview.plugin.model.AiReviewInfo
import com.aireview.plugin.model.ReviewFinding
import com.aireview.plugin.model.Severity
import com.aireview.plugin.settings.AiProvider
import com.aireview.plugin.settings.AiReviewSettings
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.security.MessageDigest

/**
 * ExternalAnnotator that pipes git diffs through the selected AI provider and shows findings inline.
 *
 * Three-phase lifecycle (all managed by IntelliJ):
 * 1. collectInformation() — EDT: gather file content, git diff, content hash
 * 2. doAnnotate()         — Background thread: call AI provider (or return cached results)
 * 3. apply()              — EDT: apply annotations (underlines, gutter icons, tooltips)
 */
class AiReviewExternalAnnotator : ExternalAnnotator<AiReviewInfo, List<ReviewFinding>>() {

    private val log = Logger.getInstance(AiReviewExternalAnnotator::class.java)

    /**
     * Phase 1: Collect info on the EDT.
     * Gathers file content, computes content hash, and retrieves the git diff.
     */
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): AiReviewInfo? {
        val settings = AiReviewSettings.getInstance()
        if (!settings.isEnabled) return null

        val project = file.project
        val virtualFile = file.virtualFile ?: return null

        // Skip non-project files, generated files, build output
        if (!isReviewableFile(virtualFile.path, project)) return null

        val fileContent = editor.document.text
        val contentHash = computeHash(fileContent)

        val diff = GitDiffService.getUnstagedDiff(project, virtualFile)
            ?: GitDiffService.getAllLocalChanges(project, virtualFile)

        if (diff.isNullOrBlank()) {
            log.debug("No git diff for ${virtualFile.name}, skipping review")
            return null
        }

        return AiReviewInfo(
            filePath = virtualFile.path,
            fileContent = fileContent,
            gitDiff = diff,
            contentHash = contentHash,
        )
    }

    /**
     * Phase 2: Run on background thread.
     * Checks cache first, then calls the configured AI provider if needed.
     */
    override fun doAnnotate(info: AiReviewInfo?): List<ReviewFinding>? {
        if (info == null) return null

        val cacheService = ApplicationManager.getApplication().getService(ReviewCacheService::class.java)

        // Check cache — if content hasn't changed, reuse previous findings
        val cached = cacheService.get(info.filePath, info.contentHash)
        if (cached != null) {
            log.debug("Cache hit for ${info.filePath} (${cached.size} findings)")
            return cached
        }

        val settings = AiReviewSettings.getInstance()
        val providerName = settings.provider.displayName
        log.info("Reviewing ${info.filePath} via $providerName...")

        val findings = when (settings.provider) {
            AiProvider.GITHUB_MODELS, AiProvider.GITHUB_MODELS_MINI -> CopilotApiClient.reviewDiff(
                filePath = info.filePath,
                fileContent = info.fileContent,
                diff = info.gitDiff ?: "",
                maxDiffLines = settings.maxDiffLines,
            )

            AiProvider.CLAUDE -> ClaudeApiClient.reviewDiff(
                filePath = info.filePath,
                fileContent = info.fileContent,
                diff = info.gitDiff ?: "",
                maxDiffLines = settings.maxDiffLines,
            )
        }

        // Cache the results
        cacheService.put(info.filePath, info.contentHash, findings)

        log.info("Review complete for ${info.filePath}: ${findings.size} findings ($providerName)")
        return findings
    }

    /**
     * Phase 3: Apply annotations on the EDT.
     * Maps findings to editor lines with appropriate severity styling.
     */
    override fun apply(file: PsiFile, findings: List<ReviewFinding>?, holder: AnnotationHolder) {
        if (findings.isNullOrEmpty()) return

        val document = file.viewProvider.document ?: return
        val totalLines = document.lineCount

        // Collect line offsets for inlays (computed now while we have the document)
        val inlayData = mutableListOf<Pair<Int, ReviewFinding>>()

        for (finding in findings) {
            val lineIndex = finding.line - 1
            if (lineIndex < 0 || lineIndex >= totalLines) continue

            val startOffset = document.getLineStartOffset(lineIndex)
            val endOffset = document.getLineEndOffset(lineIndex)
            if (startOffset == endOffset) continue

            val severity = mapSeverity(finding.severity)
            val highlightType = mapHighlightType(finding.severity)
            val textAttrKey = mapTextAttributes(finding.severity)
            val tooltip = buildTooltip(finding)

            // Underline annotation
            holder.newAnnotation(severity, "AI Review: ${finding.message}")
                .range(TextRange(startOffset, endOffset))
                .highlightType(highlightType)
                .textAttributes(textAttrKey)
                .tooltip(tooltip)
                .create()

            inlayData.add(endOffset to finding)
        }

        // Schedule inline inlays on EDT (InlayModel requires EDT access)
        if (inlayData.isNotEmpty()) {
            val virtualFile = file.virtualFile ?: return
            val project = file.project
            val docTextLength = document.textLength

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                val editor = FileEditorManager.getInstance(project)
                    .getEditors(virtualFile)
                    .filterIsInstance<TextEditor>()
                    .firstOrNull()
                    ?.editor ?: return@invokeLater

                // Clear previous AI review inlays
                editor.inlayModel
                    .getAfterLineEndElementsInRange(0, docTextLength)
                    .filter { it.renderer is InlineFindingRenderer }
                    .forEach { Disposer.dispose(it) }

                // Add new inlays
                for ((offset, finding) in inlayData) {
                    editor.inlayModel.addAfterLineEndElement(
                        offset,
                        true,
                        InlineFindingRenderer(finding)
                    )
                }
            }
        }
    }

    private fun mapSeverity(severity: Severity): HighlightSeverity {
        return when (severity) {
            Severity.ERROR -> HighlightSeverity.ERROR
            Severity.WARNING -> HighlightSeverity.WARNING
            Severity.INFO -> HighlightSeverity.WEAK_WARNING
        }
    }

    private fun mapHighlightType(severity: Severity): ProblemHighlightType {
        return when (severity) {
            Severity.ERROR -> ProblemHighlightType.GENERIC_ERROR
            Severity.WARNING -> ProblemHighlightType.WARNING
            Severity.INFO -> ProblemHighlightType.WEAK_WARNING
        }
    }

    private fun mapTextAttributes(severity: Severity) = when (severity) {
        Severity.ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES
        Severity.WARNING -> CodeInsightColors.WARNINGS_ATTRIBUTES
        Severity.INFO -> CodeInsightColors.WEAK_WARNING_ATTRIBUTES
    }

    private fun buildTooltip(finding: ReviewFinding): String {
        val sb = StringBuilder()
        sb.append("<html><body>")
        sb.append("<b>AI Code Review</b> [${finding.severity}]<br/>")
        sb.append(escapeHtml(finding.message))
        if (!finding.suggestion.isNullOrBlank()) {
            sb.append("<br/><br/><b>Suggestion:</b><br/>")
            sb.append(escapeHtml(finding.suggestion))
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br/>")
    }

    private fun computeHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun isReviewableFile(path: String, project: Project): Boolean {
        val basePath = project.basePath ?: return false

        // Must be inside project
        if (!path.startsWith(basePath)) return false

        // Skip build output and generated files
        val skipPatterns = listOf(
            "/build/", "/out/", "/.gradle/", "/.idea/",
            "/node_modules/", "/.git/", "/target/",
        )
        if (skipPatterns.any { path.contains(it) }) return false

        // Only review code files
        val reviewableExtensions = listOf(
            "kt", "java", "py", "js", "ts", "tsx", "jsx",
            "go", "rs", "scala", "groovy", "kts",
            "yaml", "yml", "json", "xml", "html", "css",
            "sql", "sh", "bash", "zsh",
            "md", "properties", "toml", "gradle",
        )
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension in reviewableExtensions
    }
}
