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
import com.kazembarani.ai.local.LocalBuildAgent
import com.kazembarani.ai.local.LocalBuildManagerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private data class Message(val text: String, val fromUser: Boolean)
private enum class AiMode(val title: String, val path: String) {
    CHAT("پاسخ و جست‌وجوی وب", "/v1/chat"),
    CODE("کدنویسی", "/v1/code"),
    ANDROID("ساخت پروژه اندروید", "/v1/android-project"),
    LOCAL_BUILD("ساخت و تست روی گوشی", "")
}

private val httpClient = OkHttpClient()
private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiApp() }
    }
}

private suspend fun askAi(message: String, mode: AiMode): String = withContext(Dispatchers.IO) {
    if (BuildConfig.AI_API_URL.contains("YOUR_BACKEND_URL")) {
        return@withContext "اتصال سرور هنوز تنظیم نشده است. بک‌اند امن برنامه باید روی یک آدرس واقعی قرار بگیرد."
    }
    val baseUrl = BuildConfig.AI_API_URL.substringBeforeLast("/v1/chat")
    val url = baseUrl + mode.path
    val payload = JSONObject().put("message", message).toString()
    val request = Request.Builder().url(url).post(payload.toRequestBody(jsonMediaType)).build()
    try {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext "خطا در ارتباط با سرور AI (${response.code})."
            val json = JSONObject(body)
            if (mode == AiMode.ANDROID) {
                val name = json.optString("name", "پروژه اندروید")
                val summary = json.optString("summary", "پروژه تولید شد.")
                val files = json.optJSONArray("files")?.length() ?: 0
                return@withContext "✅ $name\n\n$summary\n\n📁 تعداد فایل‌های تولیدشده: $files\n\nپروژه می‌تواند وارد Local Build Manager شود."
            }
            json.optString("text", "پاسخی دریافت نشد.")
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
        var mode by remember { mutableStateOf(AiMode.CHAT) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val localAgent = remember(context) { LocalBuildAgent(context.applicationContext) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text("AI", fontSize = 28.sp, modifier = Modifier.padding(24.dp))
                    AiMode.values().forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title) }, selected = mode == item,
                            onClick = { mode = item; scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("گفت‌وگوی جدید") }, selected = false,
                        onClick = { messages = emptyList(); scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("درباره برنامه") }, selected = false,
                        onClick = { showInfo = true; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            if (mode == AiMode.LOCAL_BUILD) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(mode.title) }, navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰", fontSize = 24.sp) }
                        })
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        LocalBuildManagerScreen(localAgent)
                    }
                }
            } else {
                ChatScreen(
                    mode = mode, input = input, onInput = { input = it }, messages = messages,
                    loading = loading,
                    onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty() && !loading) {
                            messages = messages + Message(text, true)
                            input = ""
                            loading = true
                            scope.launch {
                                messages = messages + Message(askAi(text, mode), false)
                                loading = false
                            }
                        }
                    },
                    onMenu = { scope.launch { drawerState.open() } }
                )
            }
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("باشه") } },
                title = { Text("AI — نسخه ۲") },
                text = { Text("این نسخه علاوه بر پاسخ، کدنویسی و تولید پروژه، یک Local Build Manager برای آماده‌سازی ساخت و تست روی خود گوشی دارد. کلید OpenAI داخل APK قرار نمی‌گیرد.") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    mode: AiMode, input: String, onInput: (String) -> Unit, messages: List<Message>,
    loading: Boolean, onSend: () -> Unit, onMenu: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(mode.title) }, navigationIcon = {
                IconButton(onClick = onMenu) { Text("☰", fontSize = 24.sp) }
            })
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = onInput, modifier = Modifier.weight(1f),
                    placeholder = { Text(if (mode == AiMode.ANDROID) "مثلاً: یک اپ یادداشت بساز..." else "پیامت را بنویس...") },
                    maxLines = 4, enabled = !loading
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
                Text("سلام! 👋\nحالت «${mode.title}» فعال است.", textAlign = TextAlign.Center, fontSize = 20.sp)
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
                if (loading) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.End) {
                        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                            Text("در حال کار کردن…", Modifier.padding(12.dp), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
