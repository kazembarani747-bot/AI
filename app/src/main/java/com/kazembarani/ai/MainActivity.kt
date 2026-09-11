package com.kazembarani.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.kazembarani.ai.local.BackendSettings
import com.kazembarani.ai.local.BuildJobLauncher
import com.kazembarani.ai.local.LocalBuildAgent
import com.kazembarani.ai.local.LocalBuildManagerScreen

private data class Message(val text: String, val fromUser: Boolean)
private enum class AiMode(val title: String, val path: String) {
    CHAT("گفت‌وگو", "/v1/chat"), CODE("کدنویسی", "/v1/code"), ANDROID("ساخت اپ اندروید", "/v1/android-project"), LOCAL_BUILD("ساخت و تست", "")
}
private val httpClient = OkHttpClient()
private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
private fun savedBackendUrl(context: Context): String = BackendSettings.saved(context)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AiApp() }
    }
}

private suspend fun askAi(context: Context, message: String, mode: AiMode): String = withContext(Dispatchers.IO) {
    val baseUrl = savedBackendUrl(context)
    if (baseUrl.isBlank() || baseUrl.contains("YOUR_BACKEND_URL")) return@withContext "اول آدرس بک‌اند را از تنظیمات وارد کن."
    if (!baseUrl.startsWith("https://")) return@withContext "برای امنیت، آدرس بک‌اند باید با https:// شروع شود."
    val request = Request.Builder().url(baseUrl.trimEnd('/') + mode.path).post(JSONObject().put("message", message).toString().toRequestBody(jsonMediaType)).build()
    try {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext "❌ خطای سرور AI (${response.code})."
            val json = JSONObject(body)
            if (mode == AiMode.ANDROID) {
                val name = json.optString("name", "پروژه اندروید")
                val summary = json.optString("summary", "پروژه تولید شد.")
                val files = json.optJSONArray("files")?.length() ?: 0
                return@withContext "✅ $name\n\n$summary\n\n📁 $files فایل تولید شد.\n\n🚀 ساخت خودکار پروژه در پس‌زمینه شروع می‌شود."
            }
            json.optString("text", "پاسخی دریافت نشد.")
        }
    } catch (_: Exception) { "❌ ارتباط با بک‌اند برقرار نشد. اینترنت و آدرس سرور را بررسی کن." }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(listOf<Message>()) }
        var showInfo by remember { mutableStateOf(false) }
        var showServerSettings by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(AiMode.CHAT) }
        var lastBuildJob by remember { mutableStateOf<String?>(null) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val localAgent = remember(context) { LocalBuildAgent(context.applicationContext) }

        ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp) { Text("AI", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 20.sp) }
                    Spacer(Modifier.width(10.dp)); Text("AI Builder", fontSize = 20.sp)
                }
                HorizontalDivider()
                Text("حالت‌ها", Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp), fontSize = 13.sp)
                AiMode.values().forEach { item ->
                    NavigationDrawerItem(
                        icon = { if (item == AiMode.LOCAL_BUILD) Icon(Icons.Default.Build, null) },
                        label = { Text(item.title) }, selected = mode == item,
                        onClick = { mode = item; scope.launch { drawerState.close() } }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (lastBuildJob != null) Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("ساخت در پس‌زمینه فعال است\nشناسه: ${lastBuildJob!!.take(8)}…", Modifier.padding(14.dp), fontSize = 13.sp)
                }
                NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("تنظیم سرور") }, selected = false,
                    onClick = { showServerSettings = true; scope.launch { drawerState.close() } }, modifier = Modifier.padding(12.dp))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("گفت‌وگوی جدید") }, selected = false,
                    onClick = { messages = emptyList(); mode = AiMode.CHAT; scope.launch { drawerState.close() } }, modifier = Modifier.padding(12.dp))
                NavigationDrawerItem(label = { Text("درباره برنامه") }, selected = false,
                    onClick = { showInfo = true; scope.launch { drawerState.close() } }, modifier = Modifier.padding(12.dp))
            }
        }) {
            if (mode == AiMode.LOCAL_BUILD) {
                Scaffold(topBar = { TopAppBar(title = { Text("ساخت و تست") }, navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } }) }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) { LocalBuildManagerScreen(localAgent) }
                }
            } else ChatScreen(mode, input, { input = it }, messages, loading, lastBuildJob, {
                val text = input.trim()
                if (text.isNotEmpty() && !loading) {
                    messages = messages + Message(text, true); input = ""; loading = true
                    scope.launch {
                        val answer = askAi(context, text, mode); messages = messages + Message(answer, false)
                        if (mode == AiMode.ANDROID && answer.startsWith("✅")) lastBuildJob = BuildJobLauncher.enqueue(context, text, 30, false)
                        loading = false
                    }
                }
            }) { scope.launch { drawerState.open() } }
        }
        if (showServerSettings) ServerSettingsDialog(context) { showServerSettings = false }
        if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, confirmButton = { TextButton(onClick = { showInfo = false }) { Text("باشه") } }, title = { Text("AI Builder — مرحله ۹") }, text = { Text("رابط کاربری بازطراحی شده و به سبک تجربه چت مدرن است. ساخت اپ اندروید بعد از تولید پروژه به‌صورت خودکار وارد صف ساخت پس‌زمینه می‌شود.") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(mode: AiMode, input: String, onInput: (String) -> Unit, messages: List<Message>, loading: Boolean, lastBuildJob: String?, onSend: () -> Unit, onMenu: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(mode.title, fontSize = 17.sp) }, navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, null) } }, actions = { Text("AI", Modifier.padding(horizontal = 16.dp), fontSize = 14.sp) })
    }, bottomBar = {
        Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { }) { Icon(Icons.Default.Add, "افزودن") }
            OutlinedTextField(value = input, onValueChange = onInput, modifier = Modifier.weight(1f), placeholder = { Text(if (mode == AiMode.ANDROID) "مثلاً یک اپ یادداشت بساز…" else "پیامت را بنویس…") }, maxLines = 5, enabled = !loading, shape = RoundedCornerShape(24.dp))
            Spacer(Modifier.width(6.dp)); FilledIconButton(onClick = onSend, enabled = input.isNotBlank() && !loading) { Icon(Icons.Default.Send, "ارسال") }
        } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (lastBuildJob != null && mode == AiMode.ANDROID) AssistChip(onClick = { }, label = { Text("ساخت پس‌زمینه فعال • ${lastBuildJob.take(8)}…") }, modifier = Modifier.padding(12.dp))
            if (messages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("سلام 👋", fontSize = 30.sp); Spacer(Modifier.height(10.dp)); Text("چه کاری می‌خوای با AI Builder انجام بدی؟", fontSize = 19.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp)); Text("می‌تونی سؤال بپرسی، کد بخوای یا مستقیماً یک اپ اندروید بسازی.", fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End) {
                    Surface(tonalElevation = if (msg.fromUser) 1.dp else 0.dp, color = if (msg.fromUser) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 330.dp)) {
                        Text(msg.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp)
                    }
                } }
                if (loading) item { Text("در حال فکر کردن و ساخت…", Modifier.padding(8.dp), fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun ServerSettingsDialog(context: Context, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(savedBackendUrl(context).takeUnless { it.contains("YOUR_BACKEND_URL") }.orEmpty()) }
    var status by remember { mutableStateOf("") }; var checking by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تنظیم سرور AI") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("آدرس HTTPS بک‌اند را وارد کن. برنامه آن را ذخیره می‌کند."); OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, placeholder = { Text("https://example.onrender.com") }); if (status.isNotBlank()) Text(status, fontSize = 13.sp) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }, confirmButton = { Row {
        TextButton(enabled = !checking && url.isNotBlank(), onClick = {
            val clean = BackendSettings.clean(url); if (!clean.startsWith("https://")) { status = "آدرس باید با https:// شروع شود."; return@TextButton }; checking = true; status = "در حال بررسی…"
            scope.launch { val ok = withContext(Dispatchers.IO) { runCatching { val r = Request.Builder().url("$clean/health").get().build(); httpClient.newCall(r).execute().use { it.isSuccessful && JSONObject(it.body?.string().orEmpty()).optBoolean("ok") } }.getOrDefault(false) }; checking = false; status = if (ok) "✅ سرور آنلاین است." else "❌ سرور پاسخ نداد." }
        }) { Text("بررسی") }
        Button(enabled = url.isNotBlank(), onClick = { val clean = BackendSettings.clean(url); if (!clean.startsWith("https://")) { status = "آدرس باید با https:// شروع شود."; return@Button }; BackendSettings.save(context, clean); onDismiss() }) { Text("ذخیره") }
    } })
}
