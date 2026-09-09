package com.kazembarani.ai.local

import java.io.File

/** Coordinates the deterministic local build/test sequence without exposing a free-form shell. */
class LocalBuildPipeline(
    private val agent: LocalBuildAgent,
    private val runtime: BuildRuntime = UnavailableBuildRuntime()
) {
    data class Result(
        val success: Boolean,
        val stage: Stage,
        val output: String,
        val apk: File? = null
    )

    enum class Stage { PREPARE, BUILD, TEST, INSTALL, COMPLETE, FAILED }

    fun run(plan: BuildPlan, install: Boolean = true): Result {
        val project = runCatching { agent.prepareBuild(plan) }.getOrElse {
            return Result(false, Stage.PREPARE, it.message ?: "آماده‌سازی پروژه ناموفق بود.")
        }
        val capabilities = runtime.capabilities()
        if (!capabilities.available || !capabilities.canBuild) {
            return Result(false, Stage.BUILD, capabilities.note)
        }

        val build = runtime.build(project, plan.buildTasks.ifEmpty { listOf("assembleDebug") })
        if (!build.success) return Result(false, Stage.BUILD, build.output, build.artifact)

        val apk = build.artifact ?: findDebugApk(project)
        if (apk == null || !apk.isFile) {
            return Result(false, Stage.BUILD, "Build موفق بود اما APK خروجی پیدا نشد.")
        }

        if (plan.testTasks.isNotEmpty()) {
            val test = runtime.test(project, plan.testTasks)
            if (!test.success) return Result(false, Stage.TEST, test.output, apk)
        }

        if (install) {
            if (!capabilities.canInstall) return Result(false, Stage.INSTALL, "Runtime نصب APK را پشتیبانی نمی‌کند.", apk)
            val installed = runtime.install(apk)
            if (!installed.success) return Result(false, Stage.INSTALL, installed.output, apk)
        }

        return Result(true, Stage.COMPLETE, "Build و مراحل درخواستی با موفقیت انجام شد.", apk)
    }

    private fun findDebugApk(project: File): File? =
        File(project, "app/build/outputs/apk/debug/app-debug.apk").takeIf { it.isFile }
}
