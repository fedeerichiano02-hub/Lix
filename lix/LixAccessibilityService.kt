package com.example.llama

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

/** Visible bridge: background tasks keep running while Godot stays visible. */
class LixAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: LixAccessibilityService? = null
        const val GODOT_PACKAGE = "org.godotengine.editor"
    }
    private var watcher: Job? = null
    private var openedForTask: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        watcher = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val task = LixBackgroundStore.latest(this@LixAccessibilityService, 40)
                    .lastOrNull { it.status == "running" && LixGodotAgent.shouldHandle(it.prompt) }
                if (task != null && task.id != openedForTask) {
                    openedForTask = task.id
                    openGodot()
                }
                delay(1500)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { watcher?.cancel(); instance = null; super.onDestroy() }

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

    fun clickText(text: String): Boolean = clickNode(rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull())

    fun setFocusedText(text: String): Boolean {
        val node = findEditable(rootInActiveWindow) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
        return null
    }
    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isEnabled) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return node.parent?.let { clickNode(it) } ?: false
    }
}
