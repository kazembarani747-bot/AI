package com.kazembarani.ai.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Rubika Bot API v3 client. The token is never logged or persisted by this class. */
class V171RubikaClient(private val token: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val base = "https://botapi.rubika.ir/v3/" + token.trim()

    suspend fun call(method: String, payload: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Rubika bot token is empty" }
        require(method.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Invalid Rubika method" }
        val request = Request.Builder()
            .url("$base/$method")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Rubika API HTTP ${response.code}")
            val root = JSONObject(body)
            val status = root.optString("status")
            if (status.isNotBlank() && !status.equals("OK", ignoreCase = true)) {
                val message = root.optString("message").ifBlank { root.optString("error") }
                error("Rubika API error: ${status}${if (message.isNotBlank()) " - $message" else ""}")
            }
            val data = root.opt("data")
            when (data) {
                is JSONObject -> data
                else -> root
            }
        }
    }

    suspend fun getMe(): JSONObject = call("getMe")

    suspend fun getUpdates(limit: Int = 100, offsetId: String? = null): JSONObject {
        val input = JSONObject().put("limit", limit.coerceIn(1, 100))
        if (!offsetId.isNullOrBlank()) input.put("offset_id", offsetId)
        return call("getUpdates", input)
    }

    suspend fun sendMessage(chatId: String, text: String, replyToMessageId: String? = null): JSONObject {
        val input = JSONObject().put("chat_id", chatId).put("text", text)
        if (!replyToMessageId.isNullOrBlank()) input.put("reply_to_message_id", replyToMessageId)
        return call("sendMessage", input)
    }

    suspend fun deleteMessages(chatId: String, messageIds: List<String>): JSONObject =
        call("deleteMessages", JSONObject().put("chat_id", chatId).put("message_ids", JSONArray(messageIds)))

    suspend fun editMessageText(chatId: String, messageId: String, text: String): JSONObject =
        call("editMessageText", JSONObject().put("chat_id", chatId).put("message_id", messageId).put("text", text))

    suspend fun forwardMessage(fromChatId: String, messageId: String, toChatId: String): JSONObject =
        call("forwardMessage", JSONObject().put("from_chat_id", fromChatId).put("message_id", messageId).put("to_chat_id", toChatId))

    /** Escape hatch for any currently supported Rubika Bot API v3 method. */
    suspend fun api(method: String, payload: JSONObject = JSONObject()): JSONObject = call(method, payload)
}
