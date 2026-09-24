package com.example.llama

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class LixVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                startVoiceActivity(Intent(this@LixVoiceSessionService, MainActivity::class.java).apply {
                    putExtra("wake_lix", true)
                })
                finish()
            }
        }
    }
}