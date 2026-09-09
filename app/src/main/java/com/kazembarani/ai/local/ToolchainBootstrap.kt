package com.kazembarani.ai.local

import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Safe planning layer for the on-device toolchain bootstrap.
 *
 * This class only detects capabilities and produces verified download metadata.
 * It deliberately does not execute downloaded binaries. Android 10+ restricts
 * execution from writable app-home directories, so the actual execution runtime
 * must be supplied by a compatible, user-approved environment.
 */
class ToolchainBootstrap(private val workspace: File) {
    data class DeviceProfile(
        val abi: String,
        val supportedAbis: List<String>,
        val apiLevel: Int,
        val freeBytes: Long
    )

    data class DownloadSpec(
        val id: String,
        val name: String,
        val sourceUrl: String,
        val sha256: String,
        val sizeBytes: Long,
        val destination: String,
        val executable: Boolean = false
    )

    data class BootstrapPlan(
        val schemaVersion: Int,
        val device: DeviceProfile,
        val requiredBytes: Long,
        val downloads: List<DownloadSpec>,
        val warnings: List<String>
    )

    fun inspect(): DeviceProfile {
        val stat = StatFs(workspace.path)
        val abis = Build.SUPPORTED_ABIS.toList()
        return DeviceProfile(
            abi = abis.firstOrNull() ?: Build.CPU_ABI,
            supportedAbis = abis,
            apiLevel = Build.VERSION.SDK_INT,
            freeBytes = stat.availableBytes
        )
    }

    /**
     * Builds a manifest from trusted, already-resolved download descriptors.
     * No URL is accepted from generated project code.
     */
    fun plan(downloads: List<DownloadSpec>, reserveBytes: Long = 512L * 1024 * 1024): BootstrapPlan {
        require(downloads.size <= 32) { "تعداد مؤلفه‌های Toolchain بیش از حد مجاز است." }
        downloads.forEach { spec ->
            require(spec.sourceUrl.startsWith("https://")) { "منبع Toolchain باید HTTPS باشد: ${spec.id}" }
            require(spec.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "SHA-256 نامعتبر است: ${spec.id}" }
            require(spec.sizeBytes >= 0) { "اندازه نامعتبر است: ${spec.id}" }
            require(spec.destination.isNotBlank() && !spec.destination.contains("..")) {
                "مسیر مقصد نامعتبر است: ${spec.id}"
            }
        }
        val required = downloads.sumOf { it.sizeBytes } + reserveBytes
        val device = inspect()
        val warnings = buildList {
            if (device.freeBytes < required) add("فضای آزاد کافی نیست: حداقل ${required / (1024 * 1024)} MiB لازم است.")
            if (device.apiLevel >= 29) add("اجرای مستقیم باینری دانلودشده از app home روی Android 10+ مجاز نیست؛ Runtime جداگانه لازم است.")
        }
        return BootstrapPlan(1, device, required, downloads, warnings)
    }

    fun stagingDirectory(): File = File(workspace, "toolchain-staging").apply { mkdirs() }
}
