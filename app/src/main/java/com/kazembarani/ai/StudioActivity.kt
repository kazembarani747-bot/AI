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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazembarani.ai.local.AiProviderConfig
import com.kazembarani.ai.local.ApiKeyStore
import com.kazembarani.ai.local.BuildJobLauncher
import com.kazembarani.ai.local.LocalBuildAgent
import com.kazembarani.ai.local.LocalBuildManagerScreen
import com.kazembarani.ai.local.OpenAiClient
import kotlinx.coroutines.launch

private val StudioBlue = Color(0xFF1677FF)
private val StudioBlueLight = Color(0xFFEAF3FF)
private val StudioInk = Color(0xFF101828)
private const val VERSION = "16.5"

private enum class Mode(val title: String, val subtitle: String) {
    CHAT("چت", "دستیار هوشمند"), CODE("کدنویسی", "تولید و اصلاح کد"), ANDROID("ساخت اپ", "ایده → پروژه → APK"), BUILD("استودیو ساخت", "Build • Test • Diagnose")
}
private data class Msg(val text: String, val mine: Boolean)

class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme(primary = StudioBlue, background = Color.White, surface = Color.White, onSurface = StudioInk)) { StudioRoot() } } }
}

@Composable
private fun StudioRoot() {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val agent = remember(context) { LocalBuildAgent(context.applicationContext) }
        var mode by remember { mutableStateOf(Mode.CHAT) }
        var input by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(emptyList<Msg>()) }
        var loading by remember { mutableStateOf(false) }
        var drawer by remember { mutableStateOf(false) }
        var tools by remember { mutableStateOf(false) }
        var provider by remember { mutableStateOf(!ApiKeyStore.hasKey(context)) }
        var web by remember { mutableStateOf(false) }
        var job by remember { mutableStateOf<String?>(null) }

        Box(Modifier.fillMaxSize()) {
            AnimatedContent(targetState = mode, label = "mode") { selected ->
                if (selected == Mode.BUILD) {
                    Scaffold(topBar = { TopAppBar(title = { Column { Text("استودیو ساخت", fontWeight = FontWeight.Bold); Text("نسخه $VERSION • محیط کامل توسعه", fontSize = 11.sp) } }, navigationIcon = { IconButton({ drawer = true }) { Icon(Icons.Default.Menu, "منو") } }) }) { pad -> Box(Modifier.fillMaxSize().padding(pad)) { LocalBuildManagerScreen(agent) } }
                } else ChatView(mode, input, { input = it }, messages, loading, job, web, { drawer = true }, { tools = true }) {
                    val text = input.trim(); if (text.isBlank() || loading) return@ChatView
                    if (!ApiKeyStore.hasKey(context)) { provider = true; return@ChatView }
                    messages = messages + Msg(text, true); input = ""; loading = true
                    scope.launch {
                        try {
                            val ai = OpenAiClient(context)
                            if (mode == Mode.ANDROID) {
                                val plan = ai.generateBuildPlan(text)
                                job = BuildJobLauncher.enqueue(context, text, plan.toJson().toString(), 30, false)
                                messages = messages + Msg("🎉 پروژه «${plan.projectName}» آماده ساخت شد!\n\n${plan.summary}\n\n📦 ${plan.files.size} فایل • 🛠️ ${plan.buildTasks.size} Build • 🧪 ${plan.testTasks.size} Test\n\n🚀 Job واقعی در پس‌زمینه ثبت شد.", false)
                            } else {
                                val persona = if (mode == Mode.CODE) "You are an expert software engineer. Always answer in Persian when the user writes Persian." else "You are a helpful Persian-speaking AI assistant. Be practical and concise."
                                val answer = if (web) ai.askWithWebSearch("$persona\n\n$text") else ai.ask("$persona\n\n$text")
                                messages = messages + Msg(answer, false)
                            }
                        } catch (e: Exception) { messages = messages + Msg("❌ ${e.message ?: "یک خطای ناشناخته رخ داد."}", false) }
                        finally { loading = false }
                    }
                }
            }
            AnimatedVisibility(drawer, enter = fadeIn() + slideInHorizontally { it }, exit = fadeOut() + slideOutHorizontally { it }) { Drawer(mode, job, { drawer = false }, { mode = it; drawer = false }, { provider = true; drawer = false }, { messages = emptyList(); job = null; mode = Mode.CHAT; drawer = false }) }
        }
        if (tools) ModalBottomSheet({ tools = false }) {
            Text("ابزارهای استودیو", Modifier.padding(horizontal = 24.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Tool(Icons.Default.Create, "ساخت پروژه", "Planner + Build + Test + Repair") { mode = Mode.ANDROID; tools = false }
            Tool(Icons.Default.Code, "کدنویسی", "تولید و اصلاح کد") { mode = Mode.CODE; tools = false }
            Tool(Icons.Default.Build, "Build / Test", "Runtime واقعی و Diagnostics") { mode = Mode.BUILD; tools = false }
            Tool(Icons.Default.Language, "جست‌وجوی وب", "OpenAI web search") { web = !web; tools = false }
            Tool(Icons.Default.Key, "OpenAI API Key", "کلید و مدل") { provider = true; tools = false }
            Spacer(Modifier.height(20.dp))
        }
        if (provider) ProviderDialog(context) { provider = false }
    }
}

