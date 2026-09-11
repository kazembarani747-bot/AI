package com.kazembarani.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.BuildJobLauncher
import com.kazembarani.ai.local.LocalBuildAgent
import com.kazembarani.ai.local.LocalBuildManagerScreen
import com.kazembarani.ai.local.OpenAiClient
import kotlinx.coroutines.launch

private data class Message(val text: String, val fromUser: Boolean)
private enum class AiMode(val title: String, val subtitle: String) {
    CHAT("گفت‌وگو", "پرسش، ایده و کمک روزمره"),
    CODE("کدنویسی", "تولید و اصلاح کد"),
    ANDROID("ساخت اپ", "تبدیل ایده به پروژه Android"),
    LOCAL_BUILD("ساخت و تست", "مدیریت پروژه‌های روی گوشی")
}

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AiApp() }
    }
}

@Composable
private fun AiApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(emptyList<Message>()) }
        var loading by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(AiMode.CHAT) }
        var jobId by remember { mutableStateOf<String?>(null) }
        var showDrawer by remember { mutableStateOf(false) }
        var showKeyDialog by remember { mutableStateOf(!ApiKeyStore.hasKey(androidx.compose.ui.platform.LocalContext.current)) }
        var showAbout by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val agent = remember(context) { LocalBuildAgent(context.applicationContext) }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showDrawer) {
                Drawer(
                    mode = mode,
                    jobId = jobId,
                    onClose = { showDrawer = false },
                    onMode = { mode = it; showDrawer = false },
                    onKey = { showKeyDialog = true; showDrawer = false },
                    onNew = { messages = emptyList(); mode = AiMode.CHAT; jobId = null; showDrawer = false },
                    onAbout = { showAbout = true; showDrawer = false }
                )
            }

            AnimatedContent(targetState = mode, label = "mode") { selected ->
                if (selected == AiMode.LOCAL_BUILD) {
                    Scaffold(topBar = { TopAppBar(
                        title = { Text("ساخت و تست", fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton(onClick = { showDrawer = true }) { Icon(Icons.Default.Menu, "منو") } }
                    ) }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { LocalBuildManagerScreen(agent) } }
                } else {
                    ChatScreen(
                        mode, input, { input = it }, messages, loading, jobId,
                        onMenu = { showDrawer = true },
                        onSend = {
                            val text = input.trim()
                            if (text.isBlank() || loading || !ApiKeyStore.hasKey(context)) {
                                if (!ApiKeyStore.hasKey(context)) showKeyDialog = true
                                return@ChatScreen
                            }
                            messages = messages + Message(text, true)
                            input = ""
                            loading = true
                            scope.launch {
                                try {
                                    val ai = OpenAiClient(context)
                                    if (mode == AiMode.ANDROID) {
                                        val plan = ai.generateBuildPlan(text)
                                        val id = BuildJobLauncher.enqueue(context, text, plan.toJson().toString(), 30, false)
                                        jobId = id
                                        messages = messages + Message("✅ پروژه «${plan.projectName}» آماده شد.\n\n${plan.summary}\n\n📁 ${plan.files.size} فایل تولید شد.\n\n🚀 کار ساخت در پس‌زمینه ثبت شد.", false)
                                    } else {
                                        val prefix = if (mode == AiMode.CODE) "You are an expert software engineer. Return useful, runnable code and explain briefly.\n\n" else "You are a helpful Persian-speaking AI assistant.\n\n"
                                        messages = messages + Message(ai.ask(prefix + text), false)
                                    }
                                } catch (e: Exception) {
                                    messages = messages + Message("❌ ${e.message ?: "خطایی رخ داد."}", false)
                                } finally { loading = false }
                            }
                        }
                    )
                }
            }
        }

        if (showKeyDialog) ApiKeyDialog(context, onDismiss = { if (ApiKeyStore.hasKey(context)) showKeyDialog = false })
        if (showAbout) AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("باشه") } },
            title = { Text("AI Builder — مرحله ۱۰") },
            text = { Text("نسخه جدید با اتصال مستقیم به OpenAI، ذخیره امن کلید روی دستگاه، رابط کاربری متحرک و صف ساخت پس‌زمینه طراحی شده است.") }
        )
    }
}

