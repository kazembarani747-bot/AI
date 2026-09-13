package com.kazembarani.ai.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MemoryMessage(val role: String, val text: String)

/** Private on-device conversation memory. It is only sent to the configured AI when building a request. */
object MemoryStore {
    private const val PREFS = "ai_memory"
    private const val MESSAGES = "messages"
    private const val FACTS = "facts"
    private const val MAX_MESSAGES = 80

    fun loadMessages(context: Context): List<MemoryMessage> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MESSAGES, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role")
                val text = item.optString("text")
                if (role.isNotBlank() && text.isNotBlank()) add(MemoryMessage(role, text))
            }
        }
    }.getOrDefault(emptyList())

    fun append(context: Context, message: MemoryMessage) {
        val current = loadMessages(context).toMutableList()
        current.add(message)
        val kept = current.takeLast(MAX_MESSAGES)
        val array = JSONArray()
        kept.forEach { array.put(JSONObject().put("role", it.role).put("text", it.text)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MESSAGES, array.toString()).apply()
    }

    fun saveFacts(context: Context, facts: List<String>) {
        val array = JSONArray()
        facts.distinct().filter { it.isNotBlank() }.take(40).forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(FACTS, array.toString()).apply()
    }

    fun loadFacts(context: Context): List<String> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(FACTS, "[]") ?: "[]")
        buildList { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }.getOrDefault(emptyList())

    fun contextPrompt(context: Context, maxMessages: Int = 18): String {
        val messages = loadMessages(context).takeLast(maxMessages)
        val facts = loadFacts(context)
        if (messages.isEmpty() && facts.isEmpty()) return ""
        return buildString {
            append("\n\n[حافظه محلی دستیار — فقط برای حفظ پیوستگی گفتگو]")
            if (facts.isNotEmpty()) append("\nنکات ذخیره‌شده: ").append(facts.joinToString(" | "))
            if (messages.isNotEmpty()) {
                append("\nگفتگوهای اخیر:")
                messages.forEach { append("\n${it.role}: ${it.text.take(1400)}") }
            }
            append("\nاگر اطلاعاتی با درخواست فعلی ناسازگار است، درخواست فعلی کاربر را اولویت بده.")
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
