package com.aireview.plugin.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Executes git commands to retrieve unstaged diffs for a specific file.
 */
object GitDiffService {

    private val LOG = Logger.getInstance(GitDiffService::class.java)

    /**
     * Returns the unstaged diff for the given file, or null if no diff / not a git repo.
     */
    fun getUnstagedDiff(project: Project, file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        val relativePath = getRelativePath(basePath, file.path) ?: return null

        return try {
            val process = ProcessBuilder("git", "diff", "--", relativePath)
                .directory(File(basePath))
                .redirectErrorStream(false)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }

            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                LOG.warn("Git diff timed out for file: $relativePath")
                return null
            }

            if (process.exitValue() != 0) {
                LOG.debug("Git diff returned non-zero for $relativePath: $errorOutput")
                return null
            }

            output.ifBlank { null }
        } catch (e: Exception) {
            LOG.warn("Failed to get git diff for $relativePath", e)
            null
        }
    }

    /**
     * Returns the staged + unstaged combined diff (all local changes).
     */
    fun getAllLocalChanges(project: Project, file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        val relativePath = getRelativePath(basePath, file.path) ?: return null

        return try {
            val headDiff = executeGitCommand(basePath, "git", "diff", "HEAD", "--", relativePath)
            headDiff?.ifBlank { null }
        } catch (e: Exception) {
            LOG.warn("Failed to get HEAD diff for $relativePath", e)
            null
        }
    }

    private fun executeGitCommand(workDir: String, vararg command: String): String? {
        val process = ProcessBuilder(*command)
            .directory(File(workDir))
            .redirectErrorStream(false)
            .start()

        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val finished = process.waitFor(10, TimeUnit.SECONDS)

        if (!finished) {
            process.destroyForcibly()
            return null
        }

        return if (process.exitValue() == 0) output else null
    }

    private fun getRelativePath(basePath: String, filePath: String): String? {
        val base = File(basePath).canonicalPath
        val file = File(filePath).canonicalPath
        return if (file.startsWith(base)) {
            file.removePrefix(base).removePrefix(File.separator)
        } else null
    }
}
