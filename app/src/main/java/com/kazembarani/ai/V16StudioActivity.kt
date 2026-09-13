package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.AutonomousJobStore

class V16StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ApiKeyStore.hasKey(this)) {
            startActivity(Intent(this, WelcomeActivity::class.java)); finish(); return
        }
        setContent {
            V16Home(
                openStudio = { startActivity(Intent(this, StudioActivity::class.java)) },
                openWorkspace = { startActivity(Intent(this, PhoneWorkspaceActivity::class.java)) },
                jobs = remember { AutonomousJobStore(this).recent() }
            )
        }
    }
}

@Composable
private fun V16Home(openStudio: () -> Unit, openWorkspace: () -> Unit, jobs: List<AutonomousJobStore.Record>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    MaterialTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AnimatedVisibility(visible, enter = fadeIn() + slideInVertically()) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column { Text("AI", fontSize = 38.sp); Text("V16.5 • OpenAI", color = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("دستیار شخصی + استودیوی توسعه", fontSize = 20.sp)
                        Text("چت، کدنویسی، فایل، Workspace و ساخت واقعی APK", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(openStudio, Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(8.dp)); Text("ورود به Studio 🚀") }
            }
            item { OutlinedButton(openWorkspace, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text("📱 فضای کاری گوشی") } }
            item { OutlinedButton(openStudio, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Code, null); Spacer(Modifier.width(8.dp)); Text("🧑‍💻 کدنویسی و تحلیل فایل") } }
            item { OutlinedButton(openStudio, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(8.dp)); Text("📦 ساخت / تست / تعمیر APK") } }
            item {
                Text("آخرین کارها", fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (jobs.isEmpty()) item { Text("هنوز کار ساختی ثبت نشده است. یک پروژه از Studio شروع کن ✨", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(jobs.take(5), key = { it.id }) { job ->
                val icon = if (job.state == "SUCCEEDED") Icons.Default.CheckCircle else if (job.state == "FAILED") Icons.Default.Error else Icons.Default.Build
                ListItem(leadingContent = { Icon(icon, null) }, headlineContent = { Text(job.request.take(55)) }, supportingContent = { Text("${job.state} • ${job.id.take(8)}…") })
            }
            item { Text("Android 13 • MIUI 14 • ARM64 • OpenAI only", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
