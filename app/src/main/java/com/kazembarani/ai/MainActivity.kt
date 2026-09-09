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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private data class Message(val text: String, val fromUser: Boolean)

private val httpClient = OkHttpClient()
private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiApp() }
    }
}

private suspend fun askAi(message: String): String = withContext(Dispatchers.IO) {
    if (BuildConfig.AI_API_URL.contains("YOUR_BACKEND_URL")) {
        return@withContext "اتصال سرور هنوز تنظیم نشده است. بک‌اند امن برنامه باید قبل از استفاده نهایی روی یک آدرس واقعی قرار بگیرد."
    }

    val payload = JSONObject().put("message", message).toString()
    val request = Request.Builder()
        .url(BuildConfig.AI_API_URL)
        .post(payload.toRequestBody(jsonMediaType))
        .build()

    try {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext "خطا در ارتباط با سرور AI (${response.code})."
            }
            JSONObject(body).optString("text", "پاسخی دریافت نشد.")
        }
    } catch (e: Exception) {
        "ارتباط با سرور برقرار نشد. اینترنت و آدرس بک‌اند را بررسی کن."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(listOf<Message>()) }
        var showInfo by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
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
            ChatScreen(
                input = input,
                onInput = { input = it },
                messages = messages,
                loading = loading,
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !loading) {
                        messages = messages + Message(text, true)
                        input = ""
                        loading = true
                        scope.launch {
                            val answer = askAi(text)
                            messages = messages + Message(answer, false)
                            loading = false
                        }
                    }
                },
                onMenu = { scope.launch { drawerState.open() } }
            )
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("باشه") } },
                title = { Text("AI — نسخه ۱") },
                text = { Text("این نسخه به یک بک‌اند امن متصل می‌شود. کلید OpenAI داخل APK قرار نمی‌گیرد و جست‌وجوی وب از سمت سرور انجام می‌شود.") }
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
    loading: Boolean,
    onSend: () -> Unit,
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
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("پیامت را بنویس...") },
                    maxLines = 4,
                    enabled = !loading
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSend, enabled = input.isNotBlank() && !loading) {
                    Text(if (loading) "..." else "ارسال")
                }
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
                if (loading) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.End) {
                            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                                Text("در حال فکر کردن…", Modifier.padding(12.dp), fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
