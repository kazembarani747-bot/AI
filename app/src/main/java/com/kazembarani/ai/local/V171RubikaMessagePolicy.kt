package com.kazembarani.ai.local

import java.util.regex.Pattern

object V171RubikaMessagePolicy {
    private val urlPattern = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE)

    data class Parsed(
        val text: String,
        val links: List<String>,
        val senderId: String?,
        val chatId: String?,
        val messageId: String?,
        val isOwner: Boolean
    )

    fun parse(text: String, senderId: String?, chatId: String?, messageId: String?, ownerId: String): Parsed {
        val links = urlPattern.matcher(text).let { matcher ->
            buildList {
                while (matcher.find()) add(matcher.group())
            }
        }
        return Parsed(text, links, senderId, chatId, messageId, !senderId.isNullOrBlank() && senderId == ownerId.trim())
    }

    fun applyLinkPolicy(parsed: Parsed, removeLinks: Boolean): String {
        if (!removeLinks) return parsed.text
        return urlPattern.matcher(parsed.text).replaceAll("").replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    fun canPerformOwnerOnlyAction(parsed: Parsed): Boolean = parsed.isOwner

    fun unauthorizedReply(): String = "😅 این دستور فقط برای سازندهٔ ربات مجازه."
}
