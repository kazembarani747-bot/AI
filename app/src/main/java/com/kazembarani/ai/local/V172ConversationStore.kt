package com.kazembarani.ai.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Durable local conversation history for V17.2. Each chat is independent and can be reopened later. */
object V172ConversationStore {
    data class Message(val id: String, val role: String, val text: String, val createdAt: Long)
    data class Conversation(val id: String, val title: String, val createdAt: Long, val updatedAt: Long, val messages: List<Message>)

    private const val PREFS = "v172_conversations"
    private const val KEY = "data"

    fun list(context: Context): List<Conversation> = read(context).sortedByDescending { it.updatedAt }

    fun get(context: Context, id: String): Conversation? = read(context).firstOrNull { it.id == id }

    fun create(context: Context, title: String = "گفت‌وگوی جدید"): Conversation {
        val now = System.currentTimeMillis()
        return Conversation(UUID.randomUUID().toString(), title.trim().ifBlank { "گفت‌وگوی جدید" }, now, now, emptyList()).also {
            write(context, read(context) + it)
        }
    }

    fun addMessage(context: Context, conversationId: String, role: String, text: String): Conversation? {
        val current = get(context, conversationId) ?: return null
        val clean = text.trim()
        if (clean.isBlank()) return current
        val title = if (current.messages.isEmpty() && role == "user") clean.take(42) else current.title
        val updated = current.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = current.messages + Message(UUID.randomUUID().toString(), role, clean, System.currentTimeMillis())
        )
        write(context, read(context).map { if (it.id == conversationId) updated else it })
        return updated
    }

    fun delete(context: Context, conversationId: String) = write(context, read(context).filterNot { it.id == conversationId })

    fun rename(context: Context, conversationId: String, title: String) {
        val clean = title.trim().ifBlank { "گفت‌وگو" }
        write(context, read(context).map { if (it.id == conversationId) it.copy(title = clean, updatedAt = System.currentTimeMillis()) else it })
    }

    private fun read(context: Context): List<Conversation> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val messages = obj.optJSONArray("messages") ?: JSONArray()
                add(Conversation(
                    obj.optString("id"), obj.optString("title", "گفت‌وگو"),
                    obj.optLong("createdAt"), obj.optLong("updatedAt"),
                    buildList {
                        for (j in 0 until messages.length()) {
                            val m = messages.optJSONObject(j) ?: continue
                            add(Message(m.optString("id"), m.optString("role", "user"), m.optString("text"), m.optLong("createdAt")))
                        }
                    }
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, conversations: List<Conversation>) {
        val array = JSONArray()
        conversations.take(50).forEach { c ->
            val messages = JSONArray()
            c.messages.takeLast(200).forEach { m -> messages.put(JSONObject().put("id", m.id).put("role", m.role).put("text", m.text).put("createdAt", m.createdAt)) }
            array.put(JSONObject().put("id", c.id).put("title", c.title).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt).put("messages", messages))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
