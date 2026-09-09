package com.kazembarani.ai.local

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** Uses Android's PackageInstaller and reports when system approval is required. */
class ApkInstaller(private val context: Context) {
    data class InstallRequest(val sessionId: Int)

    sealed interface InstallResult {
        data class Success(val message: String = "APK با موفقیت نصب شد.") : InstallResult
        data class UserActionRequired(val intent: Intent) : InstallResult
        data class Failure(val message: String) : InstallResult
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun stageAndCommit(apk: File, resultReceiver: IntentSender): InstallRequest {
        require(apk.isFile && apk.extension.equals("apk", ignoreCase = true)) { "APK معتبر نیست." }
        require(apk.length() > 0) { "APK خالی است." }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(resultReceiver)
        } catch (error: Throwable) {
            runCatching { session.abandon() }
            throw error
        } finally {
            session.close()
        }
        return InstallRequest(sessionId)
    }

    fun describeStatus(status: Int, message: String?, pendingIntent: Intent?): InstallResult = when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallResult.Success()
        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
            if (pendingIntent != null) InstallResult.UserActionRequired(pendingIntent)
            else InstallResult.Failure(message ?: "تأیید کاربر برای نصب لازم است.")
        }
        else -> InstallResult.Failure(message ?: "نصب APK ناموفق بود (status=$status).")
    }
}
