package com.kazembarani.ai.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kazembarani.ai.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

/** Runs the autonomous build loop as durable foreground Android work. */
class AutonomousBuildWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: UUID.randomUUID().toString()
        val request = inputData.getString(KEY_REQUEST)?.trim().orEmpty()
        val budgetMinutes = inputData.getInt(KEY_BUDGET, 10)
        val install = inputData.getBoolean(KEY_INSTALL, false)
        val budget = AutonomousWorkLoop.WorkBudget.values().firstOrNull { it.minutes == budgetMinutes }
            ?: AutonomousWorkLoop.WorkBudget.MINUTES_10
        val store = AutonomousJobStore(applicationContext)

        fun save(state: String, output: String, apkPath: String? = store.load(jobId)?.apkPath) {
            store.save(
                AutonomousJobStore.Record(
                    id = jobId,
                    request = request,
                    budgetMinutes = budget.minutes,
                    install = install,
                    state = state,
                    output = output,
                    apkPath = apkPath,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        if (request.isBlank()) {
            save("FAILED", "درخواست ساخت خالی است.")
            return Result.failure()
        }

        setForeground(createForegroundInfo(jobId, "ساخت خودکار AI در حال اجراست…"))
        save("RUNNING", "کار خودکار در پس‌زمینه شروع شد.")
        return try {
            val agent = LocalBuildAgent(applicationContext)
            val coordinator = AutonomousBuildCoordinator(
                agent = agent,
                runtime = CompanionBuildRuntime(),
                backendUrl = BuildConfig.AI_API_URL.substringBeforeLast("/v1/chat").trimEnd('/')
            )
            val result = coordinator.run(
                request = request,
                budget = budget,
                install = install,
                onProgress = { progress ->
                    val apk = findLatestApk(agent.workspace)
                    save(
                        if (progress.success) "${progress.stage}_OK" else "${progress.stage}_FAILED",
                        progress.output,
                        apk?.absolutePath
                    )
                    setForeground(createForegroundInfo(jobId, "${progress.stage}: ${if (progress.success) "موفق" else "در حال اصلاح"}"))
                }
            )
            val apk = findLatestApk(agent.workspace)
            save(if (result.success) "SUCCEEDED" else "FAILED", result.finalOutput, apk?.absolutePath)
            if (result.success) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            save("CANCELLED", "کار خودکار لغو شد.")
            throw e
        } catch (e: Exception) {
            save("FAILED", e.message ?: "خطای نامشخص")
            Result.failure()
        }
    }

    private fun createForegroundInfo(jobId: String, text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI Builder", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AI Builder")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        // Stage 6: explicitly declare the data-sync foreground-service type on Android 10+.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun findLatestApk(workspace: File): File? = runCatching {
        workspace.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".apk") }
            .maxByOrNull { it.lastModified() }
            ?.takeIf { it.length() > 0L }
    }.getOrNull()

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_REQUEST = "request"
        const val KEY_BUDGET = "budget_minutes"
        const val KEY_INSTALL = "install"
        private const val CHANNEL_ID = "ai_builder_foreground"
        private const val NOTIFICATION_ID = 4705
    }
}
