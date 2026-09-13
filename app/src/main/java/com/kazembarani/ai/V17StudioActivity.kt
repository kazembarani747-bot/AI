package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.V17AccountStore
import com.kazembarani.ai.local.V17MemoryStore

class V17StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ApiKeyStore.hasKey(this)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        setContent {
            V17Home(
                memoryCount = V17MemoryStore.recent(this, 100).size,
                accountEmail = V17AccountStore.email(this),
                openStudio = { startActivity(Intent(this, StudioActivity::class.java)) },
                openWorkspace = { startActivity(Intent(this, PhoneWorkspaceActivity::class.java)) },
                saveMemory = { V17MemoryStore.add(this, it) }
            )
        }
    }
}

@Composable
private fun V17Home(
    memoryCount: Int,
    accountEmail: String?,
    openStudio: () -> Unit,
    openWorkspace: () -> Unit,
    saveMemory: (String) -> Unit
) {
    var memoryText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    MaterialTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("AI", fontSize = 38.sp)
                        Text("V17.0 • OpenAI", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("نسل جدید دستیار شخصی، حافظه و ابزارهای گوشی", fontSize = 20.sp)
            }
            item { Button(openStudio, Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(8.dp)); Text("ورود به Studio 🚀") } }
            item { OutlinedButton(openWorkspace, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Smartphone, null); Spacer(Modifier.width(8.dp)); Text("📱 ابزارهای گوشی") } }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🧠 حافظه V17", fontSize = 18.sp)
                        Text("حافظه‌های ذخیره‌شده: $memoryCount")
                        OutlinedTextField(value = memoryText, onValueChange = { memoryText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("چیزی که می‌خواهی AI به خاطر بسپارد") })
                        Button(onClick = { if (memoryText.isNotBlank()) { saveMemory(memoryText); memoryText = ""; saved = true } }, enabled = memoryText.isNotBlank()) { Text(if (saved) "ذخیره شد ✓" else "ذخیره در حافظه") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("👤 حساب", fontSize = 18.sp)
                        Text(accountEmail ?: "هنوز حساب ایمیلی ثبت نشده")
                        Text("V17.0 فعلاً زیرساخت هویت ایمیل را دارد؛ احراز هویت سروری در مرحله بعدی متصل می‌شود.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("🛡️ مسیر توسعه", fontSize = 17.sp)
                Text("Memory → Account → Phone Tools → Voice → Agent → Self-Expansion", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
