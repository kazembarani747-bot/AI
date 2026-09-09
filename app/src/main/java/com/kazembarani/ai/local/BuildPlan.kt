package com.kazembarani.ai.local

import org.json.JSONArray
import org.json.JSONObject

/** Stable JSON contract between the AI project generator and the on-device build agent. */
data class BuildPlan(
    val projectName: String,
    val summary: String,
    val files: Map<String, String>,
    val buildTasks: List<String> = listOf("assembleDebug"),
    val testTasks: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("projectName", projectName)
        put("summary", summary)
        put("files", JSONObject(files))
        put("buildTasks", JSONArray(buildTasks))
        put("testTasks", JSONArray(testTasks))
    }

    companion object {
        fun fromJson(json: JSONObject): BuildPlan {
            val filesObject = json.optJSONObject("files")
            val files = if (filesObject != null) {
                buildMap {
                    filesObject.keys().forEach { key -> put(key, filesObject.optString(key)) }
                }
            } else {
                // /v1/android-project currently returns files as [{path, content}, ...].
                val filesArray = json.optJSONArray("files") ?: JSONArray()
                buildMap {
                    for (i in 0 until filesArray.length()) {
                        val item = filesArray.optJSONObject(i) ?: continue
                        val path = item.optString("path").trim()
                        if (path.isNotEmpty()) put(path, item.optString("content"))
                    }
                }
            }

            return BuildPlan(
                projectName = json.optString("projectName", json.optString("name", "GeneratedApp")),
                summary = json.optString("summary", "پروژه تولیدشده توسط AI"),
                files = files,
                buildTasks = json.optJSONArray("buildTasks")?.toStringList() ?: listOf("assembleDebug"),
                testTasks = json.optJSONArray("testTasks")?.toStringList() ?: emptyList()
            )
        }

        private fun JSONArray.toStringList(): List<String> =
            (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
