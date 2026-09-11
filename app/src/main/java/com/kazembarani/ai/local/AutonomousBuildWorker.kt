package com.kazembarani.ai.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kazembarani.ai.BuildConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.UUID

/** Runs the autonomous build loop as durable Android background work. */
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

        fun save(state: String, output: String) {
            store.save(
                AutonomousJobStore.Record(
                    id = jobId,
                    request = request,
                    budgetMinutes = budget.minutes,
                    install = install,
                    state = state,
                    output = output,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        if (request.isBlank()) {
            save("FAILED", "درخواست ساخت خالی است.")
            return Result.failure()
        }

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
                    save(
                        if (progress.success) "${progress.stage}_OK" else "${progress.stage}_FAILED",
                        progress.output
                    )
                }
            )
            save(if (result.success) "SUCCEEDED" else "FAILED", result.finalOutput)
            if (result.success) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            save("CANCELLED", "کار خودکار لغو شد.")
            throw e
        } catch (e: Exception) {
            save("FAILED", e.message ?: "خطای نامشخص")
            Result.failure()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_REQUEST = "request"
        const val KEY_BUDGET = "budget_minutes"
        const val KEY_INSTALL = "install"
    }
}
