package com.kazembarani.ai.local

import android.content.Context

object ProviderAutoSetup {
    fun applyDetected(context: Context, key: String): DetectedProvider {
        val detected = ProviderDetector.detect(key)
        ApiKeyStore.saveConfig(context, AiProviderConfig(detected.company, key.trim(), detected.baseUrl, detected.model, detected.protocol))
        return detected
    }
}
