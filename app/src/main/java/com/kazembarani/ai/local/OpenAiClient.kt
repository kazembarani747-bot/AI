package com.kazembarani.ai.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stage 11: direct OpenAI Responses API client.
 * The user's API key is read only from the encrypted local ApiKeyStore and is never committed to GitHub.
 */
class OpenAiClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun ask(prompt: String, model: String = DEFAULT_MODEL): String =
        request(prompt, model, webSearch = false)

    suspend fun askWithWebSearch(prompt: String, model: String = DEFAULT_MODEL): String =
        request(prompt, model, webSearch = true)

    private suspend fun request(prompt: String, model: String, webSearch: Boolean): String =
        withContext(Dispatchers.IO) {
            val key = ApiKeyStore.get(context)
                ?: throw IllegalStateException("کلید OpenAI تنظیم نشده است.")

            val body = JSONObject()
                .put("model", model)
                .put("input", prompt)
                .put("store", false)

            if (webSearch) {
                body.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(
                        detail?.takeIf { it.isNotBlank() }
                            ?: "OpenAI API خطا داد (${response.code})."
                    )
                }
                extractText(JSONObject(raw))
            }
        }

    suspend fun testKey(): String = ask("Reply with exactly: OK", DEFAULT_MODEL).trim()

    suspend fun generateBuildPlan(request: String, repairContext: String? = null): BuildPlan {
        val prompt = buildString {
            append("You are the Stage 11 Android project builder. Generate a complete, runnable Android project from the user's request.\n")
            append("Return ONLY valid JSON, with no Markdown fences. Schema: {")
            append("\"projectName\":string,\"summary\":string,\"files\":{path:string,...},")
            append("\"buildTasks\":[string],\"testTasks\":[string]}. ")
            append("Use a normal Gradle Android project. Keep paths relative and safe. Prefer Kotlin + Jetpack Compose when appropriate.\n")
            append("User request:\n").append(request)
            if (!repairContext.isNullOrBlank()) {
                append("\n\nPrevious build failure. Repair the project while preserving the original request:\n")
                append(repairContext)
            }
        }
        val text = ask(prompt)
        return BuildPlan.fromJson(parseJsonObject(text)).also { it.validate() }
    }

    private fun parseJsonObject(text: String): JSONObject {
        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return JSONObject(cleaned)
    }

    private fun extractText(root: JSONObject): String {
        root.optString("output_text")
            .takeIf { it.isNotBlank() }
            ?.let { return it }

        val output = root.optJSONArray("output") ?: JSONArray()
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) result.append(text)
            }
        }
        return result.toString().trim().ifBlank {
            throw IllegalStateException("پاسخ متنی از OpenAI دریافت نشد.")
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-5.6-luna"
    }
}
