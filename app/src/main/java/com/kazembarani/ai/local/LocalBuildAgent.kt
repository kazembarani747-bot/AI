package com.kazembarani.ai.local

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Capability-based on-device build agent.
 *
 * Important: modern Android does not allow an ordinary app targeting API 29+
 * to execute arbitrary native binaries from its writable app home directory.
 * Therefore this class deliberately does NOT expose a generic shell executor.
 * The next runtime adapter can implement these typed operations using a
 * compatible, user-approved execution environment.
 */
class LocalBuildAgent(private val context: Context) {
    data class ToolchainStatus(
        val workspace: File,
        val freeBytes: Long,
        val hasJdk: Boolean,
        val hasAndroidSdk: Boolean,
        val hasGradle: Boolean,
        val hasAdb: Boolean,
        val hasAndroidCli: Boolean,
        val readyForBuild: Boolean,
        val note: String
    )

    data class BuildResult(
        val success: Boolean,
        val output: String,
        val apk: File? = null
    )

    sealed interface OperationResult {
        data class Success(val message: String = "عملیات با موفقیت انجام شد.") : OperationResult
        data class Failure(val message: String) : OperationResult
        data object Unsupported : OperationResult
    }

    val workspace: File = File(context.filesDir, "ai-workspace").apply { mkdirs() }
    private val toolchainDir: File = File(workspace, "toolchain")

    fun inspectToolchain(): ToolchainStatus {
        val jdk = File(toolchainDir, "jdk/bin/java")
        val sdkRoot = File(toolchainDir, "android-sdk")
        val sdkManager = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager")
        val buildTools = File(sdkRoot, "build-tools")
        val gradle = File(toolchainDir, "gradle/bin/gradle")
        val adb = File(sdkRoot, "platform-tools/adb")
        val androidCli = File(toolchainDir, "android-cli/bin/android")
        val freeBytes = try { StatFs(context.filesDir.path).availableBytes } catch (_: Exception) { 0L }

        val hasSdk = sdkRoot.isDirectory && sdkManager.exists() && buildTools.isDirectory
        val ready = jdk.isFile && hasSdk && gradle.isFile && adb.isFile
        return ToolchainStatus(
            workspace = workspace,
            freeBytes = freeBytes,
            hasJdk = jdk.isFile,
            hasAndroidSdk = hasSdk,
            hasGradle = gradle.isFile,
            hasAdb = adb.isFile,
            hasAndroidCli = androidCli.isFile,
            readyForBuild = ready,
            note = when {
                ready -> "ابزارهای اصلی محلی آماده‌اند."
                freeBytes in 1 until 512L * 1024 * 1024 -> "فضای آزاد برای Toolchain کم است."
                else -> "محیط Build محلی هنوز کامل نصب نشده است."
            }
        )
    }

    fun createProject(name: String, files: Map<String, String>): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "نام پروژه نامعتبر است." }
        require(files.size <= 2000) { "تعداد فایل‌های پروژه بیش از حد مجاز است." }

        val root = File(workspace, "projects").canonicalFile.apply { mkdirs() }
        val project = File(root, name).canonicalFile
        require(project.path.startsWith(root.path + File.separator))
        project.mkdirs()

        files.forEach { (relative, content) ->
            val target = File(project, relative).canonicalFile
            require(target.path.startsWith(project.path + File.separator))
            require(content.length <= 2_000_000) { "فایل پروژه بیش از حد بزرگ است: $relative" }
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return project
    }

    fun prepareBuild(plan: BuildPlan): File {
        return createProject(plan.projectName, plan.files)
    }

    /** Typed operation entry point. No arbitrary command/argument execution is exposed. */
    fun execute(operation: LocalBuildOperation): OperationResult = when (operation) {
        is LocalBuildOperation.PrepareProject -> try {
            prepareBuild(operation.plan)
            OperationResult.Success("پروژه آماده شد.")
        } catch (e: Exception) {
            OperationResult.Failure(e.message ?: "ساخت پروژه ناموفق بود.")
        }
        LocalBuildOperation.BuildDebug -> OperationResult.Unsupported
        LocalBuildOperation.InstallApk -> OperationResult.Unsupported
        LocalBuildOperation.RunTests -> OperationResult.Unsupported
        LocalBuildOperation.CaptureLogcat -> OperationResult.Unsupported
        LocalBuildOperation.CaptureScreenshot -> OperationResult.Unsupported
    }
}

sealed interface LocalBuildOperation {
    data class PrepareProject(val plan: BuildPlan) : LocalBuildOperation
    data object BuildDebug : LocalBuildOperation
    data object InstallApk : LocalBuildOperation
    data object RunTests : LocalBuildOperation
    data object CaptureLogcat : LocalBuildOperation
    data object CaptureScreenshot : LocalBuildOperation
}
