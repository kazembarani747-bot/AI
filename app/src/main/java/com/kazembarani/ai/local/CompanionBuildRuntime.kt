package com.kazembarani.ai.local

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Trusted-runtime client: generated code never gets executed by the Android app itself. */
class CompanionBuildRuntime(private val baseUrl: String = "http://127.0.0.1:8787") : BuildRuntime {
    private var activeSessionId: String? = null

    private fun request(path: String, payload: JSONObject? = null, timeoutMs: Int = 1_200_000): JSONObject {
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (payload == null) "GET" else "POST"
            connectTimeout = 10_000
            readTimeout = timeoutMs
            doInput = true
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
        }
        payload?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes -> c.outputStream.use { it.write(bytes) } }
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (c.responseCode !in 200..299) error("Runtime HTTP ${c.responseCode}: $text")
        return JSONObject(text)
    }

    override fun capabilities(): BuildRuntime.Capabilities = runCatching {
        val r = request("/health")
        BuildRuntime.Capabilities(
            r.optBoolean("ok"),
            r.optBoolean("build"),
            r.optBoolean("install"),
            r.optBoolean("test"),
            r.optBoolean("logcat"),
            r.optBoolean("screenshot"),
            "Companion Runtime متصل است؛ انتقال تکه‌ای پروژه فعال است."
        )
    }.getOrElse {
        BuildRuntime.Capabilities(false, false, false, false, false, false, "Companion Runtime متصل نیست: ${it.message}")
    }

    private fun startTransfer(project: File): String {
        val manifest = JSONArray()
        project.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = project.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            require(rel.isNotBlank() && !rel.contains(".."))
            manifest.put(JSONObject().put("path", rel).put("bytes", file.length()))
        }
        val r = request(
            "/transfer/start",
            JSONObject().put("projectName", project.name).put("files", manifest),
            60_000
        )
        return r.getString("sessionId")
    }

    private fun transferFile(sessionId: String, project: File, file: File, chunkBytes: Int = 512 * 1024) {
        val rel = project.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
        val total = file.length()
        RandomAccessFile(file, "r").use { raf ->
            var offset = 0L
            while (offset < total) {
                val length = minOf(chunkBytes.toLong(), total - offset).toInt()
                val bytes = ByteArray(length)
                raf.seek(offset)
                raf.readFully(bytes)
                var sent = false
                var lastError: Throwable? = null
                repeat(3) {
                    try {
                        val payload = JSONObject()
                            .put("sessionId", sessionId)
                            .put("path", rel)
                            .put("offset", offset)
                            .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        val result = request("/transfer/chunk", payload, 120_000)
                        val next = result.getLong("fileReceived")
                        require(next == offset + length) { "Runtime returned unexpected file offset." }
                        offset = next
                        sent = true
                    } catch (e: Throwable) {
                        lastError = e
                    }
                    if (sent) return@repeat
                }
                if (!sent) throw IllegalStateException("Chunk transfer failed for $rel at offset $offset: ${lastError?.message}")
            }
        }
    }

    private fun transferProject(project: File): String {
        val sessionId = startTransfer(project)
        project.walkTopDown().filter { it.isFile }.forEach { file -> transferFile(sessionId, project, file) }
        request("/transfer/finalize", JSONObject().put("sessionId", sessionId), 60_000)
        activeSessionId = sessionId
        return sessionId
    }

    override fun build(project: File, tasks: List<String>): BuildRuntime.CommandResult = runCatching {
        val sessionId = transferProject(project)
        val r = request("/build-session", JSONObject().put("sessionId", sessionId).put("tasks", JSONArray(tasks)), 1_200_000)
        val remote = r.optString("artifact").takeIf { it.isNotBlank() }
        val artifact = remote?.let { downloadArtifact(it, File(project, "app/build/outputs/apk/debug/app-debug.apk")) }
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"), artifact)
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime build/transfer error: ${it.message}") }

    override fun test(project: File, tasks: List<String>): BuildRuntime.CommandResult = runCatching {
        val sessionId = activeSessionId ?: transferProject(project)
        val r = request("/test-session", JSONObject().put("sessionId", sessionId).put("tasks", JSONArray(tasks)), 1_200_000)
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime test error: ${it.message}") }

    override fun install(apk: File): BuildRuntime.CommandResult = runCatching {
        // APKs are normally much smaller than a project; keep the existing bounded upload path for now.
        val encoded = Base64.encodeToString(apk.readBytes(), Base64.NO_WRAP)
        val r = request("/install", JSONObject().put("base64", encoded), 300_000)
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime install error: ${it.message}") }

    override fun captureLogcat(): BuildRuntime.CommandResult = runCatching {
        val r = request("/logcat", null, 60_000)
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime logcat error: ${it.message}") }

    override fun captureScreenshot(output: File): BuildRuntime.CommandResult = runCatching {
        val r = request("/screenshot", JSONObject().put("name", output.name), 60_000)
        if (r.optInt("exitCode", 1) == 0) {
            output.parentFile?.mkdirs()
            output.writeBytes(Base64.decode(r.optString("base64"), Base64.DEFAULT))
        }
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"), output.takeIf { it.isFile })
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime screenshot error: ${it.message}") }

    private fun downloadArtifact(remotePath: String, target: File): File {
        val c = (URL(baseUrl.trimEnd('/') + "/artifact").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 300_000
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(JSONObject().put("path", remotePath).toString().toByteArray(Charsets.UTF_8)) }
        if (c.responseCode !in 200..299) error("artifact HTTP ${c.responseCode}")
        target.parentFile?.mkdirs()
        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
