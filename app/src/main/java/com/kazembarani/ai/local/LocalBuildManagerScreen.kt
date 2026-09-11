package com.kazembarani.ai.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalBuildManagerScreen(agent: LocalBuildAgent, runtime: BuildRuntime = CompanionBuildRuntime()) {
    var state by remember { mutableStateOf<BuildManagerState>(BuildManagerState.Idle) }
    var status by remember { mutableStateOf<LocalBuildAgent.ToolchainStatus?>(null) }
    var runtimeStatus by remember { mutableStateOf<BuildRuntime.Capabilities?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        state = BuildManagerState.Inspecting
        status = withContext(Dispatchers.IO) { agent.inspectToolchain() }
        runtimeStatus = withContext(Dispatchers.IO) { runtime.capabilities() }
        state = BuildManagerState.Ready(status!!)
    }
    LaunchedEffect(Unit) { refresh() }

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Local Build Manager", style = MaterialTheme.typography.headlineSmall)
            Text("Build واقعی از طریق Companion Runtime مورداعتماد؛ بدون shell آزاد داخل APK.")
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت Runtime", style = MaterialTheme.typography.titleMedium)
                val r = runtimeStatus
                Text(if (r?.available == true) "🟢 Runtime متصل" else "🔴 Runtime متصل نیست")
                if (r != null) Text(r.note)
                if (r != null) {
                    Text("Build: ${r.canBuild}  |  Test: ${r.canTest}  |  Install: ${r.canInstall}")
                    Text("Logcat: ${r.canCaptureLogs}  |  Screenshot: ${r.canCaptureScreenshots}")
                }
            }}
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت محیط محلی", style = MaterialTheme.typography.titleMedium)
                when (val current = state) {
                    BuildManagerState.Idle, BuildManagerState.Inspecting -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("در حال بررسی ابزارها…") }
                    is BuildManagerState.Ready -> { ToolRow("JDK", current.status.hasJdk); ToolRow("Android SDK", current.status.hasAndroidSdk); ToolRow("Gradle", current.status.hasGradle); ToolRow("ADB", current.status.hasAdb); Text("فضای آزاد: ${formatBytes(current.status.freeBytes)}"); Text(current.status.note) }
                    BuildManagerState.Preparing -> Text("در حال آماده‌سازی…")
                    is BuildManagerState.Building -> Text("در حال Build: ${current.project.name}")
                    is BuildManagerState.Testing -> Text("در حال تست: ${current.project.name}")
                    is BuildManagerState.Success -> Text("✅ Build موفق بود")
                    is BuildManagerState.Failed -> Text("❌ ${current.message}")
                }
            }}
        }
        item { Button(onClick = { scope.launch { refresh() } }, enabled = state !is BuildManagerState.Inspecting) { Text("بررسی Runtime و ابزارها") } }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پایپ‌لاین واقعی", style = MaterialTheme.typography.titleMedium)
                Text("1. BuildPlan از AI → 2. ارسال پروژه به Runtime → 3. Gradle Build → 4. تست Android → 5. نصب APK → 6. Logcat/Screenshot → 7. خطا به AI → اصلاح و Build مجدد")
                Text("Runtime فقط taskهای محدود و مسیرهای اعتبارسنجی‌شده را اجرا می‌کند.", style = MaterialTheme.typography.bodySmall)
            }}
        }
    }
}

@Composable private fun ToolRow(name: String, installed: Boolean) { Text(if (installed) "✅ $name" else "⬜ $name — آماده نیست") }
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024)); bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024)); else -> "$bytes B" }
