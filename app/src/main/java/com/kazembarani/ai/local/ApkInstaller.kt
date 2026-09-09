package com.kazembarani.ai.local

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import java.io.File

/** Uses Android's PackageInstaller for locally produced APKs. */
class ApkInstaller(private val context: Context) {
    data class InstallRequest(val sessionId: Int)

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
}
