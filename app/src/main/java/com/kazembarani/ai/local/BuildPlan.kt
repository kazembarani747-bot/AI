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
            val filesJson = json.optJSONObject("files") ?: JSONObject()
            val files = buildMap {
                filesJson.keys().forEach { put(it, filesJson.optString(it)) }
            }
            return BuildPlan(
                projectName = json.optString("projectName", "GeneratedApp"),
                summary = json.optString("summary", "پروژه تولیدشده توسط AI"),
                files = files,
                buildTasks = json.optJSONArray("buildTasks")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
                } ?: listOf("assembleDebug"),
                testTasks = json.optJSONArray("testTasks")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
            )
        }
    }
}
