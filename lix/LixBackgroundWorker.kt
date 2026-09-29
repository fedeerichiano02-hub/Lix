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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import java.io.FileOutputStream

/** Persistent long-running worker, independent from MainActivity. */
class LixBackgroundWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_ID = "task_id"
        const val KEY_PROMPT = "task_prompt"
        private const val CHANNEL = "lix_background"
        private const val NOTIFICATION_ID = 7010
        private const val CHECKPOINT_EVERY_CHUNKS = 20
        private const val HEARTBEAT_MS = 5000L
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel()
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Lix está trabajando en segundo plano")
            .setContentText("Lix mantiene un seguimiento de la actividad de la tarea.")
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val prompt = inputData.getString(KEY_PROMPT) ?: return Result.failure()
        val started = System.currentTimeMillis()
        LixBackgroundStore.upsert(applicationContext, LixBackgroundStore.Task(id, prompt, "queued", progress = 0, phase = "En cola", lastActivity = started, startedAt = started))
        var heartbeat: Job? = null
        return try {
            setForeground(getForegroundInfo())
            checkpoint(id, prompt, "running", 5, "Iniciando trabajador Godot")
            heartbeat = kotlinx.coroutines.CoroutineScope(currentCoroutineContext()).launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    checkpoint(id, prompt, "running", currentProgress(id), currentPhase(id))
                }
            }

            if (!LixProjectManager.hasAuthorizedProject(applicationContext)) {
                val message = "No hay un proyecto Godot autorizado. Elegí la carpeta del proyecto antes de ejecutar tareas de desarrollo."
                checkpoint(id, prompt, "needs_user", 0, "Esperando autorización del proyecto", message, finished = true)
                notifyDone(message)
                return Result.failure()
            }

            checkpoint(id, prompt, "running", 10, "Inspeccionando proyecto Godot")
            val workspace = LixProjectManager.snapshotForPrompt(applicationContext)
            if (workspace.startsWith("No hay un proyecto Godot autorizado")) {
                val message = "Lix no pudo leer el proyecto Godot autorizado."
                checkpoint(id, prompt, "failed", 0, "No se pudo leer el proyecto", message, finished = true)
                notifyDone(message)
                return Result.failure()
            }

            val model = File(applicationContext.filesDir, "models/lix-qwen3-1.7b-q4_k_m.gguf")
            if (!model.exists()) {
                checkpoint(id, prompt, "running", 15, "Preparando modelo local")
                model.parentFile?.mkdirs()
                applicationContext.assets.open("models/lix-qwen3-1.7b-q4_k_m.gguf").use { src ->
                    FileOutputStream(model).use { dst -> src.copyTo(dst) }
                }
            }

            checkpoint(id, prompt, "running", 20, "Cargando modelo local")
            val engine = AiChat.getInferenceEngine(applicationContext)
            engine.loadModel(model.absolutePath)
            engine.setSystemPrompt("Sos Lix, asistente de desarrollo. Trabajás de forma autónoma sobre la tarea solicitada. Respondé en español argentino. No inventes archivos ni resultados. Si la tarea involucra Godot, inspeccioná primero el proyecto y respetá los sistemas existentes. En tareas de proyecto, generá operaciones FILE reales para modificar el proyecto autorizado y verificá el resultado. Guardá avances y explicá claramente qué hiciste y qué quedó pendiente.")

            checkpoint(id, prompt, "running", 30, "Trabajando sobre el proyecto Godot")
            // Critical: send the actual Godot-agent workflow, including the authorized
            // workspace snapshot and FILE operation contract. Previously this snapshot
            // was computed but never included in the model prompt, so the model could
            // answer conversationally while the worker still reported completion.
            val agentPrompt = LixGodotAgent.workspacePrompt(applicationContext, prompt)
            val result = StringBuilder()
            var chunks = 0
            engine.sendUserPrompt(agentPrompt).collect { piece ->
                result.append(piece)
                chunks++
                if (chunks % CHECKPOINT_EVERY_CHUNKS == 0) {
                    val p = (30 + (chunks / CHECKPOINT_EVERY_CHUNKS).coerceAtMost(60)).coerceAtMost(90)
                    checkpoint(id, prompt, "running", p, "Generando e implementando cambios")
                    LixBackgroundStore.upsert(applicationContext, LixBackgroundStore.Task(id, prompt, "running", result.toString(), p, "Generando e implementando cambios", System.currentTimeMillis(), started, 0L))
                }
            }

            val answer = result.toString()
                .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
                .trim()

            checkpoint(id, prompt, "running", 92, "Escribiendo cambios en Godot")
            val applied = LixGodotAgent.applyOperations(applicationContext, answer)
            checkpoint(id, prompt, "running", 96, "Verificando archivos del proyecto")
            val verification = LixProjectManager.snapshotForPrompt(applicationContext)

            val explicitState = when {
                Regex("TASK_STATE:\\s*(?:COMPLETE|COMPLETED)", RegexOption.IGNORE_CASE).containsMatchIn(answer) -> "completed"
                Regex("TASK_STATE:\\s*WAITING_EXTERNAL", RegexOption.IGNORE_CASE).containsMatchIn(answer) -> "waiting_external"
                Regex("TASK_STATE:\\s*NEEDS_USER", RegexOption.IGNORE_CASE).containsMatchIn(answer) -> "needs_user"
                Regex("TASK_STATE:\\s*FAILED", RegexOption.IGNORE_CASE).containsMatchIn(answer) -> "failed"
                else -> ""
            }

            // A background Godot task cannot be considered completed merely because
            // the model produced text. At least one real FILE operation must have
            // been applied, unless the task explicitly requires no file changes.
            val taskState = when {
                explicitState.isNotBlank() && explicitState != "completed" -> explicitState
                applied > 0 && !verification.startsWith("No hay un proyecto Godot autorizado") -> "completed"
                else -> "failed"
            }

            val finalMessage = buildString {
                append(answer)
                append("\n\nCambios aplicados realmente al proyecto: ").append(applied)
                append("\nProyecto verificado después de la tarea: ").append(!verification.startsWith("No hay un proyecto Godot autorizado"))
                if (taskState == "failed" && applied == 0) {
                    append("\n\nIMPORTANTE: Lix no aplicó ningún cambio de archivo. La tarea NO se considera completada.")
                }
            }.trim()
            checkpoint(id, prompt, taskState, if (taskState == "completed") 100 else 96, finalPhase(taskState), finalMessage, finished = true)
            notifyDone(finalMessage)
            if (taskState == "completed") Result.success() else Result.failure()
        } catch (t: Throwable) {
            val message = t.message ?: "error desconocido"
            checkpoint(id, prompt, "failed", 0, "Error", message, finished = true)
            notifyDone("La tarea no pudo terminar: $message")
            Result.failure()
        } finally {
            heartbeat?.cancel()
        }
    }

    private fun checkpoint(
        id: String,
        prompt: String,
        status: String,
        progress: Int,
        phase: String,
        result: String = "",
        finished: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val previous = LixBackgroundStore.latest(applicationContext, 40).lastOrNull { it.id == id }
        LixBackgroundStore.upsert(
            applicationContext,
            LixBackgroundStore.Task(
                id = id,
                prompt = prompt,
                status = status,
                result = if (result.isNotBlank()) result else previous?.result.orEmpty(),
                progress = progress,
                phase = phase,
                lastActivity = now,
                startedAt = previous?.startedAt?.takeIf { it > 0 } ?: now,
                finishedAt = if (finished) now else 0L
            )
        )
    }

    private fun currentProgress(id: String): Int = LixBackgroundStore.latest(applicationContext, 40).lastOrNull { it.id == id }?.progress ?: 0
    private fun currentPhase(id: String): String = LixBackgroundStore.latest(applicationContext, 40).lastOrNull { it.id == id }?.phase ?: "Trabajando"
    private fun finalPhase(status: String): String = when (status) {
        "completed" -> "Completada y verificada"
        "waiting_external" -> "Esperando servicio externo"
        "needs_user" -> "Necesita intervención del usuario"
        else -> "Finalizada con error"
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
