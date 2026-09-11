package com.kazembarani.ai.local

import java.io.File

/** Coordinates build/test/install and collects diagnostics for the next AI repair pass. */
class LocalBuildPipeline(
    private val agent: LocalBuildAgent,
    private val runtime: BuildRuntime = UnavailableBuildRuntime()
) {
    data class Result(val success: Boolean, val stage: Stage, val output: String, val apk: File? = null)
    enum class Stage { PREPARE, BUILD, TEST, INSTALL, COMPLETE, FAILED }

    fun run(plan: BuildPlan, install: Boolean = true): Result {
        val project = runCatching { agent.prepareBuild(plan) }.getOrElse {
            return Result(false, Stage.PREPARE, it.message ?: "آماده‌سازی پروژه ناموفق بود.")
        }
        val capabilities = runtime.capabilities()
        if (!capabilities.available || !capabilities.canBuild) return Result(false, Stage.BUILD, capabilities.note)

        val build = runtime.build(project, plan.buildTasks.ifEmpty { listOf("assembleDebug") })
        if (!build.success) return failed(Stage.BUILD, build.output, build.artifact, capabilities)
        val apk = build.artifact ?: findDebugApk(project)
        if (apk == null || !apk.isFile) return failed(Stage.BUILD, "Build موفق بود اما APK خروجی پیدا نشد.", null, capabilities)

        if (plan.testTasks.isNotEmpty()) {
            if (!capabilities.canTest) return failed(Stage.TEST, "Runtime تست Android را پشتیبانی نمی‌کند.", apk, capabilities)
            val test = runtime.test(project, plan.testTasks)
            if (!test.success) return failed(Stage.TEST, test.output, apk, capabilities)
        }
        if (install) {
            if (!capabilities.canInstall) return failed(Stage.INSTALL, "Runtime نصب APK را پشتیبانی نمی‌کند.", apk, capabilities)
            val installed = runtime.install(apk)
            if (!installed.success) return failed(Stage.INSTALL, installed.output, apk, capabilities)
        }
        return Result(true, Stage.COMPLETE, "Build و مراحل درخواستی با موفقیت انجام شد.", apk)
    }

    private fun failed(stage: Stage, message: String, apk: File?, caps: BuildRuntime.Capabilities): Result {
        val diagnostics = buildString {
            append(message)
            if (caps.canCaptureLogs) runtime.captureLogcat().let { append("\n\n--- LOGCAT ---\n").append(it.output) }
            if (caps.canCaptureScreenshots) {
                val shot = File(agent.workspace, "diagnostics/${System.currentTimeMillis()}.png")
                runtime.captureScreenshot(shot).let { if (it.success) append("\n\n--- SCREENSHOT ---\n").append(shot.absolutePath) }
            }
        }
        return Result(false, stage, diagnostics.takeLast(140_000), apk)
    }

    private fun findDebugApk(project: File): File? = File(project, "app/build/outputs/apk/debug/app-debug.apk").takeIf { it.isFile }
}
