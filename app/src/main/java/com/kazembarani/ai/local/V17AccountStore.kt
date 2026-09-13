package com.kazembarani.ai.local

import android.content.Context
import android.util.Patterns

/** V17 account foundation. Keeps only a normalized email identity locally until a real backend is wired. */
object V17AccountStore {
    private const val PREFS = "v17_account"
    private const val KEY_EMAIL = "email"

    fun email(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun saveEmail(context: Context, email: String): Boolean {
        val normalized = email.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_EMAIL, normalized).apply()
        return true
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
