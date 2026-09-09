package com.kazembarani.ai.local

import java.io.File

/**
 * Narrow execution boundary for local Android builds/tests.
 * Implementations must be backed by a trusted, user-approved runtime.
 */
interface BuildRuntime {
    data class Capabilities(
        val available: Boolean,
        val canBuild: Boolean,
        val canInstall: Boolean,
        val canTest: Boolean,
        val canCaptureLogs: Boolean,
        val canCaptureScreenshots: Boolean,
        val note: String
    )

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val artifact: File? = null
    ) {
        val success: Boolean get() = exitCode == 0
    }

    fun capabilities(): Capabilities
    fun build(project: File, tasks: List<String>): CommandResult
    fun test(project: File, tasks: List<String>): CommandResult
    fun install(apk: File): CommandResult
    fun captureLogcat(): CommandResult
    fun captureScreenshot(output: File): CommandResult
}

/** Runtime placeholder used until a compatible execution environment is provisioned. */
class UnavailableBuildRuntime : BuildRuntime {
    override fun capabilities() = BuildRuntime.Capabilities(
        available = false,
        canBuild = false,
        canInstall = false,
        canTest = false,
        canCaptureLogs = false,
        canCaptureScreenshots = false,
        note = "Runtime اجرای محلی هنوز روی دستگاه provision نشده است."
    )

    private fun unavailable(operation: String) = BuildRuntime.CommandResult(
        exitCode = 126,
        output = "$operation: runtime محلی در دسترس نیست."
    )

    override fun build(project: File, tasks: List<String>) = unavailable("build")
    override fun test(project: File, tasks: List<String>) = unavailable("test")
    override fun install(apk: File) = unavailable("install")
    override fun captureLogcat() = unavailable("logcat")
    override fun captureScreenshot(output: File) = unavailable("screenshot")
}
