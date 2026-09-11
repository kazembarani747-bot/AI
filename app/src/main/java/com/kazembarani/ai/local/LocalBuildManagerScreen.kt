package com.kazembarani.ai.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kazembarani.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalBuildManagerScreen(agent: LocalBuildAgent, runtime: BuildRuntime = CompanionBuildRuntime()) {
    var state by remember { mutableStateOf<BuildManagerState>(BuildManagerState.Idle) }
    var status by remember { mutableStateOf<LocalBuildAgent.ToolchainStatus?>(null) }
    var runtimeStatus by remember { mutableStateOf<BuildRuntime.Capabilities?>(null) }
    var prompt by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf(AutonomousWorkLoop.WorkBudget.MINUTES_10) }
    var install by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<List<AutonomousBuildCoordinator.Progress>>(emptyList()) }
    var finalOutput by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        state = BuildManagerState.Inspecting
        status = withContext(Dispatchers.IO) { agent.inspectToolchain() }
        runtimeStatus = withContext(Dispatchers.IO) { runtime.capabilities() }
        state = BuildManagerState.Ready(status!!)
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    LazyColumn(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ساخت خودکار AI", style = MaterialTheme.typography.headlineSmall)
            Text("مرحله ۱: درخواست → Planner → AutonomousWorkLoop → Build/تست → بازگشت خطا به AI")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("درخواست ساخت", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        enabled = !running,
                        placeholder = { Text("مثلاً: یک اپ یادداشت با جست‌وجو، ذخیره محلی و رابط فارسی بساز") }
                    )

                    Text("مدت کار خودکار")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutonomousWorkLoop.WorkBudget.values().forEach { option ->
                            FilterChip(
                                selected = budget == option,
                                onClick = { if (!running) budget = option },
                                label = { Text("${option.minutes} دقیقه") }
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("نصب APK پس از Build")
                            Text("پیش‌فرض خاموش است", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = install, onCheckedChange = { if (!running) install = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !running && prompt.isNotBlank() && runtimeStatus?.canBuild == true,
                            onClick = {
                                progress = emptyList()
                                finalOutput = ""
                                running = true
                                job = scope.launch {
                                    val coordinator = AutonomousBuildCoordinator(
                                        agent = agent,
                                        runtime = runtime,
                                        backendUrl = BuildConfig.AI_API_URL.substringBeforeLast("/v1/chat")
                                    )
                                    try {
                                        val result = coordinator.run(
                                            request = prompt,
                                            budget = budget,
                                            install = install,
                                            onProgress = { item -> progress = progress + item }
                                        )
                                        finalOutput = result.finalOutput
                                    } catch (e: Exception) {
                                        finalOutput = e.message ?: "خطای نامشخص"
                                    } finally {
                                        running = false
                                    }
                                }
                            }
                        ) { Text(if (running) "در حال کار…" else "شروع ساخت خودکار") }

                        if (running) {
                            OutlinedButton(onClick = { job?.cancel(); running = false }) {
                                Text("توقف")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وضعیت Runtime", style = MaterialTheme.typography.titleMedium)
                    val r = runtimeStatus
                    Text(if (r?.available == true) "🟢 Runtime متصل" else "🔴 Runtime متصل نیست")
                    if (r != null) {
                        Text(r.note)
                        Text("Build: ${r.canBuild} | Test: ${r.canTest} | Install: ${r.canInstall}")
                        Text("Logcat: ${r.canCaptureLogs} | Screenshot: ${r.canCaptureScreenshots}")
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وضعیت محیط محلی", style = MaterialTheme.typography.titleMedium)
                    when (val current = state) {
                        BuildManagerState.Idle, BuildManagerState.Inspecting -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("در حال بررسی ابزارها…")
                        }
                        is BuildManagerState.Ready -> {
                            ToolRow("JDK", current.status.hasJdk)
                            ToolRow("Android SDK", current.status.hasAndroidSdk)
                            ToolRow("Gradle", current.status.hasGradle)
                            ToolRow("ADB", current.status.hasAdb)
                            Text("فضای آزاد: ${formatBytes(current.status.freeBytes)}")
                            Text(current.status.note)
                        }
                        BuildManagerState.Preparing -> Text("در حال آماده‌سازی…")
                        is BuildManagerState.Building -> Text("در حال Build: ${current.project.name}")
                        is BuildManagerState.Testing -> Text("در حال تست: ${current.project.name}")
                        is BuildManagerState.Success -> Text("✅ Build موفق بود")
                        is BuildManagerState.Failed -> Text("❌ ${current.message}")
                    }
                }
            }
        }

        if (progress.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("گزارش تلاش‌های خودکار", style = MaterialTheme.typography.titleMedium)
                        progress.forEach { item ->
                            Text("تلاش ${item.attempt}: ${stageLabel(item.stage)} ${if (item.success) "✅" else "❌"}")
                            Text(item.output.takeLast(1200), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (finalOutput.isNotBlank()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("نتیجه نهایی", style = MaterialTheme.typography.titleMedium)
                        Text(finalOutput.takeLast(6000))
                    }
                }
            }
        }

        item {
            Button(
                onClick = { scope.launch { refresh() } },
                enabled = !running && state !is BuildManagerState.Inspecting
            ) { Text("بررسی Runtime و ابزارها") }
        }
    }
}

@Composable private fun ToolRow(name: String, installed: Boolean) {
    Text(if (installed) "✅ $name" else "⬜ $name — آماده نیست")
}

private fun stageLabel(stage: String): String = when (stage) {
    "PLAN" -> "برنامه‌ریزی AI"
    "PREPARE" -> "آماده‌سازی"
    "BUILD" -> "ساخت APK"
    "TEST" -> "تست Android"
    "INSTALL" -> "نصب APK"
    "COMPLETE" -> "تکمیل"
    "FAILED" -> "خطا و ارسال بازخورد به AI"
    else -> stage
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "$bytes B"
}
