package com.aireview.plugin.listener

import com.aireview.plugin.settings.AiReviewSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer

/**
 * Listens for file save events and triggers re-analysis via the ExternalAnnotator.
 *
 * When a file is saved, this listener forces IntelliJ's DaemonCodeAnalyzer to re-run,
 * which in turn invokes the AiReviewExternalAnnotator's three-phase lifecycle.
 */
class FileSaveListener : BulkFileListener {

    private val log = Logger.getInstance(FileSaveListener::class.java)

    override fun after(events: List<VFileEvent>) {
        val settings = AiReviewSettings.getInstance()
        if (!settings.isEnabled || !settings.reviewOnSave) return

        val changedFiles = events
            .filter { it.isFromSave }
            .mapNotNull { it.file }

        if (changedFiles.isEmpty()) return

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue

            val fileEditorManager = FileEditorManager.getInstance(project)
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)

            for (vFile in changedFiles) {
                if (!fileEditorManager.isFileOpen(vFile)) continue

                val document = FileDocumentManager.getInstance().getDocument(vFile) ?: continue
                val psiFile = psiDocumentManager.getPsiFile(document) ?: continue

                log.debug("File saved, triggering AI review: ${vFile.name}")
                daemonCodeAnalyzer.restart(psiFile, "AI Code Review: file saved")
            }
        }
    }
}
