package com.example.llama

import android.content.Intent
import android.service.voice.VoiceInteractionService

class LixVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            try { setInvocationEffectEnabled(true) } catch (_: Exception) {}
        }
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("wake_lix", true)
        })
    }
}
