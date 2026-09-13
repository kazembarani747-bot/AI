package com.kazembarani.ai.local

data class DetectedProvider(
    val company: String,
    val baseUrl: String,
    val model: String,
    val protocol: AiProviderConfig.Protocol,
    val confidence: String
)

/** Best-effort key fingerprinting. Unknown providers remain editable instead of being guessed as certain. */
object ProviderDetector {
    fun detect(rawKey: String): DetectedProvider {
        val key = rawKey.trim()
        return when {
            key.startsWith("sk-or-v1-", true) -> DetectedProvider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", AiProviderConfig.Protocol.CHAT_COMPLETIONS, "زیاد")
            key.startsWith("sk-ant-", true) -> DetectedProvider("Anthropic", "https://api.anthropic.com/v1", "claude-3-5-haiku-latest", AiProviderConfig.Protocol.ANTHROPIC, "زیاد")
            key.startsWith("AIza") -> DetectedProvider("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", AiProviderConfig.Protocol.GEMINI, "زیاد")
            key.startsWith("gsk_", true) -> DetectedProvider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", AiProviderConfig.Protocol.CHAT_COMPLETIONS, "زیاد")
            key.startsWith("sk-proj-", true) -> DetectedProvider("OpenAI", "https://api.openai.com/v1", "gpt-5.6-luna", AiProviderConfig.Protocol.RESPONSES, "زیاد")
            key.startsWith("sk-", true) -> DetectedProvider("OpenAI / سازگار با OpenAI", "https://api.openai.com/v1", "gpt-5.6-luna", AiProviderConfig.Protocol.RESPONSES, "متوسط")
            else -> DetectedProvider("ارائه‌دهنده ناشناخته", "https://api.openai.com/v1", "gpt-5.6-luna", AiProviderConfig.Protocol.RESPONSES, "نامشخص")
        }
    }
}
