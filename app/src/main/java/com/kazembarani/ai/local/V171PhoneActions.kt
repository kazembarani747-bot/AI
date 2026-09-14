package com.kazembarani.ai.local

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Phone actions use Android intents and keep the final user-visible action under Android control. */
object V171PhoneActions {
    fun openUrl(context: Context, url: String): Boolean {
        val value = url.trim()
        if (!value.startsWith("https://") && !value.startsWith("http://")) return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun composeMessage(context: Context, address: String, body: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(address.trim())}"))
            .putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
