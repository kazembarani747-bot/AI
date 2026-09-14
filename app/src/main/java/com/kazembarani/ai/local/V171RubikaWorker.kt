package com.kazembarani.ai.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.json.JSONObject

/** One bounded Rubika polling pass. Scheduling is controlled by V171RubikaScheduler. */
class V171RubikaWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val token = V171RubikaStore.token(applicationContext)?.trim().orEmpty()
        val ownerId = V171RubikaStore.ownerId(applicationContext).trim()
        if (token.isBlank() || ownerId.isBlank()) return Result.success()

        return runCatching {
            val client = V171RubikaClient(token)
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val offset = prefs.getString(OFFSET, null)
            val updates = client.getUpdates(limit = 20, offsetId = offset)
            val newOffset = updates.optString("next_offset_id").takeIf { it.isNotBlank() }
            if (newOffset != null) prefs.edit().putString(OFFSET, newOffset).apply()

            val list = updates.optJSONArray("updates") ?: return@runCatching Result.success()
            val autoReply = V171RubikaStore.autoReply(applicationContext)
            val removeLinks = V171RubikaStore.removeLinks(applicationContext)
            val funny = V171RubikaStore.funnyMode(applicationContext)
            val ai = if (autoReply) OpenAiClient(applicationContext) else null

            for (i in 0 until list.length()) {
                val update = list.optJSONObject(i) ?: continue
                processUpdate(client, ai, update, ownerId, autoReply, removeLinks, funny)
                delay(80)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun processUpdate(
        client: V171RubikaClient,
        ai: OpenAiClient?,
        update: JSONObject,
        ownerId: String,
        autoReply: Boolean,
        removeLinks: Boolean,
        funny: Boolean
    ) {
        val parsedUpdate = V171RubikaUpdateParser.parse(update) ?: return
        if (parsedUpdate.senderIsBot) return

        val parsed = V171RubikaMessagePolicy.parse(
            parsedUpdate.text,
            parsedUpdate.senderId,
            parsedUpdate.chatId,
            parsedUpdate.messageId,
            ownerId
        )
        if (parsed.isOwner && parsedUpdate.text.startsWith("/")) {
            handleOwnerCommand(client, parsedUpdate.chatId!!, parsedUpdate.messageId, parsedUpdate.text)
            return
        }

        // Editing is intentionally limited to owner messages. Removing another user's message
        // requires the bot to have the required chat permissions; never pretend otherwise.
        if (removeLinks && parsed.links.isNotEmpty() && parsed.isOwner) {
            val cleaned = V171RubikaMessagePolicy.applyLinkPolicy(parsed, true)
            if (cleaned != parsedUpdate.text && parsedUpdate.messageId != null) {
                runCatching { client.editMessageText(parsedUpdate.chatId!!, parsedUpdate.messageId, cleaned) }
            }
        }

        if (!autoReply || ai == null) return
        val prompt = if (funny) "با لحن دوستانه و کمی شوخ‌طبع، بدون توهین، به این پیام پاسخ بده:\n${parsedUpdate.text}" else parsedUpdate.text
        val answer = runCatching { ai.ask(prompt) }.getOrElse { return }
        if (answer.isNotBlank()) client.sendMessage(parsedUpdate.chatId!!, answer.take(3500), parsedUpdate.messageId)
    }

    private suspend fun handleOwnerCommand(client: V171RubikaClient, chatId: String, replyTo: String?, text: String) {
        when (text.lowercase().trim()) {
            "/status" -> client.sendMessage(chatId, "✅ ربات فعال است و Polling V17.1 در حال اجراست.", replyTo)
            "/links on" -> { V171RubikaStore.setRemoveLinks(applicationContext, true); client.sendMessage(chatId, "🔗 حذف لینک فعال شد.", replyTo) }
            "/links off" -> { V171RubikaStore.setRemoveLinks(applicationContext, false); client.sendMessage(chatId, "🔗 حذف لینک خاموش شد.", replyTo) }
            "/auto on" -> { V171RubikaStore.setAutoReply(applicationContext, true); client.sendMessage(chatId, "🤖 پاسخ خودکار فعال شد.", replyTo) }
            "/auto off" -> { V171RubikaStore.setAutoReply(applicationContext, false); client.sendMessage(chatId, "🤖 پاسخ خودکار خاموش شد.", replyTo) }
            "/funny on" -> { V171RubikaStore.setFunnyMode(applicationContext, true); client.sendMessage(chatId, "😂 حالت شوخ‌طبعی فعال شد.", replyTo) }
            "/funny off" -> { V171RubikaStore.setFunnyMode(applicationContext, false); client.sendMessage(chatId, "🙂 حالت شوخ‌طبعی خاموش شد.", replyTo) }
            "/help" -> client.sendMessage(chatId, "دستورات مالک: /status /links on|off /auto on|off /funny on|off", replyTo)
        }
    }

    companion object {
        const val PREFS = "v171_rubika_runtime"
        const val OFFSET = "offset_id"
    }
}
