package com.kazembarani.ai.local

import android.content.Context
import com.kazembarani.ai.BuildConfig

/** Single source of truth for the backend URL used by foreground and background work. */
object BackendSettings {
    private const val PREFS = "ai_settings"
    private const val BACKEND_URL_KEY = "backend_url"

    fun clean(value: String): String {
        var url = value.trim().trimEnd('/')
        url = url.removeSuffix("/v1/chat").trimEnd('/')
        return url
    }

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_URL_KEY, BuildConfig.AI_API_URL.substringBeforeLast("/v1/chat"))
            ?.let(::clean)
            .orEmpty()

    fun save(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(BACKEND_URL_KEY, clean(value))
            .apply()
    }
}
