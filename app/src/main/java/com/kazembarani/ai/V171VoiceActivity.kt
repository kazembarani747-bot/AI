package com.kazembarani.ai

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class V171VoiceActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("fa", "IR") }
        setContent { VoiceScreen(onListen = ::listen, onSpeak = ::speak) }
    }

    private fun listen(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) { onResult(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()) }
                override fun onError(error: Int) = Unit
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            r.startListening(intent)
        }
    }

    private fun speak(text: String) { tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "ai-v171") }

    override fun onDestroy() {
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}

@Composable
private fun VoiceScreen(onListen: ((String) -> Unit) -> Unit, onSpeak: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🎙️ Voice • V17.1", style = MaterialTheme.typography.headlineSmall)
        Text("تشخیص گفتار و تبدیل پاسخ به صدا با APIهای رسمی Android انجام می‌شود.")
        Text(text.ifBlank { "برای شروع روی شنیدن بزن." })
        Button(onClick = { onListen { text = it } }, modifier = Modifier.fillMaxWidth()) { Text("🎤 شنیدن") }
        OutlinedButton(onClick = { if (text.isNotBlank()) onSpeak(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("🔊 پخش پاسخ") }
    }
}
