package com.example.llama

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

class LixWakeService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(7001, notification)
        }
        startRecognizer()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("lix_voice", "Lix voz", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Activación por voz de Lix"
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "lix_voice")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Lix está escuchando")
                .setContentText("Decí “Lix, activáte” para iniciar.")
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Lix está escuchando")
                .setContentText("Decí “Lix, activáte”.")
                .setOngoing(true)
                .build()
        }
    }

    private fun startRecognizer() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "AR"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        recognizer?.startListening(intent)
    }

    private fun checkPhrase(text: String) {
        val q = text.lowercase(Locale("es","AR"))
            .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
        if (q.contains("lix activ") || q.contains("lix activa")) {
            val open = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("wake_lix", true)
            }
            startActivity(open)
            stopListening()
        }
    }

    private fun stopListening() {
        listening = false
        recognizer?.stopListening()
    }

    override fun onResults(results: android.os.Bundle) {
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { checkPhrase(it) }
        if (listening) startRecognizer()
    }

    override fun onPartialResults(partialResults: android.os.Bundle) {
        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { checkPhrase(it) }
    }

    override fun onError(error: Int) { if (listening) startRecognizer() }
    override fun onReadyForSpeech(params: android.os.Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        super.onDestroy()
    }
}
