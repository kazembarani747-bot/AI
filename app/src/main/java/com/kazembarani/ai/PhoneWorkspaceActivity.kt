package com.kazembarani.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.WorkspaceStore
import java.io.File

class PhoneWorkspaceActivity : ComponentActivity() {
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showSelected(it) }
    }
    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { exportWorkspace(it) }
    }
    private var selected: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorkspaceScreen(
            projects = WorkspaceStore.listProjects(this),
            selected = selected,
            open = { openFile.launch(arrayOf("*/*")) },
            export = { createFile.launch("AI-Workspace.zip") }
        ) }
    }

    private fun showSelected(uri: Uri) { selected = uri; recreate() }
    private fun exportWorkspace(uri: Uri) {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write("AI Workspace\nProjects: ${WorkspaceStore.listProjects(this).joinToString { it.name }}\n".toByteArray())
        }
    }
}

@Composable
private fun WorkspaceScreen(projects: List<File>, selected: Uri?, open: () -> Unit, export: () -> Unit) {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("فضای کاری گوشی", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📱 دسترسی امن به فایل‌های گوشی", fontSize = 22.sp)
                Text("نسخه 16.2 از Android Storage Access Framework استفاده می‌کند؛ بدون دسترسی گسترده به حافظه.", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = open) { Text("📂 انتخاب فایل") }
                    OutlinedButton(onClick = export) { Text("📦 خروجی Workspace") }
                }
                selected?.let { Text("فایل انتخاب‌شده: $it", fontSize = 12.sp) }
                HorizontalDivider()
                Text("پروژه‌های AI-Workspace", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (projects.isEmpty()) Text("هنوز پروژه‌ای ذخیره نشده است.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(projects) { Text("• ${it.name}") } }
            }
        }
    }
}
