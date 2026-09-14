package com.kazembarani.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kazembarani.ai.local.V171RubikaScheduler
import com.kazembarani.ai.local.V171RubikaStore

class V171RubikaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        V171RubikaScheduler.start(this)
        setContent {
            MaterialTheme {
                RubikaSettings(
                    token = V171RubikaStore.token(this).orEmpty(),
                    owner = V171RubikaStore.ownerId(this),
                    removeLinks = V171RubikaStore.removeLinks(this),
                    autoReply = V171RubikaStore.autoReply(this),
                    funnyMode = V171RubikaStore.funnyMode(this),
                    onSave = { token, owner ->
                        V171RubikaStore.save(this, token, owner)
                        V171RubikaScheduler.start(this)
                    },
                    onRemoveLinks = { V171RubikaStore.setRemoveLinks(this, it) },
                    onAutoReply = { V171RubikaStore.setAutoReply(this, it) },
                    onFunnyMode = { V171RubikaStore.setFunnyMode(this, it) }
                )
            }
        }
    }
}

@Composable
private fun RubikaSettings(
    token: String,
    owner: String,
    removeLinks: Boolean,
    autoReply: Boolean,
    funnyMode: Boolean,
    onSave: (String, String) -> Unit,
    onRemoveLinks: (Boolean) -> Unit,
    onAutoReply: (Boolean) -> Unit,
    onFunnyMode: (Boolean) -> Unit
) {
    var tokenText by remember { mutableStateOf(token) }
    var ownerText by remember { mutableStateOf(owner) }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
        item { Text("🤖 Rubika Bot • V17.1", style = MaterialTheme.typography.headlineSmall) }
        item { Text("توکن ربات و شناسه مالک فقط روی همین دستگاه نگه‌داری می‌شوند. توکن وارد لاگ یا GitHub نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(tokenText, { tokenText = it }, Modifier.fillMaxWidth(), label = { Text("Bot Token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
        item { OutlinedTextField(ownerText, { ownerText = it }, Modifier.fillMaxWidth(), label = { Text("Owner ID 👑") }, singleLine = true) }
        item { Button({ if (tokenText.isNotBlank() && ownerText.isNotBlank()) { onSave(tokenText, ownerText); saved = true } }, enabled = tokenText.isNotBlank() && ownerText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saved) "ذخیره شد ✓" else "ذخیره تنظیمات") } }
        item { SettingSwitch("پاسخ خودکار", autoReply, onAutoReply) }
        item { SettingSwitch("پردازش حذف لینک", removeLinks, onRemoveLinks) }
        item { SettingSwitch("حالت شوخ‌طبعی 😂", funnyMode, onFunnyMode) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قواعد مالک 👑", style = MaterialTheme.typography.titleMedium)
                Text("دستورهای مدیریتی فقط وقتی مجازند که فرستنده با Owner ID برابر باشد. پیام‌های عادی می‌توانند برای پاسخ AI پردازش شوند.")
                Text("Polling واقعی با WorkManager زمان‌بندی می‌شود؛ Android حداقل بازهٔ ۱۵ دقیقه‌ای برای کار دوره‌ای را اعمال می‌کند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}
