package com.kazembarani.ai.local

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Verifies staged toolchain archives before they can be considered usable. */
object DownloadVerifier {
    fun sha256(file: File): String {
        require(file.isFile) { "فایل برای بررسی وجود ندارد: ${file.path}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSha256: String, expectedSizeBytes: Long): Boolean {
        if (!file.isFile || file.length() != expectedSizeBytes) return false
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }
}
