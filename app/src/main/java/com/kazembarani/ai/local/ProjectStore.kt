package com.kazembarani.ai.local

import java.io.File
import org.json.JSONObject

/** Persists generated BuildPlans so generation and local build are decoupled. */
class ProjectStore(private val agent: LocalBuildAgent) {
    private val metadataDir = File(agent.workspace, "project-metadata").apply { mkdirs() }

    fun save(plan: BuildPlan): File {
        validateProjectName(plan.projectName)
        require(plan.files.size <= 2000) { "تعداد فایل‌های پروژه بیش از حد مجاز است." }
        val project = agent.prepareBuild(plan)
        metadataFile(plan.projectName).writeText(plan.toJson().toString())
        return project
    }

    fun load(projectName: String): BuildPlan? {
        validateProjectName(projectName)
        val file = metadataFile(projectName)
        if (!file.isFile) return null
        return runCatching { BuildPlan.fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun listProjects(): List<String> =
        metadataDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) }
            ?.sorted()
            ?.toList()
            ?: emptyList()

    private fun metadataFile(projectName: String): File {
        val root = metadataDir.canonicalFile
        val file = File(root, "$projectName.json").canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "مسیر متادیتای پروژه نامعتبر است." }
        return file
    }

    private fun validateProjectName(name: String) {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "نام پروژه نامعتبر است." }
        require(name.length <= 80) { "نام پروژه بیش از حد طولانی است." }
    }
}
