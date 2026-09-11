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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    CHAT("گفت‌وگو", "چت هوشمند و خلاق"),
    CODE("کدنویسی", "نوشتن، توضیح و رفع خطا"),
    ANDROID("ساخت اپ", "ایده → پروژه Android → APK"),
    LOCAL_BUILD("استودیو ساخت", "فایل‌ها، Build، Test و خروجی")
}

private val Stage11Blue = Color(0xFF1769E0)
private val Stage11BlueLight = Color(0xFFEAF3FF)
private val Stage11Ink = Color(0xFF111827)
private val Stage11Soft = Color(0xFFF7F9FC)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { Stage11Theme { AiApp() } }
    }
}

@Composable
private fun Stage11Theme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Stage11Blue,
        onPrimary = Color.White,
        primaryContainer = Stage11BlueLight,
        onPrimaryContainer = Stage11Ink,
        secondary = Color(0xFF4D7CC7),
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Stage11Soft,
        onBackground = Stage11Ink,
        onSurface = Stage11Ink,
        onSurfaceVariant = Color(0xFF536174)
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@Composable
private fun AiApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(emptyList<Message>()) }
        var loading by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(AiMode.CHAT) }
        var jobId by remember { mutableStateOf<String?>(null) }
        var drawer by remember { mutableStateOf(false) }
        var keyDialog by remember { mutableStateOf(!ApiKeyStore.hasKey(context)) }
        var toolsOpen by remember { mutableStateOf(false) }
        var webMode by remember { mutableStateOf(false) }
        val agent = remember(context) { LocalBuildAgent(context.applicationContext) }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(targetState = mode, label = "stage11-mode") { selected ->
                if (selected == AiMode.LOCAL_BUILD) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Column { Text("استودیو ساخت", fontWeight = FontWeight.Bold); Text("محیط کامل پروژه", fontSize = 11.sp) } },
                                navigationIcon = { IconButton(onClick = { drawer = true }) { Icon(Icons.Default.Menu, "منو") } }
                            )
                        }
                    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { LocalBuildManagerScreen(agent) } }
                } else {
                    ChatScreen(
                        mode = mode,
                        input = input,
                        onInput = { input = it },
                        messages = messages,
                        loading = loading,
                        jobId = jobId,
                        webMode = webMode,
                        onMenu = { drawer = true },
                        onTools = { toolsOpen = true },
                        onSend = {
                            val text = input.trim()
                            if (text.isBlank() || loading) return@ChatScreen
                            if (!ApiKeyStore.hasKey(context)) { keyDialog = true; return@ChatScreen }
                            messages = messages + Message(text, true)
                            input = ""
                            loading = true
                            scope.launch {
                                try {
                                    val ai = OpenAiClient(context)
                                    if (mode == AiMode.ANDROID) {
                                        val plan = ai.generateBuildPlan(text)
                                        jobId = BuildJobLauncher.enqueue(context, text, plan.toJson().toString(), 30, false)
                                        messages = messages + Message(
                                            "🎉 پروژه «${plan.projectName}» آماده شد!\n\n${plan.summary}\n\n📦 ${plan.files.size} فایل\n🛠️ ${plan.buildTasks.size} مرحله Build\n🧪 ${plan.testTasks.size} تست\n\n🚀 ساخت در پس‌زمینه ثبت شد.", false
                                        )
                                    } else {
                                        val persona = when (mode) {
                                            AiMode.CODE -> "You are an expert software engineer. Return useful runnable code and explain briefly."
                                            else -> "You are a helpful Persian-speaking AI assistant. Be concise, friendly and practical. Support emoji naturally."
                                        }
                                        val answer = if (webMode) ai.askWithWebSearch("$persona\n\n$text") else ai.ask("$persona\n\n$text")
                                        messages = messages + Message(answer, false)
                                    }
                                } catch (e: Exception) {
                                    messages = messages + Message("❌ ${e.message ?: "خطایی رخ داد."}", false)
                                } finally { loading = false }
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = drawer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                Stage11Drawer(
                    mode = mode,
                    jobId = jobId,
                    onClose = { drawer = false },
                    onMode = { mode = it; drawer = false },
                    onKey = { keyDialog = true; drawer = false },
                    onNew = { messages = emptyList(); mode = AiMode.CHAT; jobId = null; drawer = false }
                )
            }
        }

        if (toolsOpen) {
            ModalBottomSheet(onDismissRequest = { toolsOpen = false }) {
                Text("ابزارهای استودیو", Modifier.padding(horizontal = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                ToolRow(Icons.Default.Create, "ساخت پروژه", "تبدیل توضیح به BuildPlan") { mode = AiMode.ANDROID; toolsOpen = false }
                ToolRow(Icons.Default.Code, "کدنویسی", "تولید و اصلاح کد") { mode = AiMode.CODE; toolsOpen = false }
                ToolRow(Icons.Default.Terminal, "Build / Test", "مدیریت اجرای پروژه") { mode = AiMode.LOCAL_BUILD; toolsOpen = false }
                ToolRow(Icons.Default.Language, "جست‌وجوی وب", "پاسخ با ابزار Web Search") { webMode = !webMode; toolsOpen = false }
                ToolRow(Icons.Default.Tune, "کلید OpenAI", "مدیریت کلید روی دستگاه") { keyDialog = true; toolsOpen = false }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (keyDialog) ApiKeyDialog(context, onDismiss = { if (ApiKeyStore.hasKey(context)) keyDialog = false })
    }
}

@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = Stage11BlueLight) { Icon(icon, null, Modifier.padding(11.dp), tint = Stage11Blue) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Stage11Drawer(mode: AiMode, jobId: String?, onClose: () -> Unit, onMode: (AiMode) -> Unit, onKey: () -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) {
        Column(
            Modifier.fillMaxHeight().width(330.dp).background(Color.White).padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(17.dp), color = Stage11BlueLight) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.padding(13.dp), tint = Stage11Blue)
                }
                Spacer(Modifier.width(10.dp))
                Column { Text("AI Builder", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("مرحله ۱۱ • Studio", fontSize = 12.sp, color = Stage11Blue) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("فضاهای کاری", Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiMode.values().forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(if (item == AiMode.LOCAL_BUILD) Icons.Default.Build else Icons.Default.AutoAwesome, null) },
                    label = { Column { Text(item.title); Text(item.subtitle, fontSize = 11.sp) } },
                    selected = mode == item,
                    onClick = { onMode(item) }, Modifier.padding(vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (jobId != null) Surface(shape = RoundedCornerShape(16.dp), color = Stage11BlueLight, modifier = Modifier.fillMaxWidth().padding(5.dp)) {
                Text("🔨 ساخت فعال\n${jobId.take(10)}…", Modifier.padding(14.dp), fontSize = 12.sp, color = Stage11Ink)
            }
            NavigationDrawerItem(icon = { Icon(Icons.Default.Key, null) }, label = { Text("کلید OpenAI") }, selected = false, onClick = onKey)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("گفت‌وگوی جدید") }, selected = false, onClick = onNew)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) { Text("بستن") }
        }
    }
}

