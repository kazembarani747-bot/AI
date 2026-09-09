package com.kazembarani.ai.local

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** Downloads only pre-resolved toolchain artifacts into a staging directory. */
class TrustedDownloader(private val bootstrap: ToolchainBootstrap) {
    data class Result(val file: File, val bytes: Long)

    companion object {
        private const val MAX_ARTIFACT_BYTES = 2L * 1024 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private val TRUSTED_HOSTS = setOf("dl.google.com", "developer.android.com", "services.gradle.org")
    }

    fun download(spec: ToolchainBootstrap.DownloadSpec): Result {
        require(spec.sizeBytes in 1..MAX_ARTIFACT_BYTES) { "اندازه مؤلفه نامعتبر یا بیش از حد مجاز است: ${spec.id}" }
        val uri = URI(spec.sourceUrl)
        require(uri.scheme.equals("https", ignoreCase = true)) { "دانلود فقط با HTTPS مجاز است." }
        require(isTrustedHost(uri.host)) { "میزبان Toolchain مورد اعتماد نیست: ${uri.host}" }

        val staging = bootstrap.stagingDirectory().canonicalFile
        val target = File(staging, "${safeName(spec.id)}.part").canonicalFile
        require(target.parentFile?.canonicalFile == staging) { "مسیر staging نامعتبر است." }

        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = false
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "دانلود ${spec.id} ناموفق بود: HTTP ${connection.responseCode}" }
            val contentLength = connection.contentLengthLong
            require(contentLength <= 0L || contentLength == spec.sizeBytes) { "اندازه دریافتی با manifest سازگار نیست." }

            var total = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= spec.sizeBytes) { "فایل ${spec.id} بزرگ‌تر از manifest است." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(total == spec.sizeBytes) { "اندازه نهایی ${spec.id} برابر manifest نیست." }
            require(DownloadVerifier.verify(target, spec.sha256, spec.sizeBytes)) { "SHA-256 مؤلفه ${spec.id} معتبر نیست." }

            val verified = File(staging, safeName(spec.id)).canonicalFile
            require(verified.parentFile?.canonicalFile == staging)
            if (verified.exists()) verified.delete()
            require(target.renameTo(verified)) { "انتقال فایل تأییدشده ناموفق بود." }
            return Result(verified, total)
        } finally {
            connection.disconnect()
            if (target.exists()) target.delete()
        }
    }

    private fun safeName(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
    private fun isTrustedHost(host: String?): Boolean = host != null && TRUSTED_HOSTS.any { host == it || host.endsWith(".$it") }
}
