package com.kazembarani.ai.local

import android.content.Context
import android.os.StatFs
import java.io.File

/** Capability-based on-device build agent; execution is delegated to a trusted runtime adapter. */
class LocalBuildAgent(val context: Context) {
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

    data class BuildResult(val success: Boolean, val output: String, val apk: File? = null)

    sealed interface OperationResult {
        data class Success(val message: String = "عملیات با موفقیت انجام شد.") : OperationResult
        data class Failure(val message: String) : OperationResult
        data object Unsupported : OperationResult
    }

    val workspace: File = File(context.filesDir, "ai-workspace").apply { mkdirs() }
    private val toolchainDir: File = File(workspace, "toolchain")
    val projectStore: ProjectStore by lazy { ProjectStore(this) }
    val toolchainBootstrap: ToolchainBootstrap by lazy { ToolchainBootstrap(workspace) }
    val trustedDownloader: TrustedDownloader by lazy { TrustedDownloader(toolchainBootstrap) }

    fun inspectToolchain(): ToolchainStatus {
        val jdk = File(toolchainDir, "jdk/bin/java")
        val sdkRoot = File(toolchainDir, "android-sdk")
        val sdkManager = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager")
        val buildTools = File(sdkRoot, "build-tools")
        val gradle = File(toolchainDir, "gradle/bin/gradle")
        val adb = File(sdkRoot, "platform-tools/adb")
        val androidCli = File(toolchainDir, "android-cli/bin/android")
        val freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
        val hasSdk = sdkRoot.isDirectory && sdkManager.isFile && buildTools.isDirectory
        val ready = jdk.isFile && hasSdk && gradle.isFile && adb.isFile
        return ToolchainStatus(
            workspace, freeBytes, jdk.isFile, hasSdk, gradle.isFile, adb.isFile, androidCli.isFile, ready,
            when {
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
            require(relative.isNotBlank() && !relative.startsWith("/") && !relative.contains("..")) { "مسیر فایل نامعتبر است: $relative" }
            val target = File(project, relative).canonicalFile
            require(target.path.startsWith(project.path + File.separator))
            require(content.length <= 2_000_000) { "فایل پروژه بیش از حد بزرگ است: $relative" }
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return project
    }

    fun prepareBuild(plan: BuildPlan): File = createProject(plan.projectName, plan.files)

    fun execute(operation: LocalBuildOperation): OperationResult = when (operation) {
        is LocalBuildOperation.PrepareProject -> runCatching {
            projectStore.save(operation.plan)
            OperationResult.Success("پروژه آماده و ذخیره شد.")
        }.getOrElse { OperationResult.Failure(it.message ?: "ساخت پروژه ناموفق بود.") }
        LocalBuildOperation.BuildDebug, LocalBuildOperation.InstallApk,
        LocalBuildOperation.RunTests, LocalBuildOperation.CaptureLogcat,
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
