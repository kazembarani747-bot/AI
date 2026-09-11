@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kazembarani.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.BuildJobLauncher
import com.kazembarani.ai.local.LocalBuildAgent
import com.kazembarani.ai.local.LocalBuildManagerScreen
import com.kazembarani.ai.local.OpenAiClient
import kotlinx.coroutines.launch

private val Blue = Color(0xFF1677FF)
private val BlueLight = Color(0xFFEAF3FF)
private val Ink = Color(0xFF101828)

private enum class AiMode(val title: String, val subtitle: String) {
    CHAT("چت", "دستیار هوشمند"),
    CODE("کدنویسی", "تولید و اصلاح کد"),
    ANDROID("ساخت اپ", "تبدیل ایده به APK"),
    LOCAL_BUILD("استودیو ساخت", "Build و Test واقعی")
}

private data class Message(val text: String, val mine: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    background = Color.White,
                    surface = Color.White,
                    onBackground = Ink,
                    onSurface = Ink
                )
            ) { StudioApp() }
        }
    }
}

@Composable
private fun StudioApp() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
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

        Box(Modifier.fillMaxSize().background(Color.White)) {
            AnimatedContent(targetState = mode, label = "mode") { selected: AiMode ->
                if (selected == AiMode.LOCAL_BUILD) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text("استودیو ساخت", fontWeight = FontWeight.Bold)
                                        Text("محیط کامل پروژه", fontSize = 11.sp)
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { drawer = true }) { Icon(Icons.Default.Menu, "منو") }
                                }
                            )
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) { LocalBuildManagerScreen(agent) }
                    }
                } else {
                    ChatScreen(
                        mode, input, { input = it }, messages, loading, jobId, webMode,
                        { drawer = true }, { toolsOpen = true }
                    ) {
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
                                    val persona = if (mode == AiMode.CODE) {
                                        "You are an expert software engineer. Return useful runnable code and explain briefly."
                                    } else {
                                        "You are a helpful Persian-speaking AI assistant. Be concise, friendly and practical. Support emoji naturally."
                                    }
                                    messages = messages + Message(
                                        if (webMode) ai.askWithWebSearch("$persona\n\n$text")
                                        else ai.ask("$persona\n\n$text"), false
                                    )
                                }
                            } catch (e: Exception) {
                                messages = messages + Message("❌ ${e.message ?: "خطایی رخ داد."}", false)
                            } finally { loading = false }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = drawer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                BuilderDrawer(
                    mode, jobId,
                    { drawer = false },
                    { mode = it; drawer = false },
                    { keyDialog = true; drawer = false },
                    { messages = emptyList(); mode = AiMode.CHAT; jobId = null; drawer = false }
                )
            }
        }

        if (toolsOpen) ModalBottomSheet(onDismissRequest = { toolsOpen = false }) {
            Text("ابزارهای استودیو", Modifier.padding(horizontal = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ToolRow(Icons.Default.Create, "ساخت پروژه", "تبدیل توضیح به BuildPlan") { mode = AiMode.ANDROID; toolsOpen = false }
            ToolRow(Icons.Default.Code, "کدنویسی", "تولید و اصلاح کد") { mode = AiMode.CODE; toolsOpen = false }
            ToolRow(Icons.Default.Terminal, "Build / Test", "مدیریت اجرای پروژه") { mode = AiMode.LOCAL_BUILD; toolsOpen = false }
            ToolRow(Icons.Default.Language, "جست‌وجوی وب", "پاسخ با Web Search") { webMode = !webMode; toolsOpen = false }
            ToolRow(Icons.Default.Tune, "کلید OpenAI", "مدیریت کلید روی دستگاه") { keyDialog = true; toolsOpen = false }
            Spacer(Modifier.height(24.dp))
        }

        if (keyDialog) ApiKeyDialog(context, onDismiss = { if (ApiKeyStore.hasKey(context)) keyDialog = false })
    }
}