@Composable
private fun Drawer(
    mode: AiMode, jobId: String?, onClose: () -> Unit, onMode: (AiMode) -> Unit,
    onKey: () -> Unit, onNew: () -> Unit, onAbout: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f))) {
        Column(Modifier.fillMaxHeight().width(320.dp).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 5.dp) { Icon(Icons.Default.AutoAwesome, null, Modifier.padding(12.dp)) }
                Spacer(Modifier.width(10.dp)); Column { Text("AI Builder", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("مرحله ۱۰", fontSize = 12.sp) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("حالت‌ها", Modifier.padding(12.dp), fontSize = 12.sp)
            AiMode.values().forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(if (item == AiMode.LOCAL_BUILD) Icons.Default.Build else Icons.Default.AutoAwesome, null) },
                    label = { Column { Text(item.title); Text(item.subtitle, fontSize = 11.sp) } },
                    selected = mode == item,
                    onClick = { onMode(item) }, Modifier.padding(vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(jobId != null, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Text("🔨 ساخت پس‌زمینه فعال\n${jobId?.take(8)}…", Modifier.padding(14.dp), fontSize = 12.sp)
                }
            }
            NavigationDrawerItem(icon = { Icon(Icons.Default.Key, null) }, label = { Text("کلید OpenAI") }, selected = false, onClick = onKey)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("گفت‌وگوی جدید") }, selected = false, onClick = onNew)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("تنظیمات") }, selected = false, onClick = onKey)
            NavigationDrawerItem(label = { Text("درباره") }, selected = false, onClick = onAbout)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) { Text("بستن") }
        }
    }
}

@Composable
private fun ChatScreen(
    mode: AiMode, input: String, onInput: (String) -> Unit, messages: List<Message>, loading: Boolean, jobId: String?,
    onMenu: () -> Unit, onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, fontSize = 11.sp) } },
            navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "منو") } },
            actions = { Icon(Icons.Default.AutoAwesome, "AI", Modifier.padding(horizontal = 16.dp)) }
        )
    }, bottomBar = {
        Surface(shadowElevation = 12.dp, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { }) { Icon(Icons.Default.Add, "افزودن") }
                OutlinedTextField(
                    value = input, onValueChange = onInput, enabled = !loading,
                    modifier = Modifier.weight(1f), maxLines = 5,
                    placeholder = { Text(if (mode == AiMode.ANDROID) "مثلاً یک بازی سه‌بعدی بساز…" else "پیامت را بنویس…") },
                    shape = RoundedCornerShape(26.dp)
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = onSend, enabled = input.isNotBlank() && !loading) { Icon(Icons.Default.Send, "ارسال") }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (jobId != null && mode == AiMode.ANDROID) {
                AssistChip(onClick = { }, label = { Text("ساخت در پس‌زمینه • ${jobId.take(8)}…") }, modifier = Modifier.padding(12.dp))
            }
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(42.dp))
                        }
                        Spacer(Modifier.height(18.dp)); Text("سلام 👋", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp)); Text("چه چیزی می‌خوای بسازیم؟", fontSize = 20.sp)
                        Spacer(Modifier.height(8.dp)); Text("از سؤال ساده تا ساخت یک پروژه Android.", textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End) {
                            Surface(
                                color = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 350.dp)
                            ) { Text(msg.text, Modifier.padding(16.dp), fontSize = 16.sp) }
                        }
                    }
                    item {
                        AnimatedVisibility(loading, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) { Text("✨ در حال فکر کردن…", Modifier.padding(14.dp), fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(context: Context, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (ApiKeyStore.hasKey(context)) onDismiss() },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Key, null); Spacer(Modifier.width(8.dp)); Text("اتصال به OpenAI") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("کلید API خودت را وارد کن. کلید به‌صورت رمزنگاری‌شده روی همین دستگاه ذخیره می‌شود و داخل GitHub قرار نمی‌گیرد.", fontSize = 13.sp)
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true, label = { Text("OpenAI API Key") }, placeholder = { Text("sk-…") })
                if (status.isNotBlank()) Text(status, fontSize = 12.sp)
            }
        },
        dismissButton = { if (ApiKeyStore.hasKey(context)) TextButton(onClick = onDismiss) { Text("بعداً") } },
        confirmButton = {
            Row {
                TextButton(enabled = key.isNotBlank() && !testing, onClick = {
                    ApiKeyStore.save(context, key)
                    testing = true; status = "در حال بررسی کلید…"
                    scope.launch {
                        status = runCatching { OpenAiClient(context).testKey(); "✅ کلید معتبر است." }.getOrElse { "❌ ${it.message}" }
                        testing = false
                    }
                }) { Text(if (testing) "بررسی…" else "ذخیره و بررسی") }
                if (ApiKeyStore.hasKey(context)) TextButton(onClick = { ApiKeyStore.clear(context); key = ""; status = "کلید حذف شد." }) { Text("حذف") }
            }
        }
    )
}
