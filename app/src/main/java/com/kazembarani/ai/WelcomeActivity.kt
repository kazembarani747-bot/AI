package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.ProviderAutoSetup

private val Blue = Color(0xFF1677FF)
private val Ink = Color(0xFF101828)

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ApiKeyStore.hasKey(this)) {
            startActivity(Intent(this, StudioActivity::class.java)); finish(); return
        }
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Color.White, surface = Color.White, onSurface = Ink)) { WelcomeScreen() } }
    }

    private fun openStudio(key: String) {
        runCatching { ProviderAutoSetup.applyOpenAi(this, key) }
            .onSuccess { startActivity(Intent(this, StudioActivity::class.java)); finish() }
    }

    @Composable
    private fun WelcomeScreen() {
        var key by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp)) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFEAF3FF)) { Icon(Icons.Default.AutoAwesome, null, Modifier.padding(18.dp), tint = Blue) }
                Spacer(Modifier.height(18.dp))
                Text("AI", fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("نسخه 16.1 • OpenAI", color = Blue, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("برای شروع فقط کلید OpenAI را وارد کن. اتصال به‌صورت خودکار آماده می‌شود.", fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(key, { key = it; error = null }, Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, placeholder = { Text("sk-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEAF3FF), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Blue); Spacer(Modifier.width(8.dp)); Text("OpenAI • Responses API • gpt-5.6-luna", fontSize = 12.sp)
                    }
                }
                error?.let { Spacer(Modifier.height(8.dp)); Text("❌ $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Spacer(Modifier.height(14.dp))
                Button(enabled = key.trim().isNotBlank(), onClick = { runCatching { ProviderAutoSetup.applyOpenAi(this@WelcomeActivity, key) }.onSuccess { openStudio(key) }.onFailure { error = it.message } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("شروع AI ✨", fontSize = 16.sp) }
                Spacer(Modifier.height(12.dp))
                Text("کلید در ذخیره‌ساز امن برنامه نگهداری می‌شود.", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