@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(15.dp), color = BlueLight) { Icon(icon, null, Modifier.padding(11.dp), tint = Blue) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun BuilderDrawer(mode: AiMode, jobId: String?, onClose: () -> Unit, onMode: (AiMode) -> Unit, onKey: () -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) {
        Column(Modifier.fillMaxHeight().width(330.dp).background(Color.White).padding(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(17.dp), color = BlueLight) { Icon(Icons.Default.AutoAwesome, null, Modifier.padding(13.dp), tint = Blue) }
                Spacer(Modifier.width(10.dp)); Column { Text("AI Builder", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("نسخه ۱۳ • Studio", fontSize = 12.sp, color = Blue) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("فضاهای کاری", Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiMode.values().forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(if (item == AiMode.LOCAL_BUILD) Icons.Default.Build else Icons.Default.AutoAwesome, null) },
                    label = { Column { Text(item.title); Text(item.subtitle, fontSize = 11.sp) } },
                    selected = mode == item,
                    onClick = { onMode(item) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (jobId != null) Surface(shape = RoundedCornerShape(16.dp), color = BlueLight, modifier = Modifier.fillMaxWidth().padding(5.dp)) {
                Text("🔨 ساخت فعال\n${jobId.take(10)}…", Modifier.padding(14.dp), fontSize = 12.sp, color = Ink)
            }
            NavigationDrawerItem(icon = { Icon(Icons.Default.Key, null) }, label = { Text("کلید OpenAI") }, selected = false, onClick = onKey)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("گفت‌وگوی جدید") }, selected = false, onClick = onNew)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) { Text("بستن") }
        }
    }
}

@Composable
private fun ChatScreen(mode: AiMode, input: String, onInput: (String) -> Unit, messages: List<Message>, loading: Boolean, jobId: String?, webMode: Boolean, onMenu: () -> Unit, onTools: () -> Unit, onSend: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    val sendScale by animateFloatAsState(if (input.isBlank() || loading) .92f else 1f, tween(220, easing = FastOutSlowInEasing), label = "send-scale")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "منو") } },
                actions = { if (webMode) AssistChip(onClick = { }, label = { Text("وب") }, modifier = Modifier.padding(end = 6.dp)); IconButton(onClick = onTools) { Icon(Icons.Default.Tune, "ابزارها") } }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 14.dp, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    if (mode == AiMode.ANDROID) Text("💡 پروژه را با توضیح طبیعی توصیف کن؛ AI برایت BuildPlan می‌سازد.", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = onTools) { Icon(Icons.Default.Add, "ابزارها") }
                        OutlinedTextField(value = input, onValueChange = onInput, enabled = !loading, modifier = Modifier.weight(1f), maxLines = 6, placeholder = { Text(if (mode == AiMode.ANDROID) "مثلاً یک بازی سه‌بعدی بساز… 🚀" else "پیامت را بنویس… 😊") }, shape = RoundedCornerShape(27.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue))
                        Spacer(Modifier.width(6.dp)); FilledIconButton(onClick = onSend, enabled = input.isNotBlank() && !loading, modifier = Modifier.graphicsLayer { scaleX = sendScale; scaleY = sendScale }) { Icon(Icons.Default.Send, "ارسال") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (jobId != null && mode == AiMode.ANDROID) AssistChip(onClick = { }, label = { Text("🏗️ ساخت پس‌زمینه • ${jobId.take(8)}…") }, modifier = Modifier.padding(12.dp))
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("چه چیزی می‌سازیم؟ ✨", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp)); Text("با زبان طبیعی ایده‌ات را بگو.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp)); AssistChip(onClick = { onInput("یک اپ مدیریت کارها با رابط فارسی و ذخیره محلی بساز") }, label = { Text("🧩 ساخت یک اپ نمونه") })
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages) { msg -> MessageBubble(msg) }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(20.dp), color = if (msg.mine) BlueLight else Color(0xFFF5F7FA), tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 340.dp)) {
            Text(msg.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink)
        }
    }
}

@Composable
private fun ApiKeyDialog(context: android.content.Context, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کلید OpenAI") },
        text = {
            Column {
                Text("کلید فقط روی همین دستگاه به‌صورت رمزگذاری‌شده ذخیره می‌شود.", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = key, onValueChange = { key = it; error = null }, singleLine = true, label = { Text("API key") })
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank() && !busy, onClick = {
                busy = true
                try { ApiKeyStore.save(context, key.trim()); error = null; onDismiss() }
                catch (e: Exception) { error = e.message ?: "ذخیره کلید ناموفق بود." }
                finally { busy = false }
            }) { Text(if (busy) "در حال ذخیره…" else "ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}
