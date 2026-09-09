package com.kazembarani.ai.local

import java.io.File
import org.json.JSONObject

/** Persists generated BuildPlans so generation and local build are decoupled. */
class ProjectStore(private val agent: LocalBuildAgent) {
    private val metadataDir = File(agent.workspace, "project-metadata").apply { mkdirs() }

    fun save(plan: BuildPlan): File {
        val project = agent.prepareBuild(plan)
        val metadata = File(metadataDir, "${plan.projectName}.json")
        metadata.writeText(plan.toJson().toString())
        return project
    }

    fun load(projectName: String): BuildPlan? {
        val file = File(metadataDir, "$projectName.json")
        if (!file.isFile) return null
        return BuildPlan.fromJson(JSONObject(file.readText()))
    }
}
