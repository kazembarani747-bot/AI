package com.kazembarani.ai.local

import java.io.File

sealed interface BuildManagerState {
    data object Idle : BuildManagerState
    data object Inspecting : BuildManagerState
    data object Preparing : BuildManagerState
    data class Ready(val status: LocalBuildAgent.ToolchainStatus) : BuildManagerState
    data class Building(val project: File) : BuildManagerState
    data class Testing(val project: File) : BuildManagerState
    data class Success(val apk: File?, val output: String) : BuildManagerState
    data class Failed(val message: String, val output: String = "") : BuildManagerState
}
