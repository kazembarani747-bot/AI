package com.kazembarani.ai.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, durable V17 memory foundation. Stores user-approved memories locally. */
object V17MemoryStore {
    private const val PREFS = "v17_memory"
    private const val KEY_ITEMS = "items"

    data class Memory(val id: Long, val text: String, val createdAt: Long)

    @Synchronized
    fun add(context: Context, text: String): Memory? {
        val value = text.trim()
        if (value.isEmpty()) return null
        val item = Memory(System.currentTimeMillis(), value, System.currentTimeMillis())
        val array = readArray(context)
        array.put(JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("createdAt", item.createdAt)
        })
        save(context, array)
        return item
    }

    @Synchronized
    fun recent(context: Context, limit: Int = 20): List<Memory> {
        val array = readArray(context)
        val result = ArrayList<Memory>(minOf(array.length(), limit))
        for (index in array.length() - 1 downTo maxOf(0, array.length() - limit)) {
            val item = array.optJSONObject(index) ?: continue
            result += Memory(item.optLong("id"), item.optString("text"), item.optLong("createdAt"))
        }
        return result
    }

    @Synchronized
    fun delete(context: Context, id: Long): Boolean {
        val array = readArray(context)
        val replacement = JSONArray()
        var removed = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optLong("id") == id) removed = true else replacement.put(item)
        }
        if (removed) save(context, replacement)
        return removed
    }

    @Synchronized
    fun clear(context: Context) {
        save(context, JSONArray())
    }

    private fun readArray(context: Context): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]"))
    }.getOrElse { JSONArray() }

    private fun save(context: Context, array: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
