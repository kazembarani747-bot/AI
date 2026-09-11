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
    fun validate() {
        require(Regex("^[A-Za-z0-9._-]{1,80}$").matches(projectName)) { "نام پروژه نامعتبر است." }
        require(files.isNotEmpty() && files.size <= 2000) { "تعداد فایل‌های پروژه نامعتبر است." }
        files.forEach { (path, content) ->
            require(path.isNotBlank() && !path.startsWith("/") && !path.contains("..")) { "مسیر فایل نامعتبر است: $path" }
            require(content.length <= 2_000_000) { "فایل بیش از حد بزرگ است: $path" }
        }
        validateTasks(buildTasks, "buildTasks")
        validateTasks(testTasks, "testTasks")
    }

    private fun validateTasks(tasks: List<String>, name: String) {
        require(tasks.size <= 16 && tasks.all { Regex("^[A-Za-z0-9:_-]{1,80}$").matches(it) }) { "$name نامعتبر است." }
    }

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
