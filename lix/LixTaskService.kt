package com.example.llama

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale

class LixTaskService : Service() {
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = Notification.Builder(this, "lix_tasks")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Lix está trabajando")
            .setContentText("Hay una tarea de Lix en ejecución.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(7002, notification)
        }
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("es","AR") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("task_message") ?: "La tarea terminó."
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "lix_task")
        val done = getSystemService(NotificationManager::class.java)
        done.notify(7003, Notification.Builder(this, "lix_tasks")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Lix terminó una tarea")
            .setContentText(message)
            .setAutoCancel(true)
            .build())
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("lix_tasks", "Tareas de Lix", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
