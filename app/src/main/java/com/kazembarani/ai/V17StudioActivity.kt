package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
import com.kazembarani.ai.local.V17SelfImprovementStore
import com.kazembarani.ai.local.V171RubikaScheduler
import com.kazembarani.ai.local.V171RubikaStore
import com.kazembarani.ai.local.V171SelfUpdateManager
import kotlinx.coroutines.launch

class V17StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ApiKeyStore.hasKey(this)) { startActivity(Intent(this, WelcomeActivity::class.java)); finish(); return }
        V171RubikaScheduler.start(this)
        setContent {
            V172Home(
                memoryCount = V17MemoryStore.recent(this, 100).size,
                accountEmail = V17AccountStore.email(this),
                selfImprovementState = V17SelfImprovementStore.state(this),
                rubikaConfigured = !V171RubikaStore.token(this).isNullOrBlank(),
                openStudio = { startActivity(Intent(this, StudioActivity::class.java)) },
                openWorkspace = { startActivity(Intent(this, PhoneWorkspaceActivity::class.java)) },
                openRubika = { startActivity(Intent(this, V171RubikaActivity::class.java)) },
                openVoice = { startActivity(Intent(this, V171VoiceActivity::class.java)) },
                checkUpdate = { checkForUpdate() },
                saveMemory = { V17MemoryStore.add(this, it) },
                setSelfImprovementEnabled = { V17SelfImprovementStore.setEnabled(this, it) }
            )
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val manager = V171SelfUpdateManager(this@V17StudioActivity)
            when (val result = manager.checkLatest()) {
                is V171SelfUpdateManager.CheckResult.UpToDate -> Toast.makeText(this@V17StudioActivity, "نسخه 17.2 شما به‌روز است (${result.tag}) ✓", Toast.LENGTH_LONG).show()
                is V171SelfUpdateManager.CheckResult.Failed -> Toast.makeText(this@V17StudioActivity, result.message, Toast.LENGTH_LONG).show()
                is V171SelfUpdateManager.CheckResult.UpdateAvailable -> {
                    Toast.makeText(this@V17StudioActivity, "نسخه جدید پیدا شد؛ در حال دانلود…", Toast.LENGTH_SHORT).show()
                    runCatching { val apk = manager.download(result.release); manager.clearOldUpdates(apk); startActivity(manager.installIntent(apk)) }
                        .onFailure { Toast.makeText(this@V17StudioActivity, "دانلود/آماده‌سازی به‌روزرسانی ناموفق بود: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}

@Composable
private fun V172Home(memoryCount: Int, accountEmail: String?, selfImprovementState: V17SelfImprovementStore.State, rubikaConfigured: Boolean, openStudio: () -> Unit, openWorkspace: () -> Unit, openRubika: () -> Unit, openVoice: () -> Unit, checkUpdate: () -> Unit, saveMemory: (String) -> Unit, setSelfImprovementEnabled: (Boolean) -> Unit) {
    var memoryText by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var selfImprovementEnabled by remember { mutableStateOf(selfImprovementState.enabled) }
    MaterialTheme {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.AutoAwesome, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.width(12.dp)); Column { Text("AI", fontSize = 38.sp); Text("V17.2 • OpenAI", color = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.height(8.dp)); Text("Chat، Memory، Agent، Android، Voice و Bot", fontSize = 20.sp) }
            item { Button(openStudio, Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(8.dp)); Text("ورود به Chat 🚀") } }
            item { OutlinedButton(openWorkspace, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Smartphone, null); Spacer(Modifier.width(8.dp)); Text("📱 ابزارهای گوشی و Runtime") } }
            item { OutlinedButton(openVoice, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("🎙️ Voice") } }
            item { OutlinedButton(openRubika, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(if (rubikaConfigured) "🤖 Rubika Bot ✓" else "🤖 راه‌اندازی Rubika Bot") } }
            item { OutlinedButton(checkUpdate, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text("⬇️ بررسی و به‌روزرسانی AI 17.2") } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("🧠 حافظه", fontSize = 18.sp); Text("حافظه‌های ذخیره‌شده: $memoryCount"); OutlinedTextField(memoryText, { memoryText = it }, Modifier.fillMaxWidth(), label = { Text("چیزی که می‌خواهی AI به خاطر بسپارد") }); Button({ if (memoryText.isNotBlank()) { saveMemory(memoryText); memoryText = ""; saved = true } }, enabled = memoryText.isNotBlank()) { Text(if (saved) "ذخیره شد ✓" else "ذخیره در حافظه") } } } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("👤 حساب", fontSize = 18.sp); Text(accountEmail ?: "هنوز حساب ایمیلی ثبت نشده"); Text("هویت ایمیل فعلاً محلی است؛ ورود سروری بدون بک‌اند واقعی ادعا نمی‌شود.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("♻️ خودبهبوددهی کنترل‌شده", fontSize = 18.sp); Text("تحقیق → پیشنهاد → اعتبارسنجی → Build/Test → آماده‌سازی نسخه؛ تغییرات حساس بدون کنترل کاربر اعمال نمی‌شوند.", fontSize = 13.sp); Row(verticalAlignment = Alignment.CenterVertically) { Text("فعال", Modifier.weight(1f)); Switch(checked = selfImprovementEnabled, onCheckedChange = { selfImprovementEnabled = it; setSelfImprovementEnabled(it) }) }; Text("وضعیت: ${selfImprovementState.lastResult}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            item { Text("🛡️ V17.2", fontSize = 17.sp); Text("Persistent Chats • Local Build/Test/Repair • Phone Intents • Voice • Rubika • Controlled Self-Update", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
