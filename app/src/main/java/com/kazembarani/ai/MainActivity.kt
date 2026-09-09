package com.kazembarani.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

private data class Message(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(listOf<Message>()) }
        var showInfo by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("AI", fontSize = 28.sp, modifier = Modifier.padding(24.dp))
                    NavigationDrawerItem(
                        label = { Text("گفت‌وگوی جدید") }, selected = false,
                        onClick = {
                            messages = emptyList()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("درباره برنامه") }, selected = false,
                        onClick = {
                            showInfo = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            ChatScreen(input, { input = it }, messages, { messages = it }) {
                scope.launch { drawerState.open() }
            }
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("باشه") } },
                title = { Text("AI — نسخه ۱") },
                text = { Text("نسخه اول رابط چت و مدیریت گفت‌وگوها را دارد. اتصال امن به سرویس هوش مصنوعی از طریق بک‌اند انجام می‌شود تا کلید API داخل APK قرار نگیرد.") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    input: String,
    onInput: (String) -> Unit,
    messages: List<Message>,
    onMessages: (List<Message>) -> Unit,
    onMenu: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI") }, navigationIcon = {
                IconButton(onClick = onMenu) { Text("☰", fontSize = 24.sp) }
            })
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = onInput, modifier = Modifier.weight(1f),
                    placeholder = { Text("پیامت را بنویس...") }, maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        onMessages(messages + Message(text, true) + Message("رابط نسخه اول آماده است؛ اتصال امن به سرویس AI در مرحله بعد فعال می‌شود. 🚀", false))
                        onInput("")
                    }
                }) { Text("ارسال") }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("سلام! 👋\nسؤال خودت را بپرس تا با هم جلو برویم.", textAlign = TextAlign.Center, fontSize = 20.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                items(messages) { msg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End
                    ) {
                        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                            Text(msg.text, Modifier.padding(12.dp), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
