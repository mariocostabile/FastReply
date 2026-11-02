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
        val header = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name").firstOrNull()
        if (!shouldSend(p, header?.text?.toString(), entry?.text?.toString(), now)) return
        val send = (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull()
            ?: findByDescription(root, setOf("Invia", "Send")))
            ?.let { clickableSelfOrParent(it) }
            ?: return // button not ready yet: wait for the next event within the window
        Pending.value = null // disarm before clicking: two close events must never send twice
        // Go home only if the tap really happened, otherwise stay in the chat so the user sees the unsent text.
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun clickableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.isEnabled && n.isVisibleToUser) return n
            n = n.parent
        }
        return null
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
