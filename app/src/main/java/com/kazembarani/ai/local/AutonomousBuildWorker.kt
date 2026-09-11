package com.kazembarani.ai.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

/** Durable build loop. The selected trusted runtime performs compilation/testing; AI handles repair passes. */
class AutonomousBuildWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: UUID.randomUUID().toString()
        val request = inputData.getString(KEY_REQUEST)?.trim().orEmpty()
        var planJson = inputData.getString(KEY_PLAN_JSON).orEmpty()
        val budgetMinutes = inputData.getInt(KEY_BUDGET, 10).coerceIn(10, 60)
        val install = inputData.getBoolean(KEY_INSTALL, false)
        val store = AutonomousJobStore(applicationContext)
        fun save(state: String, output: String, apkPath: String? = store.load(jobId)?.apkPath) { store.save(AutonomousJobStore.Record(jobId, request, budgetMinutes, install, state, output, apkPath, System.currentTimeMillis())) }
        if (request.isBlank() || planJson.isBlank()) { save("FAILED", "پروژه یا BuildPlan اولیه وجود ندارد."); return Result.failure() }
        if (!ApiKeyStore.hasKey(applicationContext)) { save("FAILED", "کلید OpenAI روی دستگاه تنظیم نشده است."); return Result.failure() }
        setForeground(createForegroundInfo(jobId, "ساخت و تست آماده می‌شود…")); save("RUNNING", "ساخت خودکار شروع شد.")
        return try {
            val agent = LocalBuildAgent(applicationContext)
            val runtime = CompanionBuildRuntime()
            val capabilities = runtime.capabilities()
            if (!capabilities.available || !capabilities.canBuild) { save("WAITING_FOR_RUNTIME", "Runtime ساخت در دسترس نیست.\n${capabilities.note}"); return Result.failure() }
            val pipeline = LocalBuildPipeline(agent, runtime)
            val ai = OpenAiClient(applicationContext)
            val deadline = System.currentTimeMillis() + budgetMinutes * 60_000L
            var attempt = 0; var lastOutput = ""
            while (System.currentTimeMillis() < deadline && attempt < 8) {
                attempt++; setForeground(createForegroundInfo(jobId, "تلاش $attempt برای Build/Test…"))
                val plan = BuildPlan.fromJson(org.json.JSONObject(planJson)); plan.validate()
                val result = pipeline.run(plan, install); val apk = result.apk ?: findLatestApk(agent.workspace); lastOutput = result.output
                save(if (result.success) "SUCCEEDED" else "REPAIRING", "تلاش $attempt\n${result.output}", apk?.absolutePath)
                if (result.success) return Result.success()
                if (System.currentTimeMillis() >= deadline) break
                val repaired = ai.generateBuildPlan(request, result.output.takeLast(60_000)); planJson = repaired.toJson().toString()
            }
            save("FAILED", "زمان ساخت تمام شد.\n\n$lastOutput"); Result.failure()
        } catch (e: CancellationException) { save("CANCELLED", "کار خودکار لغو شد."); throw e }
        catch (e: Exception) { save("FAILED", e.message ?: "خطای نامشخص"); Result.failure() }
    }
    private fun createForegroundInfo(jobId: String, text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AI Builder", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("AI Builder").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_PROGRESS).build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIFICATION_ID, notification)
    }
    private fun findLatestApk(workspace: File): File? = runCatching { workspace.walkTopDown().filter { it.isFile && it.name.endsWith(".apk") }.maxByOrNull { it.lastModified() }?.takeIf { it.length() > 0L } }.getOrNull()
    companion object { const val KEY_JOB_ID = "job_id"; const val KEY_REQUEST = "request"; const val KEY_PLAN_JSON = "plan_json"; const val KEY_BUDGET = "budget_minutes"; const val KEY_INSTALL = "install"; private const val CHANNEL_ID = "ai_builder_foreground"; private const val NOTIFICATION_ID = 4705 }
}
