package com.example.llama

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arm.aichat.AiChat
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileOutputStream

/** Runs user-requested long tasks outside the Activity lifecycle. */
class LixBackgroundWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_ID = "task_id"
        const val KEY_PROMPT = "task_prompt"
        private const val CHANNEL = "lix_background"
        private const val NOTIFICATION_ID = 7010
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel()
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Lix está trabajando")
            .setContentText("La tarea continúa aunque cierres la aplicación.")
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val prompt = inputData.getString(KEY_PROMPT) ?: return Result.failure()
        LixBackgroundStore.upsert(applicationContext, LixBackgroundStore.Task(id, prompt, "running"))
        return try {
            setForeground(getForegroundInfo())
            val model = File(applicationContext.filesDir, "models/lix-qwen3-1.7b-q4_k_m.gguf")
            if (!model.exists()) {
                model.parentFile?.mkdirs()
                applicationContext.assets.open("models/lix-qwen3-1.7b-q4_k_m.gguf").use { src ->
                    FileOutputStream(model).use { dst -> src.copyTo(dst) }
                }
            }
            val engine = AiChat.getInferenceEngine(applicationContext)
            engine.loadModel(model.absolutePath)
            engine.setSystemPrompt("Sos Lix, asistente personal. Respondé en español argentino, claro, directo y útil. No inventes datos. Priorizá completar la tarea solicitada.")
            val result = StringBuilder()
            engine.sendUserPrompt(prompt).collect { result.append(it) }
            val answer = result.toString().replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()
            LixBackgroundStore.upsert(applicationContext, LixBackgroundStore.Task(id, prompt, "completed", answer))
            notifyDone(answer)
            Result.success()
        } catch (t: Throwable) {
            LixBackgroundStore.upsert(applicationContext, LixBackgroundStore.Task(id, prompt, "failed", t.message ?: "Error desconocido"))
            notifyDone("La tarea no pudo terminar: ${t.message ?: "error desconocido"}")
            Result.failure()
        }
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Tareas de Lix", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notifyDone(text: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Lix terminó una tarea")
            .setContentText(text.take(180))
            .setAutoCancel(true)
            .build())
    }
}
