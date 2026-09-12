package com.kazembarani.ai.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class AiProviderConfig(
    val company: String = "OpenAI",
    val apiKey: String,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-5.6-luna",
    val protocol: Protocol = Protocol.RESPONSES
) {
    enum class Protocol { RESPONSES, CHAT_COMPLETIONS }
}

/** Secure encrypted storage for the active AI provider profile. */
object ApiKeyStore {
    private const val PREFS = "ai_secure_settings"
    private const val VALUE = "active_provider"
    private const val KEY_ALIAS = "ai_builder_provider_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun hasKey(context: Context): Boolean = getConfig(context)?.apiKey?.isNotBlank() == true

    fun getConfig(context: Context): AiProviderConfig? = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12)
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8))
        AiProviderConfig(
            company = json.optString("company", "OpenAI"),
            apiKey = json.optString("apiKey"),
            baseUrl = json.optString("baseUrl", "https://api.openai.com/v1").trim().trimEnd('/'),
            model = json.optString("model", "gpt-5.6-luna"),
            protocol = runCatching { AiProviderConfig.Protocol.valueOf(json.optString("protocol", "RESPONSES")) }.getOrDefault(AiProviderConfig.Protocol.RESPONSES)
        ).takeIf { it.apiKey.isNotBlank() }
    }.getOrNull()

    fun save(context: Context, value: String) {
        saveConfig(context, AiProviderConfig(apiKey = value.trim()))
    }

    fun saveConfig(context: Context, config: AiProviderConfig) {
        require(config.apiKey.isNotBlank()) { "کلید API خالی است." }
        require(config.baseUrl.startsWith("https://")) { "آدرس API باید با https:// شروع شود." }
        val json = JSONObject().apply {
            put("company", config.company.trim().ifBlank { "Custom" })
            put("apiKey", config.apiKey.trim())
            put("baseUrl", config.baseUrl.trim().trimEnd('/'))
            put("model", config.model.trim().ifBlank { "default" })
            put("protocol", config.protocol.name)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(VALUE, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(VALUE).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
