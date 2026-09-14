package com.kazembarani.ai

import com.kazembarani.ai.local.V171RubikaUpdateParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V171RubikaUpdateParserTest {
    @Test
    fun parsesDocumentedNewMessageShape() {
        val update = JSONObject()
            .put("type", "NewMessage")
            .put("chat_id", "g0abc")
            .put("new_message", JSONObject()
                .put("message_id", "m123")
                .put("sender_id", "u456")
                .put("sender_type", "User")
                .put("text", "سلام"))

        val parsed = V171RubikaUpdateParser.parse(update)

        assertEquals("سلام", parsed?.text)
        assertEquals("g0abc", parsed?.chatId)
        assertEquals("m123", parsed?.messageId)
        assertEquals("u456", parsed?.senderId)
        assertFalse(parsed!!.senderIsBot)
    }

    @Test
    fun rejectsBotMessages() {
        val update = JSONObject()
            .put("chat_id", "u123")
            .put("new_message", JSONObject()
                .put("message_id", "m1")
                .put("sender_type", "Bot")
                .put("text", "bot reply"))

        val parsed = V171RubikaUpdateParser.parse(update)

        assertTrue(parsed!!.senderIsBot)
    }

    @Test
    fun rejectsUpdatesWithoutTextOrChat() {
        assertNull(V171RubikaUpdateParser.parse(JSONObject().put("chat_id", "c1")))
        assertNull(V171RubikaUpdateParser.parse(JSONObject().put("new_message", JSONObject().put("text", "hello"))))
    }
}
