package com.kazembarani.ai.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounded autonomous orchestration loop. The loop itself does not execute arbitrary shell commands;
 * all build/test/install work stays behind BuildRuntime.
 */
class AutonomousWorkLoop(
    private val agent: LocalBuildAgent,
    private val runtime: BuildRuntime
) {
    enum class WorkBudget(val minutes: Int) {
        MINUTES_10(10),
        MINUTES_20(20),
        MINUTES_30(30),
        MINUTES_60(60)
    }

    data class Attempt(
        val number: Int,
        val stage: String,
        val success: Boolean,
        val output: String
    )

    data class Result(
        val success: Boolean,
        val attempts: List<Attempt>,
        val finalOutput: String
    )

    suspend fun run(
        planProvider: suspend (feedback: String?) -> BuildPlan,
        budget: WorkBudget,
        install: Boolean = false,
        maxAttempts: Int = 12,
        onProgress: suspend (Attempt) -> Unit = {}
    ): Result {
        val attempts = mutableListOf<Attempt>()
        var feedback: String? = null
        var lastOutput = ""

        val timedResult = withTimeoutOrNull(budget.minutes * 60_000L) {
            repeat(maxAttempts) { index ->
                val plan = try {
                    planProvider(feedback)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val a = Attempt(index + 1, "PLAN", false, "AI plan failed: ${e.message}")
                    attempts += a
                    onProgress(a)
                    return@withTimeoutOrNull Result(false, attempts, a.output)
                }

                val result = LocalBuildPipeline(agent, runtime).run(plan, install)
                val a = Attempt(index + 1, result.stage.name, result.success, result.output)
                attempts += a
                onProgress(a)
                lastOutput = result.output
                if (result.success) return@withTimeoutOrNull Result(true, attempts, result.output)

                feedback = result.output.takeLast(12_000)
                delay(250)
            }
            Result(false, attempts, lastOutput.ifBlank { "تعداد تلاش‌های خودکار به سقف رسید." })
        }

        return timedResult ?: Result(
            false,
            attempts,
            "زمان کاری ${budget.minutes} دقیقه‌ای تمام شد؛ آخرین خطا: ${lastOutput.ifBlank { "نامشخص" }}"
        )
    }
}
