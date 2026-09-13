package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class V16StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V16Home { startActivity(Intent(this, StudioActivity::class.java)) } }
    }
}

@Composable
private fun V16Home(openStudio: () -> Unit) {
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("AI", fontSize = 38.sp)
            Text("V16.1 • OpenAI", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("دستیار شخصی و استودیوی توسعه", fontSize = 20.sp)
            Spacer(Modifier.height(28.dp))
            Button(openStudio, Modifier.fillMaxWidth().height(54.dp)) { Text("ورود به Studio 🚀") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(openStudio, Modifier.fillMaxWidth().height(54.dp)) { Text("💬 گفتگو") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(openStudio, Modifier.fillMaxWidth().height(54.dp)) { Text("🧑‍💻 کدنویسی") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(openStudio, Modifier.fillMaxWidth().height(54.dp)) { Text("📱 ساخت اپ و APK") }
            Spacer(Modifier.height(24.dp))
            Text("Android 13 • MIUI 14 • OpenAI only", fontSize = 12.sp)
        }
    }
}
