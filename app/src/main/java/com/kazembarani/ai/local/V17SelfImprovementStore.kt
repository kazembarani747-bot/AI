package com.kazembarani.ai.local

import android.content.Context

/**
 * Persistent control plane for V17's self-improvement loop.
 *
 * The loop may research, propose, validate and stage an update automatically.
 * Installation/activation remains subject to Android's package/security model.
 */
object V17SelfImprovementStore {
    private const val PREFS = "v17_self_improvement"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_RESULT = "last_result"
    private const val KEY_LAST_UPDATE = "last_update"

    data class State(
        val enabled: Boolean,
        val lastResult: String,
        val lastUpdateAt: Long
    )

    fun state(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            lastResult = prefs.getString(KEY_LAST_RESULT, "Not run yet") ?: "Not run yet",
            lastUpdateAt = prefs.getLong(KEY_LAST_UPDATE, 0L)
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun recordResult(context: Context, result: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RESULT, result.take(500))
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    /** Stages a proposed improvement only; it never executes arbitrary downloaded code. */
    fun shouldAutoRun(context: Context): Boolean = state(context).enabled
}
