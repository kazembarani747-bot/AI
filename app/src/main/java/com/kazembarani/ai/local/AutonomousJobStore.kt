package com.kazembarani.ai.local

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Durable journal for autonomous build jobs, including the latest APK artifact path. */
class AutonomousJobStore(context: Context) {
    private val root = File(context.filesDir, "ai-workspace/autonomous-jobs").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("autonomous-build", Context.MODE_PRIVATE)

    data class Record(
        val id: String,
        val request: String,
        val budgetMinutes: Int,
        val install: Boolean,
        val state: String,
        val output: String,
        val apkPath: String? = null,
        val updatedAt: Long
    )

    fun save(record: Record) {
        val safe = record.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(root, "$safe.json").writeText(
            JSONObject()
                .put("id", record.id)
                .put("request", record.request)
                .put("budgetMinutes", record.budgetMinutes)
                .put("install", record.install)
                .put("state", record.state)
                .put("output", record.output.takeLast(20_000))
                .put("apkPath", record.apkPath ?: JSONObject.NULL)
                .put("updatedAt", record.updatedAt)
                .toString()
        )
        prefs.edit().putString(KEY_LAST_JOB_ID, record.id).apply()
    }

    fun load(id: String): Record? {
        val file = File(root, "$id.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            Record(
                id = json.getString("id"),
                request = json.getString("request"),
                budgetMinutes = json.getInt("budgetMinutes"),
                install = json.getBoolean("install"),
                state = json.getString("state"),
                output = json.optString("output"),
                apkPath = json.optString("apkPath").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = json.optLong("updatedAt")
            )
        }.getOrNull()
    }

    fun lastJobId(): String? = prefs.getString(KEY_LAST_JOB_ID, null)

    companion object {
        private const val KEY_LAST_JOB_ID = "last_job_id"
    }
}
