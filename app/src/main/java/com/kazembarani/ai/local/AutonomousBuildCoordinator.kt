package com.kazembarani.ai.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Connects the Android UI to the bounded autonomous build loop and the server-side AI planner. */
class AutonomousBuildCoordinator(
    private val agent: LocalBuildAgent,
    private val runtime: BuildRuntime = CompanionBuildRuntime(),
    private val backendUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    data class Progress(
        val attempt: Int,
        val stage: String,
        val success: Boolean,
        val output: String
    )

    suspend fun run(
        request: String,
        budget: AutonomousWorkLoop.WorkBudget,
        install: Boolean = false,
        maxAttempts: Int = 12,
        onProgress: suspend (Progress) -> Unit = {}
    ): AutonomousWorkLoop.Result {
        val cleanRequest = request.trim()
        require(cleanRequest.isNotEmpty()) { "درخواست ساخت خالی است." }

        val loop = AutonomousWorkLoop(agent, runtime)
        return loop.run(
            planProvider = { feedback -> requestPlan(cleanRequest, feedback) },
            budget = budget,
            install = install,
            maxAttempts = maxAttempts,
            onProgress = { attempt ->
                onProgress(Progress(attempt.number, attempt.stage, attempt.success, attempt.output))
            }
        )
    }

    private suspend fun requestPlan(request: String, feedback: String?): BuildPlan = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("request", request)
            .apply { if (!feedback.isNullOrBlank()) put("feedback", feedback) }
            .toString()

        val url = backendUrl.trimEnd('/') + "/v1/autonomous-plan"
        val httpRequest = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Planner HTTP ${response.code}: ${body.take(2000)}")
                }
                val json = JSONObject(body)
                val planJson = json.optJSONObject("plan") ?: json
                return@withContext BuildPlan.fromJson(planJson)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("ارتباط با Planner برقرار نشد: ${e.message}", e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
