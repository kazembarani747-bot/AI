package com.kazembarani.ai.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal real Rubika Bot API client. Token is never included in logs. */
class V171RubikaClient(private val token: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val base = "https://botapi.rubika.ir/v3/" + token.trim()

    suspend fun call(method: String, payload: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/$method")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Rubika API HTTP ${response.code}")
            JSONObject(body)
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
        call("deleteMessages", JSONObject().put("chat_id", chatId).put("message_ids", org.json.JSONArray(messageIds)))

    suspend fun editMessageText(chatId: String, messageId: String, text: String): JSONObject =
        call("editMessageText", JSONObject().put("chat_id", chatId).put("message_id", messageId).put("text", text))

    suspend fun forwardMessage(fromChatId: String, messageId: String, toChatId: String): JSONObject =
        call("forwardMessage", JSONObject().put("from_chat_id", fromChatId).put("message_id", messageId).put("to_chat_id", toChatId))
}
