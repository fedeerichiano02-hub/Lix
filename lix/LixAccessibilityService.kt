package com.example.llama

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Visible Godot control bridge. The background worker can keep running while
 * this service brings Godot to the foreground and performs observable UI
 * actions when the user has explicitly enabled the accessibility permission.
 */
class LixAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: LixAccessibilityService? = null
        const val GODOT_PACKAGE = "org.godotengine.editor"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun openGodot(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(GODOT_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        return true
    }

    fun clickText(text: String): Boolean = clickNode(findNodeByText(rootInActiveWindow, text))

    fun clickDescription(description: String): Boolean = clickNode(findNodeByDescription(rootInActiveWindow, description))

    fun setFocusedText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Opens Godot and leaves it visible. The worker continues independently. */
    fun startVisibleGodotWork(onProgress: ((String) -> Unit)? = null) {
        if (!openGodot()) {
            onProgress?.invoke("No se encontró el editor Godot instalado.")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            delay(1200)
            onProgress?.invoke("Godot abierto; preparando trabajo visible")
            // Do not blindly click arbitrary coordinates. Actions are semantic
            // and only run when the expected Godot UI element is present.
            clickText("Import") || clickText("Importar")
            delay(700)
            onProgress?.invoke("Godot activo; esperando controles del proyecto")
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        node.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), text)?.let { return it }
        }
        return null
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.equals(description, true) == true) return node
        for (i in 0 until node.childCount) {
            findNodeByDescription(node.getChild(i), description)?.let { return it }
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isEnabled) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return node.parent?.let { clickNode(it) } ?: false
    }
}
