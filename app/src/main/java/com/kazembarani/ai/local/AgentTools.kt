package com.kazembarani.ai.local

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/** Small, real tool layer used by the agent. Tools are deliberately capability-scoped. */
object AgentTools {
    data class ToolInfo(val name: String, val description: String)

    fun catalog(): List<ToolInfo> = listOf(
        ToolInfo("web_search", "Search the web through the OpenAI Responses API when enabled."),
        ToolInfo("phone_file_read", "Read a user-selected text/code file through Android SAF."),
        ToolInfo("workspace", "Create and manage files in the app workspace."),
        ToolInfo("build_test", "Queue a real background build/test job."),
        ToolInfo("memory", "Use private on-device conversation memory for continuity.")
    )

    fun readTextFile(context: Context, uri: Uri, maxChars: Int = 200_000): String {
        require(maxChars in 1..1_000_000) { "maxChars out of range" }
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8192)
                while (out.length < maxChars) {
                    val n = reader.read(buffer)
                    if (n <= 0) break
                    val remaining = maxChars - out.length
                    out.append(buffer, 0, minOf(n, remaining))
                }
                return out.toString()
            }
        } ?: throw IllegalStateException("فایل قابل خواندن نیست.")
    }

    fun buildToolPrompt(userRequest: String, fileContext: String? = null): String = buildString {
        append("You are an Android development agent. Use the available capabilities honestly.\n")
        append("Available tools: web_search, phone_file_read, workspace, build_test, memory.\n")
        append("Never claim a tool ran unless the app actually ran it.\n\n")
        append("User request:\n").append(userRequest)
        if (!fileContext.isNullOrBlank()) {
            append("\n\nSelected file context:\n").append(fileContext.take(200_000))
        }
    }
}
