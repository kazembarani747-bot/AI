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

    LaunchedEffect(Unit) {
        state = BuildManagerState.Inspecting
        status = withContext(Dispatchers.IO) { agent.inspectToolchain() }
        state = BuildManagerState.Ready(status!!)
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Local Build Manager", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(3.dp))
            Text("ساخت و تست پروژه روی خود گوشی؛ GitHub برای Build لازم نیست.")
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
                Button(onClick = {
                    state = BuildManagerState.Inspecting
                    status = null
                }) { Text("بررسی دوباره") }
                OutlinedButton(onClick = {
                    state = BuildManagerState.Preparing
                }) { Text("آماده‌سازی") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مراحل بعدی", style = MaterialTheme.typography.titleMedium)
                    Text("1. دانلود امن JDK و ابزارهای Android")
                    Text("2. نصب/به‌روزرسانی SDK و Build Tools")
                    Text("3. دریافت پروژه تولیدشده توسط AI")
                    Text("4. Gradle Build محلی")
                    Text("5. نصب APK و اجرای تست‌های دستگاه")
                    Text("6. جمع‌آوری Logcat و ارسال خطا به AI برای اصلاح")
                }
            }
        }
    }
}

@Composable
private fun ToolRow(name: String, installed: Boolean) {
    Text(if (installed) "✅ $name" else "⬜ $name — نصب نشده")
}
