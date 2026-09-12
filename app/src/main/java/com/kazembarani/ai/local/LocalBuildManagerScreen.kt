package com.kazembarani.ai.local

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.work.WorkManager
import com.kazembarani.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun LocalBuildManagerScreen(agent: LocalBuildAgent, runtime: BuildRuntime = CompanionBuildRuntime(BuildConfig.RUNTIME_URL, BuildConfig.RUNTIME_TOKEN.ifBlank { null })) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BuildManagerState>(BuildManagerState.Idle) }
    var status by remember { mutableStateOf<LocalBuildAgent.ToolchainStatus?>(null) }
    var runtimeStatus by remember { mutableStateOf<BuildRuntime.Capabilities?>(null) }
    var prompt by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf(AutonomousWorkLoop.WorkBudget.MINUTES_10) }
    var install by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var jobState by remember { mutableStateOf("") }
    var jobOutput by remember { mutableStateOf("") }
    var apkPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val store = remember { AutonomousJobStore(context) }

    fun loadJob(id: String) {
        val record = store.load(id) ?: return
        jobId = id; prompt = record.request
        budget = AutonomousWorkLoop.WorkBudget.values().firstOrNull { it.minutes == record.budgetMinutes } ?: AutonomousWorkLoop.WorkBudget.MINUTES_10
        install = record.install; jobState = record.state; jobOutput = record.output; apkPath = record.apkPath
    }

    suspend fun refresh() {
        state = BuildManagerState.Inspecting
        status = withContext(Dispatchers.IO) { agent.inspectToolchain() }
        runtimeStatus = withContext(Dispatchers.IO) { runtime.capabilities() }
        state = BuildManagerState.Ready(status!!)
    }

    LaunchedEffect(Unit) { store.lastJobId()?.let { loadJob(it) }; refresh() }
    LaunchedEffect(jobId) {
        val id = jobId ?: return@LaunchedEffect
        val workId = runCatching { UUID.fromString(id) }.getOrNull() ?: return@LaunchedEffect
        val manager = WorkManager.getInstance(agent.context)
        while (true) {
            val info = withContext(Dispatchers.IO) { manager.getWorkInfoById(workId).get() }
            val record = withContext(Dispatchers.IO) { store.load(id) }
            jobState = record?.state ?: info?.state?.name.orEmpty(); jobOutput = record?.output.orEmpty(); apkPath = record?.apkPath
            running = info?.let { !it.state.isFinished } == true
            if (info == null || info.state.isFinished) break
            delay(1000)
        }
    }

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("استودیو ساخت نسخه ۱۵", style = MaterialTheme.typography.headlineSmall); Text("Planner → Build → Test → Diagnostics → Repair → APK", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ساخت خودکار", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), minLines = 4, enabled = !running, placeholder = { Text("مثلاً یک اپ بساز که هنگام اجرا بنویسد: سلام برنامه کار می‌کند 👋") })
                Text("بودجه زمانی")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { AutonomousWorkLoop.WorkBudget.values().forEach { option -> FilterChip(budget == option, { if (!running) budget = option }, label = { Text("${option.minutes} دقیقه") }) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("نصب APK پس از Build"); Text("اختیاری", style = MaterialTheme.typography.bodySmall) }; Switch(install, { if (!running) install = it }) }
                val runtimeReady = runtimeStatus?.canBuild == true
                if (!runtimeReady) Text("🟠 Runtime ساخت واقعی در دسترس نیست؛ اتصال Runtime را بررسی کن.", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !running && prompt.isNotBlank() && runtimeReady, onClick = { val id = BuildJobLauncher.enqueue(agent.context, prompt.trim(), "", budget.minutes, install); jobId = id; running = true; jobState = "QUEUED"; jobOutput = "در صف اجرای پس‌زمینه قرار گرفت؛ Build و Test واقعی انجام می‌شود."; apkPath = null }) { Text(if (running) "در حال کار…" else "🚀 شروع ساخت") }
                    if (running) OutlinedButton(onClick = { jobId?.let { runCatching { WorkManager.getInstance(agent.context).cancelWorkById(UUID.fromString(it)) } } }) { Text("توقف") }
                }
            } }
        }
        if (jobId != null) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("وضعیت اجرای Job", style = MaterialTheme.typography.titleMedium); Text("شناسه: ${jobId!!.take(8)}…"); Text("وضعیت: $jobState")
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (jobOutput.isNotBlank()) Text(jobOutput.takeLast(8000))
                val apk = apkPath?.let(::File)?.takeIf { it.isFile }
                if (apk != null) {
                    Text("📦 APK آماده است: ${apk.name}"); Text("حجم: ${formatBytes(apk.length())}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk); context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }) { Text("باز کردن APK") }
                        OutlinedButton(onClick = { val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/vnd.android.package-archive"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "اشتراک APK")) }) { Text("اشتراک") }
                    }
                }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Runtime و تست", style = MaterialTheme.typography.titleMedium)
                val r = runtimeStatus
                Text(if (r?.available == true) "🟢 Runtime متصل" else "🔴 Runtime متصل نیست")
                if (r != null) { Text(r.note); Text("Build: ${r.canBuild} • Test: ${r.canTest} • Install: ${r.canInstall}"); Text("Logcat: ${r.canCaptureLogs} • Screenshot: ${r.canCaptureScreenshots}") }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Toolchain محلی", style = MaterialTheme.typography.titleMedium)
                when (val current = state) {
                    BuildManagerState.Idle, BuildManagerState.Inspecting -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("در حال بررسی ابزارها…") }
                    is BuildManagerState.Ready -> { ToolRow("JDK", current.status.hasJdk); ToolRow("Android SDK", current.status.hasAndroidSdk); ToolRow("Gradle", current.status.hasGradle); ToolRow("ADB", current.status.hasAdb); Text("فضای آزاد: ${formatBytes(current.status.freeBytes)}"); Text(current.status.note) }
                    BuildManagerState.Preparing -> Text("در حال آماده‌سازی پروژه…")
                    is BuildManagerState.Building -> Text("در حال Build: ${current.project.name}")
                    is BuildManagerState.Testing -> Text("در حال Test: ${current.project.name}")
                    is BuildManagerState.Success -> Text("✅ ساخت موفق")
                    is BuildManagerState.Failed -> Text("❌ ${current.message}", color = MaterialTheme.colorScheme.error)
                }
            } }
        }
        item { Button(onClick = { scope.launch { refresh() } }, enabled = !running && state !is BuildManagerState.Inspecting) { Text("🔄 بررسی دوباره Runtime و ابزارها") } }
    }
}

@Composable private fun ToolRow(name: String, installed: Boolean) { Text(if (installed) "✅ $name" else "⬜ $name — آماده نیست") }
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024)); bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024)); else -> "$bytes B" }
