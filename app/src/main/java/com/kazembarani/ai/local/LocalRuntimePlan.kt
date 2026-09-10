package com.kazembarani.ai.local

/**
 * Describes the supported execution backends for on-device build/test work.
 * No downloaded executable is trusted or executed from writable app storage.
 */
data class LocalRuntimePlan(
    val backend: Backend,
    val requirements: List<String>,
    val supportedAbis: List<String>,
    val notes: List<String>
) {
    enum class Backend {
        EMBEDDED_NATIVE_RUNTIME,
        USER_APPROVED_COMPANION_RUNTIME,
        UNAVAILABLE
    }

    companion object {
        fun from(agent: LocalBuildAgent): LocalRuntimePlan {
            val status = agent.inspectToolchain()
            val abis = android.os.Build.SUPPORTED_ABIS.toList()
            return when {
                status.hasJdk && status.hasAndroidSdk && status.hasGradle && status.hasAdb ->
                    LocalRuntimePlan(
                        Backend.EMBEDDED_NATIVE_RUNTIME,
                        listOf("JDK", "Android SDK", "Gradle", "ADB"),
                        abis,
                        listOf("Toolchain files are present; execution still requires a compatible embedded/native runtime.")
                    )
                else ->
                    LocalRuntimePlan(
                        Backend.UNAVAILABLE,
                        listOf("JDK", "Android SDK", "Gradle", "ADB"),
                        abis,
                        listOf("Toolchain is incomplete. Do not execute downloaded binaries directly from app-writable storage.")
                    )
            }
        }
    }
}
