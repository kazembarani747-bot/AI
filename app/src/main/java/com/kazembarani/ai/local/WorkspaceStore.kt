package com.kazembarani.ai.local

import android.content.Context
import java.io.File

/** Phone-side workspace for generated source/projects. Builds remain delegated to the real build runtime. */
object WorkspaceStore {
    fun root(context: Context): File = File(context.filesDir, "AI-Workspace").apply { mkdirs() }

    fun project(context: Context, name: String): File = File(root(context), sanitize(name)).apply { mkdirs() }

    fun writeText(context: Context, projectName: String, relativePath: String, content: String): File {
        val base = project(context, projectName).canonicalFile
        val target = File(base, relativePath).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "مسیر فایل نامعتبر است." }
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        return target
    }

    fun listProjects(context: Context): List<File> = root(context).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()

    private fun sanitize(value: String): String = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "Project" }
}
