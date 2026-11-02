package com.mariocostabile.fastreply

import java.net.URLEncoder

/** A message FastReply just handed to WhatsApp, waiting for SendService to tap send. */
data class PendingSend(val phone: String, val name: String, val text: String, val at: Long)

/** Shared between MainActivity and SendService (same process). */
object Pending {
    @Volatile var value: PendingSend? = null
}

const val ARM_WINDOW_MS = 5000L

fun buildWaUri(phone: String, text: String): String =
    "https://wa.me/$phone?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/**
 * Tap send only for our own message, in the right contact's chat, within the window.
 * Unknown chat header (null) means no send: fail safe to manual mode.
 */
fun shouldSend(p: PendingSend?, chatName: String?, entryText: String?, now: Long): Boolean =
    p != null && now - p.at <= ARM_WINDOW_MS && chatName == p.name && entryText == p.text
