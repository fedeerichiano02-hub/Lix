package com.example.llama

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class LixAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: LixAccessibilityService? = null
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
}
