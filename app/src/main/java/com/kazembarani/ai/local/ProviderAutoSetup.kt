package com.kazembarani.ai.local

import android.content.Context

/** V16.1 intentionally supports OpenAI only. */
object ProviderAutoSetup {
    private const val OPENAI_URL = "https://api.openai.com/v1"
    private const val OPENAI_MODEL = "gpt-5.6-luna"

    fun applyOpenAi(context: Context, key: String): AiProviderConfig {
        val clean = key.trim()
        require(clean.isNotBlank()) { "کلید OpenAI را وارد کن." }
        require(clean.startsWith("sk-")) { "این کلید شبیه یک OpenAI API key نیست." }
        val config = AiProviderConfig(
            company = "OpenAI",
            apiKey = clean,
            baseUrl = OPENAI_URL,
            model = OPENAI_MODEL,
            protocol = AiProviderConfig.Protocol.RESPONSES
        )
        ApiKeyStore.saveConfig(context, config)
        return config
    }
}
