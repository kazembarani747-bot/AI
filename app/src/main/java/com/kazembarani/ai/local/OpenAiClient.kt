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

/** AI client for OpenAI Responses and OpenAI-compatible Chat Completions endpoints. */
class OpenAiClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun ask(prompt: String): String = request(withMemory(prompt), webSearch = false)

    suspend fun askWithWebSearch(prompt: String): String = request(withMemory(prompt), webSearch = true)

    suspend fun testKey(): String = request("Reply with exactly: OK", webSearch = false).trim()

    private fun withMemory(prompt: String): String {
        val memory = MemoryStore.contextPrompt(context)
        return buildString {
            append("You are the user's persistent personal AI assistant. Speak naturally and warmly. If the user writes Persian, answer in Persian. Understand casual conversation, emotional support, jokes, emojis and technical requests. Do not claim to remember anything outside the supplied memory.\n\n")
            append(prompt)
            append(memory)
        }
    }

    private suspend fun request(prompt: String, webSearch: Boolean): String = withContext(Dispatchers.IO) {
        val config = ApiKeyStore.getConfig(context) ?: throw IllegalStateException("🔑 هنوز هیچ API Key تنظیم نشده است. فقط کلیدت را وارد کن تا برنامه آن را تشخیص دهد.")
        val url = when (config.protocol) {
            AiProviderConfig.Protocol.RESPONSES -> "${config.baseUrl}/responses"
            AiProviderConfig.Protocol.CHAT_COMPLETIONS -> "${config.baseUrl}/chat/completions"
        }
        val body = when (config.protocol) {
            AiProviderConfig.Protocol.RESPONSES -> JSONObject()
                .put("model", config.model)
                .put("input", prompt)
                .put("store", false)
                .apply {
                    if (webSearch && config.company.contains("OpenAI", true)) {
                        put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                    }
                }
            AiProviderConfig.Protocol.CHAT_COMPLETIONS -> JSONObject()
                .put("model", config.model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(persianApiError(response.code, raw, config.company))
                extractText(JSONObject(raw), config.protocol)
            }
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("🌐 اینترنت یا سرور «${config.company}» در دسترس نیست.")
        } catch (e: java.net.SocketTimeoutException) {
            throw IllegalStateException("⏱️ پاسخ «${config.company}» طول کشید و زمان درخواست تمام شد. دوباره امتحان کن.")
        }
    }

    suspend fun generateBuildPlan(request: String, repairContext: String? = null): BuildPlan {
        val prompt = buildString {
            append("You are an expert Android development agent. Understand the user's intent before planning. Generate a complete runnable Android project.\n")
            append("Return ONLY valid JSON: {\"projectName\":string,\"summary\":string,\"files\":{path:string,...},\"buildTasks\":[string],\"testTasks\":[string]}.\n")
            append("Use Kotlin + Jetpack Compose when appropriate. Include real build and instrumentation test tasks. Never use fake success.\n")
            append("User request:\n").append(request)
            if (!repairContext.isNullOrBlank()) append("\n\nPrevious build/test diagnostics. Repair the project while preserving the request:\n").append(repairContext)
        }
        return BuildPlan.fromJson(parseJsonObject(ask(prompt))).also { it.validate() }
    }

    private fun parseJsonObject(text: String): JSONObject {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return JSONObject(cleaned)
    }

    private fun extractText(root: JSONObject, protocol: AiProviderConfig.Protocol): String {
        val result = when (protocol) {
            AiProviderConfig.Protocol.RESPONSES -> root.optString("output_text").takeIf { it.isNotBlank() } ?: run {
                val output = root.optJSONArray("output") ?: JSONArray()
                buildString {
                    for (i in 0 until output.length()) {
                        val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                        for (j in 0 until content.length()) append(content.optJSONObject(j)?.optString("text").orEmpty())
                    }
                }
            }
            AiProviderConfig.Protocol.CHAT_COMPLETIONS -> root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        }
        return result.trim().ifBlank { throw IllegalStateException("🤖 سرویس پاسخ متنی برنگرداند. مدل و نوع API را بررسی کن.") }
    }

    private fun persianApiError(code: Int, raw: String, company: String): String {
        val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val lower = message.lowercase()
        return when {
            code == 400 -> "⚠️ درخواست برای «$company» نامعتبر بود. مدل، Base URL یا قالب API را بررسی کن."
            code == 401 || lower.contains("invalid api key") || lower.contains("incorrect api key") -> "🔑 کلید API نامعتبر است یا دسترسی آن رد شده است."
            code == 402 || lower.contains("billing") || lower.contains("account is not active") -> "💳 حساب «$company» برای API فعال نیست یا اعتبار/صورت‌حساب آن مشکل دارد."
            code == 403 -> "⛔ این کلید اجازه استفاده از مدل یا سرویس انتخاب‌شده را ندارد."
            code == 404 -> "🔎 آدرس API یا مدل پیدا نشد. Base URL و نام مدل را بررسی کن."
            code == 429 -> "🚦 سقف درخواست یا سهمیه API پر شده است. کمی بعد دوباره امتحان کن."
            code in 500..599 -> "🛠️ سرور «$company» موقتاً خطا داد (کد $code)."
            message.isNotBlank() -> "❌ سرویس «$company» خطا داد: $message"
            else -> "❌ درخواست API ناموفق بود (کد $code)."
        }
    }
}
