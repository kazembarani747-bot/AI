package com.kazembarani.ai.local

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Local Android build-agent foundation.
 *
 * Android apps are sandboxed, so arbitrary native executables downloaded at runtime
 * cannot simply be executed from the writable app directory on modern Android.
 * This class therefore provides a safe, capability-based abstraction first: it
 * manages project files, records build plans, and exposes toolchain detection.
 * A compatible user-space runtime/toolchain can be plugged in later without
 * changing the UI or AI protocol.
 */
class LocalBuildAgent(private val context: Context) {
    data class ToolchainStatus(
        val workspace: File,
        val hasJdk: Boolean,
        val hasAndroidSdk: Boolean,
        val hasGradle: Boolean,
        val hasAdb: Boolean,
        val readyForBuild: Boolean,
        val note: String
    )

    data class BuildResult(
        val success: Boolean,
        val exitCode: Int?,
        val output: String,
        val apk: File? = null
    )

    val workspace: File = File(context.filesDir, "ai-workspace").apply { mkdirs() }

    fun inspectToolchain(): ToolchainStatus {
        val jdk = File(workspace, "toolchain/jdk/bin/java")
        val sdk = File(workspace, "toolchain/android-sdk/platform-tools/adb")
        val gradle = File(workspace, "toolchain/gradle/bin/gradle")
        val adb = File(workspace, "toolchain/android-sdk/platform-tools/adb")
        val ready = jdk.exists() && sdk.parentFile?.exists() == true && gradle.exists() && adb.exists()
        return ToolchainStatus(
            workspace = workspace,
            hasJdk = jdk.exists(),
            hasAndroidSdk = sdk.parentFile?.exists() == true,
            hasGradle = gradle.exists(),
            hasAdb = adb.exists(),
            readyForBuild = ready,
            note = if (ready) "ابزارهای محلی آماده‌اند." else "محیط Build محلی هنوز نصب نشده است."
        )
    }

    fun createProject(name: String, files: Map<String, String>): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "نام پروژه نامعتبر است." }
        val project = File(workspace, "projects/$name").canonicalFile
        val root = File(workspace, "projects").canonicalFile
        require(project.path.startsWith(root.path + File.separator))
        files.forEach { (relative, content) ->
            val target = File(project, relative).canonicalFile
            require(target.path.startsWith(project.path + File.separator))
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return project
    }

    /** Execute only a command supplied by the future allow-listed build runner. */
    fun runAllowlistedCommand(command: List<String>, timeoutSeconds: Long = 180): BuildResult {
        require(command.isNotEmpty())
        val allowed = setOf("gradle", "./gradlew", "adb", "java", "android")
        require(command.first() in allowed) { "دستور برای Build Agent مجاز نیست." }
        return try {
            val process = ProcessBuilder(command)
                .directory(workspace)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                BuildResult(false, null, output + "\nTimeout")
            } else {
                BuildResult(process.exitValue() == 0, process.exitValue(), output)
            }
        } catch (e: Exception) {
            BuildResult(false, null, e.message ?: e.javaClass.simpleName)
        }
    }
}
