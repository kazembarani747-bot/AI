package com.kazembarani.ai.local

import org.json.JSONObject

/** Parses the documented Rubika update shape without making network calls. */
object V171RubikaUpdateParser {
    data class Message(
        val text: String,
        val chatId: String?,
        val messageId: String?,
        val senderId: String?,
        val senderIsBot: Boolean
    )

    fun parse(update: JSONObject): Message? {
        val message = update.optJSONObject("new_message") ?: update.optJSONObject("message") ?: return null
        val text = message.optString("text").trim()
        val chatId = update.optString("chat_id").takeIf { it.isNotBlank() }
            ?: message.optString("chat_id").takeIf { it.isNotBlank() }
        val messageId = message.optString("message_id").takeIf { it.isNotBlank() }
        val senderId = message.optString("sender_id").takeIf { it.isNotBlank() }
            ?: message.optJSONObject("sender")?.optString("user_id")?.takeIf { it.isNotBlank() }
        val senderIsBot = message.optString("sender_type").equals("Bot", ignoreCase = true)
        if (text.isBlank() || chatId == null) return null
        return Message(text, chatId, messageId, senderId, senderIsBot)
    }
}
