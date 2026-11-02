package com.mariocostabile.fastreply

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Taps WhatsApp's send button only for a message FastReply just armed, then goes home. */
class SendService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val p = Pending.value ?: return
        val now = System.currentTimeMillis()
        if (now - p.at > ARM_WINDOW_MS) {
            Pending.value = null // timed out: user finishes manually in the open chat
            return
        }
        val root = rootInActiveWindow ?: return
        val entry = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry").firstOrNull()
        if (!shouldSend(p, entry?.text?.toString(), now)) return
        val send = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull()
            ?: findByDescription(root, setOf("Invia", "Send"))
            ?: return
        Pending.value = null // disarm before clicking: two close events must never send twice
        send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {}

    private fun findByDescription(node: AccessibilityNodeInfo, labels: Set<String>): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() in labels) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findByDescription(it, labels) }
            if (found != null) return found
        }
        return null
    }
}

fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, SendService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