@Composable private fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(15.dp), color = StudioBlueLight) { Icon(icon, null, Modifier.padding(11.dp), tint = StudioBlue) }; Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun Drawer(mode: Mode, job: String?, close: () -> Unit, select: (Mode) -> Unit, provider: () -> Unit, fresh: () -> Unit) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))) { Column(Modifier.fillMaxHeight().width(330.dp).background(Color.White).padding(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(17.dp), color = StudioBlueLight) { Icon(Icons.Default.AutoAwesome, null, Modifier.padding(13.dp), tint = StudioBlue) }; Spacer(Modifier.width(10.dp)); Column { Text("AI Builder", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("نسخه $VERSION • Studio", fontSize = 12.sp, color = StudioBlue) } }; HorizontalDivider(); Text("فضاهای کاری", Modifier.padding(12.dp), fontSize = 12.sp); Mode.values().forEach { item -> NavigationDrawerItem(icon = { Icon(if (item == Mode.BUILD) Icons.Default.Build else Icons.Default.AutoAwesome, null) }, label = { Column { Text(item.title); Text(item.subtitle, fontSize = 11.sp) } }, selected = item == mode, onClick = { select(item) }) }; Spacer(Modifier.weight(1f)); if (job != null) Surface(shape = RoundedCornerShape(16.dp), color = StudioBlueLight, modifier = Modifier.fillMaxWidth().padding(5.dp)) { Text("🔨 ساخت فعال\n${job.take(10)}…", Modifier.padding(14.dp), fontSize = 12.sp) }; NavigationDrawerItem(icon = { Icon(Icons.Default.Key, null) }, label = { Text("OpenAI API Key") }, selected = false, onClick = provider); NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("گفت‌وگوی جدید") }, selected = false, onClick = fresh); TextButton(close) { Text("بستن") } } } }

@Composable private fun ChatView(mode: Mode, input: String, setInput: (String) -> Unit, messages: List<Msg>, loading: Boolean, job: String?, web: Boolean, menu: () -> Unit, tools: () -> Unit, send: () -> Unit) { val list = rememberLazyListState(); LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }; val scale by animateFloatAsState(if (input.isBlank() || loading) .92f else 1f, tween(220, easing = FastOutSlowInEasing), label = "send"); Scaffold(topBar = { TopAppBar(title = { Column { Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, fontSize = 11.sp) } }, navigationIcon = { IconButton(menu) { Icon(Icons.Default.Menu, "منو") } }, actions = { if (web) AssistChip({}, label = { Text("وب") }); IconButton(tools) { Icon(Icons.Default.Tune, "ابزارها") } }) }, bottomBar = { Surface(shadowElevation = 14.dp) { Column(Modifier.padding(10.dp)) { if (mode == Mode.ANDROID) Text("💡 توضیح طبیعی بده؛ AI پروژه را برای Build واقعی آماده می‌کند.", Modifier.padding(6.dp), fontSize = 11.sp); Row(verticalAlignment = Alignment.Bottom) { IconButton(tools) { Icon(Icons.Default.Add, "ابزارها") }; OutlinedTextField(input, setInput, Modifier.weight(1f), enabled = !loading, maxLines = 6, placeholder = { Text("ایده‌ات را بنویس… 🚀") }, shape = RoundedCornerShape(27.dp)); Spacer(Modifier.width(6.dp)); FilledIconButton(send, enabled = input.isNotBlank() && !loading, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { Icon(Icons.Default.Send, "ارسال") } } } } }) { pad -> Column(Modifier.fillMaxSize().padding(pad)) { if (job != null && mode == Mode.ANDROID) AssistChip({}, label = { Text("🏗️ ساخت پس‌زمینه • ${job.take(8)}…") }, Modifier.padding(12.dp)); if (messages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("چه چیزی می‌سازیم؟ ✨", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("نسخه $VERSION — آماده برای ایده‌های بزرگ", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); AssistChip({ setInput("یک اپ بساز که هنگام باز شدن بنویسد: سلام برنامه کار می‌کند 👋") }, label = { Text("🧪 تست سریع APK") }) } } else LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(messages) { MessageBubble(it) }; if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) } } } } }

@Composable private fun MessageBubble(msg: Msg) { Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start) { Surface(shape = RoundedCornerShape(20.dp), color = if (msg.mine) StudioBlueLight else Color(0xFFF5F7FA), modifier = Modifier.widthIn(max = 360.dp)) { Text(msg.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = StudioInk) } } }

@Composable private fun ProviderDialog(context: android.content.Context, dismiss: () -> Unit) { val old = remember { ApiKeyStore.getConfig(context) }; var key by remember { mutableStateOf("") }; var model by remember { mutableStateOf(old?.model ?: "gpt-5.6-luna") }; var error by remember { mutableStateOf<String?>(null) }; AlertDialog(onDismissRequest = { if (old != null) dismiss() }, title = { Text("OpenAI API") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("V16.5 فقط OpenAI را استفاده می‌کند. کلید روی دستگاه ذخیره می‌شود.", fontSize = 12.sp); OutlinedTextField(key, { key = it }, label = { Text(if (old == null) "API Key" else "API Key جدید (اختیاری)") }, singleLine = true, visualTransformation = PasswordVisualTransformation()); OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true); error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } } }, confirmButton = { Button({ runCatching { val finalKey = key.trim().ifBlank { old?.apiKey.orEmpty() }; require(finalKey.isNotBlank()) { "کلید API را وارد کن." }; ApiKeyStore.saveConfig(context, AiProviderConfig("OpenAI", finalKey, "https://api.openai.com/v1", model.trim().ifBlank { "gpt-5.6-luna" }, AiProviderConfig.Protocol.RESPONSES)); dismiss() }.onFailure { error = it.message ?: "ذخیره ناموفق بود." } }) { Text("ذخیره") } }, dismissButton = { if (old != null) TextButton(dismiss) { Text("لغو") } }) }
