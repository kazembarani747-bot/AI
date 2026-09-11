package com.kazembarani.ai.local

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Trusted-runtime client: generated code never gets executed by the Android app itself. */
class CompanionBuildRuntime(private val baseUrl: String = "http://127.0.0.1:8787") : BuildRuntime {
    private fun request(path: String, payload: JSONObject? = null, timeoutMs: Int = 1_200_000): JSONObject {
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (payload == null) "GET" else "POST"
            connectTimeout = 10_000; readTimeout = timeoutMs; doInput = true
            if (payload != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        payload?.toString()?.toByteArray(Charsets.UTF_8)?.let { c.outputStream.use { it.write(it) } }
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (c.responseCode !in 200..299) error("Runtime HTTP ${c.responseCode}: $text")
        return JSONObject(text)
    }

    override fun capabilities(): BuildRuntime.Capabilities = runCatching {
        val r = request("/health")
        BuildRuntime.Capabilities(r.optBoolean("ok"), r.optBoolean("build"), r.optBoolean("install"), r.optBoolean("test"), r.optBoolean("logcat"), r.optBoolean("screenshot"), "Companion Runtime متصل است.")
    }.getOrElse { BuildRuntime.Capabilities(false, false, false, false, false, false, "Companion Runtime متصل نیست: ${it.message}") }

    private fun projectPayload(project: File, tasks: List<String>): JSONObject {
        val files = JSONObject()
        project.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = project.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
            require(!rel.contains(".."))
            files.put(rel, file.readText())
        }
        return JSONObject().put("projectName", project.name).put("files", files).put("tasks", tasks)
    }

    override fun build(project: File, tasks: List<String>): BuildRuntime.CommandResult = runCatching {
        val r = request("/build", projectPayload(project, tasks))
        val remote = r.optString("artifact").takeIf { it.isNotBlank() }
        val artifact = remote?.let { downloadArtifact(it, File(project, "app/build/outputs/apk/debug/app-debug.apk")) }
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"), artifact)
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime build error: ${it.message}") }

    override fun test(project: File, tasks: List<String>): BuildRuntime.CommandResult = runCatching {
        val r = request("/test", JSONObject().put("projectName", project.name).put("tasks", tasks))
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime test error: ${it.message}") }

    override fun install(apk: File): BuildRuntime.CommandResult = runCatching {
        val encoded = Base64.getEncoder().encodeToString(apk.readBytes())
        val r = request("/install", JSONObject().put("base64", encoded), 300_000)
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime install error: ${it.message}") }

    override fun captureLogcat(): BuildRuntime.CommandResult = runCatching {
        val r = request("/logcat", null, 60_000)
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"))
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime logcat error: ${it.message}") }

    override fun captureScreenshot(output: File): BuildRuntime.CommandResult = runCatching {
        val r = request("/screenshot", JSONObject().put("name", output.name), 60_000)
        if (r.optInt("exitCode", 1) == 0) { output.parentFile?.mkdirs(); output.writeBytes(Base64.getDecoder().decode(r.optString("base64"))) }
        BuildRuntime.CommandResult(r.optInt("exitCode", 1), r.optString("output"), output.takeIf { it.isFile })
    }.getOrElse { BuildRuntime.CommandResult(1, "Runtime screenshot error: ${it.message}") }

    private fun downloadArtifact(remotePath: String, target: File): File {
        val c = (URL(baseUrl.trimEnd('/') + "/artifact").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 300_000; setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(JSONObject().put("path", remotePath).toString().toByteArray(Charsets.UTF_8)) }
        if (c.responseCode !in 200..299) error("artifact HTTP ${c.responseCode}")
        target.parentFile?.mkdirs(); c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