@Composable
private fun ChatScreen(
    mode: AiMode, input: String, onInput: (String) -> Unit, messages: List<Message>, loading: Boolean,
    jobId: String?, webMode: Boolean, onMenu: () -> Unit, onTools: () -> Unit, onSend: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val sendScale by animateFloatAsState(if (input.isBlank() || loading) .92f else 1f, tween(220, easing = FastOutSlowInEasing), label = "send-scale")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "منو") } },
                actions = {
                    if (webMode) AssistChip(onClick = { }, label = { Text("وب") }, modifier = Modifier.padding(end = 6.dp))
                    IconButton(onClick = onTools) { Icon(Icons.Default.Tune, "ابزارها") }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 14.dp, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    if (mode == AiMode.ANDROID) Text("💡 پروژه را با توضیح طبیعی توصیف کن؛ AI برایت BuildPlan می‌سازد.", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = onTools) { Icon(Icons.Default.Add, "ابزارها") }
                        OutlinedTextField(
                            value = input, onValueChange = onInput, enabled = !loading,
                            modifier = Modifier.weight(1f), maxLines = 6,
                            placeholder = { Text(if (mode == AiMode.ANDROID) "مثلاً یک بازی سه‌بعدی بساز… 🚀" else "پیامت را بنویس… 😊") },
                            shape = RoundedCornerShape(27.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Stage11Blue)
                        )
                        Spacer(Modifier.width(6.dp))
                        FilledIconButton(
                            onClick = onSend,
                            enabled = input.isNotBlank() && !loading,
                            modifier = Modifier.graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        ) { Icon(Icons.Default.Send, "ارسال") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (jobId != null && mode == AiMode.ANDROID) {
                AssistChip(onClick = { }, label = { Text("🏗️ ساخت پس‌زمینه • ${jobId.take(8)}…") }, modifier = Modifier.padding(12.dp))
            }
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Stage11BlueLight, Color.White))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(46.dp), tint = Stage11Blue) }
                        Spacer(Modifier.height(18.dp))
                        Text("سلام 👋", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Stage11Ink)
                        Spacer(Modifier.height(8.dp))
                        Text("چه چیزی می‌خوای بسازیم؟", fontSize = 21.sp, color = Stage11Ink)
                        Spacer(Modifier.height(8.dp))
                        Text("گفت‌وگو، کدنویسی یا ساخت یک پروژه کامل Android.", textAlign = TextAlign.Center, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip({ onInput("یک اپ ساده و زیبا برای یادداشت بساز") }, "📝 اپ")
                            SuggestionChip({ onInput("این کد را برایم توضیح بده") }, "💻 کد")
                            SuggestionChip({ onInput("یک ایده بازی سه‌بعدی پیشنهاد بده") }, "🎮 بازی")
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.Start else Arrangement.End) {
                            val bubbleColor by animateColorAsState(if (msg.fromUser) Stage11BlueLight else Stage11Soft, tween(250), label = "bubble")
                            Surface(color = bubbleColor, shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 370.dp)) {
                                Text(msg.text, Modifier.padding(16.dp), fontSize = 16.sp, color = Stage11Ink)
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(loading, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                            Surface(shape = RoundedCornerShape(18.dp), color = Stage11BlueLight) { Text("✨ در حال فکر کردن…", Modifier.padding(14.dp), fontSize = 13.sp, color = Stage11Ink) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(onClick: () -> Unit, label: String) {
    AssistChip(onClick = onClick, label = { Text(label, fontSize = 12.sp) })
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
                Text("کلید API روی همین دستگاه به‌صورت رمزنگاری‌شده نگه‌داری می‌شود و داخل GitHub قرار نمی‌گیرد.", fontSize = 13.sp)
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
