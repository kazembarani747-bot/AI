package com.kazembarani.ai.local

import android.content.Context
import java.io.File

/**
 * Probes an APK-embedded native runtime without attempting to execute arbitrary
 * files from writable app storage. A future native launcher can live in
 * nativeLibraryDir and expose a stable command bridge to the Kotlin layer.
 */
class EmbeddedRuntimeProbe(private val context: Context) {
    data class Status(
        val available: Boolean,
        val launcher: File?,
        val note: String
    )

    fun inspect(): Status {
        val dir = File(context.applicationInfo.nativeLibraryDir)
        val candidates = listOf("libai_runtime.so", "liblocal_runtime.so")
            .map { File(dir, it) }
        val launcher = candidates.firstOrNull { it.isFile && it.length() > 0L }
        return if (launcher != null) {
            Status(true, launcher, "Runtime بومی داخل APK پیدا شد.")
        } else {
            Status(
                false,
                null,
                "Runtime بومی هنوز داخل APK قرار نگرفته است؛ فایل اجرایی نباید از app storage اجرا شود."
            )
        }
    }
}
