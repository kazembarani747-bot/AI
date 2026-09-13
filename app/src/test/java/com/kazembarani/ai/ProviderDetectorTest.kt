package com.kazembarani.ai

import com.kazembarani.ai.local.AiProviderConfig
import com.kazembarani.ai.local.ProviderDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetectorTest {
    @Test fun detectsOpenAiProjectKey() {
        val result = ProviderDetector.detect("sk-proj-example")
        assertEquals("OpenAI", result.company)
        assertEquals(AiProviderConfig.Protocol.RESPONSES, result.protocol)
        assertTrue(result.confidence.isNotBlank())
    }

    @Test fun detectsOpenRouterKey() {
        val result = ProviderDetector.detect("sk-or-v1-example")
        assertEquals("OpenRouter", result.company)
        assertEquals(AiProviderConfig.Protocol.CHAT_COMPLETIONS, result.protocol)
    }

    @Test fun keepsUnknownKeysConfigurable() {
        val result = ProviderDetector.detect("custom-provider-key")
        assertTrue(result.company.contains("ناشناخته"))
    }
}
