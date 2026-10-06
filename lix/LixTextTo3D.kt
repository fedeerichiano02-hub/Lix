package com.example.llama

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LixTextTo3D {
    private const val API = "https://lix-3d-proxy.workers.dev/text-to-3d"

    fun isRequest(prompt: String): Boolean {
        val q = prompt.lowercase()
        val has3d = q.contains("3d") || q.contains("tres dimensiones")
        val create = listOf("crea", "crear", "hacé", "hace", "hacer", "generá", "genera", "generar", "modelá", "modela", "modelo").any { q.contains(it) }
        return has3d && create
    }

    fun start(activity: Activity, prompt: String) {
        Toast.makeText(activity, "Lix está creando el modelo 3D…", Toast.LENGTH_LONG).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("mode", "preview")
                    put("prompt", prompt.take(800))
                    put("ai_model", "latest")
                    put("model_type", "standard")
                    put("should_remesh", true)
                    put("target_polycount", 50000)
                    put("pose_mode", if (prompt.lowercase().contains("personaje") || prompt.lowercase().contains("humano")) "a-pose" else "")
                    put("target_formats", org.json.JSONArray().put("glb"))
                }.toString()

                val created = requestJson("POST", API, body)
                val previewId = created.getString("result")
                var task = JSONObject()
                var finished = false
                for (i in 0 until 180) {
                    task = requestJson("GET", API + "/" + previewId, null)
                    val progress = task.optInt("progress", 0)
                    if (progress % 10 == 0) withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Lix 3D: " + progress + "%", Toast.LENGTH_SHORT).show()
                    }
                    when (task.optString("status")) {
                        "SUCCEEDED" -> { finished = true; break }
                        "FAILED", "CANCELED" -> throw IllegalStateException(task.optJSONObject("task_error")?.optString("message") ?: "La generación 3D falló")
                    }
                    Thread.sleep(5000)
                }
                if (!finished) throw IllegalStateException("La generación 3D tardó demasiado.")

                val refineBody = JSONObject().apply {
                    put("mode", "refine")
                    put("preview_task_id", previewId)
                    put("enable_pbr", true)
                    put("texture_resolution", "2k")
                    put("target_formats", org.json.JSONArray().put("glb"))
                    put("auto_size", true)
                }.toString()

                val refined = requestJson("POST", API, refineBody)
                val refineId = refined.getString("result")
                var finalTask = JSONObject()
                var finalFinished = false
                for (i in 0 until 240) {
                    finalTask = requestJson("GET", API + "/" + refineId, null)
                    val progress = finalTask.optInt("progress", 0)
                    if (progress % 10 == 0) withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Lix 3D textura: " + progress + "%", Toast.LENGTH_SHORT).show()
                    }
                    when (finalTask.optString("status")) {
                        "SUCCEEDED" -> { finalFinished = true; break }
                        "FAILED", "CANCELED" -> throw IllegalStateException(finalTask.optJSONObject("task_error")?.optString("message") ?: "La texturización falló")
                    }
                    Thread.sleep(5000)
                }
                if (!finalFinished) throw IllegalStateException("La texturización tardó demasiado.")

                val glbUrl = finalTask.getJSONObject("model_urls").getString("glb")
                val temp = File(activity.cacheDir, "lix_3d_" + System.currentTimeMillis() + ".glb")
                download(glbUrl, temp)
                val publicUri = exportToDownloads(activity, temp)
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(activity)
                        .setTitle("Modelo 3D listo")
                        .setMessage("Lix creó el modelo y guardó el GLB en Descargas/Lix.")
                        .setPositiveButton("Compartir") { _, _ ->
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "model/gltf-binary"
                                putExtra(android.content.Intent.EXTRA_STREAM, publicUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            activity.startActivity(android.content.Intent.createChooser(send, "Compartir modelo 3D"))
                        }
                        .setNegativeButton("Cerrar", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "No pude crear el modelo 3D: " + (e.message ?: "error"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestJson(method: String, url: String, body: String?): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            if (body != null) doOutput = true
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Lix 3D HTTP " + code + ": " + text)
        return JSONObject(text)
    }

    private fun download(url: String, out: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30000
        c.readTimeout = 180000
        c.inputStream.use { input -> FileOutputStream(out).use { output -> input.copyTo(output, 64 * 1024) } }
    }

    private fun exportToDownloads(activity: Activity, source: File): Uri {
        val name = "Lix_3D_" + System.currentTimeMillis() + ".glb"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "model/gltf-binary")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lix")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No pude crear el archivo")
        try {
            activity.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            activity.contentResolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            activity.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
