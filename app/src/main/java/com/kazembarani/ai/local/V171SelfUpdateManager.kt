package com.kazembarani.ai.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kazembarani.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Controlled self-update channel. It only downloads a public GitHub Release APK and then
 * hands installation to Android's PackageInstaller, so the OS/user remains in control.
 */
class V171SelfUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    data class ReleaseInfo(
        val tag: String,
        val name: String,
        val apkUrl: String,
        val sha256: String?
    )

    sealed interface CheckResult {
        data class UpdateAvailable(val release: ReleaseInfo) : CheckResult
        data class UpToDate(val tag: String) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    suspend fun checkLatest(): CheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("GitHub HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                val tag = root.optString("tag_name").ifBlank { throw IllegalStateException("Release tag خالی است.") }
                val name = root.optString("name", tag)
                val assets = root.optJSONArray("assets") ?: throw IllegalStateException("Release بدون APK است.")
                var apkUrl: String? = null
                var sha256: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name")
                    if (assetName == APK_ASSET) apkUrl = asset.optString("browser_download_url")
                    if (assetName == SHA_ASSET) sha256 = asset.optString("browser_download_url")
                }
                val url = apkUrl ?: throw IllegalStateException("APK نسخه جدید پیدا نشد.")
                val lastInstalled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_TAG, null)
                if (tag == lastInstalled) CheckResult.UpToDate(tag)
                else CheckResult.UpdateAvailable(ReleaseInfo(tag, name, url, sha256))
            }
        }.getOrElse { CheckResult.Failed(it.message ?: "بررسی نسخه جدید ناموفق بود.") }
    }

    suspend fun download(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "AI-${release.tag}.apk")
        val request = Request.Builder().url(release.apkUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("دانلود APK با HTTP ${response.code} شکست خورد.")
            val body = response.body ?: throw IllegalStateException("بدنه APK خالی است.")
            body.byteStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
        }
        require(apk.length() > 0) { "APK دانلودشده خالی است." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_TAG, release.tag).apply()
        apk
    }

    fun installIntent(apk: File): Intent {
        require(apk.isFile && apk.length() > 0) { "APK برای نصب موجود نیست." }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearOldUpdates(keep: File? = null) {
        File(context.filesDir, "updates").listFiles()?.forEach { file ->
            if (file != keep) runCatching { file.delete() }
        }
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/kazembarani747-bot/AI/releases/latest"
        private const val APK_ASSET = "app-debug.apk"
        private const val SHA_ASSET = "app-debug.apk.sha256"
        private const val PREFS = "v171_self_update"
        private const val KEY_LAST_TAG = "last_installed_release_tag"
    }
}
