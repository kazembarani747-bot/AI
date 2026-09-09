package com.kazembarani.ai.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LocalBuildManagerScreen(agent: LocalBuildAgent) {
    var state by remember { mutableStateOf<BuildManagerState>(BuildManagerState.Idle) }
    var status by remember { mutableStateOf<LocalBuildAgent.ToolchainStatus?>(null) }

    suspend fun refresh() {
        state = BuildManagerState.Inspecting
        status = withContext(Dispatchers.IO) { agent.inspectToolchain() }
        state = BuildManagerState.Ready(status!!)
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Local Build Manager", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(3.dp))
            Text("هدف: تولید پروژه، Build و تست روی خود گوشی؛ GitHub فقط برای ذخیره/همگام‌سازی اختیاری است.")
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
                            ToolRow("Android CLI", current.status.hasAndroidCli)
                            Text("فضای آزاد: ${formatBytes(current.status.freeBytes)}")
                            Text(current.status.note)
                        }
                        BuildManagerState.Preparing -> Text("در حال آماده‌سازی Toolchain…")
                        is BuildManagerState.Building -> Text("در حال Build: ${current.project.name}")
                        is BuildManagerState.Testing -> Text("در حال تست: ${current.project.name}")
                        is BuildManagerState.Success -> Text("✅ Build موفق بود")
                        is BuildManagerState.Failed -> Text("❌ ${current.message}")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state = BuildManagerState.Inspecting }) { Text("بررسی دوباره") }
                OutlinedButton(onClick = {
                    state = BuildManagerState.Preparing
                    // Real download/install is intentionally delegated to the future
                    // trusted toolchain runtime; no arbitrary executable is launched here.
                }) { Text("آماده‌سازی") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پایپ‌لاین هدف", style = MaterialTheme.typography.titleMedium)
                    Text("1. دریافت BuildPlan از AI")
                    Text("2. اعتبارسنجی مسیرها و ذخیره پروژه")
                    Text("3. آماده‌سازی JDK / Android SDK / Build Tools")
                    Text("4. اجرای Build محلی از طریق Runtime مورداعتماد")
                    Text("5. نصب APK و اجرای تست روی دستگاه")
                    Text("6. جمع‌آوری Logcat، layout و screenshot")
                    Text("7. ارسال خطا به AI → اصلاح → Build مجدد")
                }
            }
        }
        item {
            Text(
                "نکته: Android 10+ اجرای فایل اجرایی از home directory قابل‌نوشتن برنامه را محدود می‌کند؛ بنابراین Build Agent عمداً shell آزاد ندارد و باید با یک Runtime سازگار و مورداعتماد تکمیل شود.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ToolRow(name: String, installed: Boolean) {
    Text(if (installed) "✅ $name" else "⬜ $name — آماده نیست")
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "$bytes B"
}
