package com.kazembarani.ai.local

/**
 * Describes the on-device toolchain without bundling or executing downloaded
 * binaries yet. URLs are intentionally not hard-coded here; the bootstrapper
 * will resolve verified official sources before installing any component.
 */
data class ToolchainManifest(
    val schemaVersion: Int = 1,
    val jdk: Component = Component("JDK", required = true),
    val androidCli: Component = Component("Android CLI", required = false),
    val platformTools: Component = Component("Android Platform Tools", required = true),
    val gradle: Component = Component("Gradle", required = true),
    val sdkPlatforms: Component = Component("Android SDK Platforms", required = true),
    val buildTools: Component = Component("Android Build Tools", required = true)
) {
    data class Component(val name: String, val required: Boolean)
}
