package com.example.llama

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.EditText
import android.widget.Toast
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LixImageTo3D {
    private const val PREFS = "lix"
    private const val KEY = "meshy_api_key"
    private const val API = "https://api.meshy.ai/openapi/v1/image-to-3d"

    fun isRequest(prompt: String): Boolean {
        val q = prompt.lowercase()
        val objectWords = listOf("personaje", "modelo", "modelo 3d", "personaje 3d", "asset", "figura")
        val threeD = listOf("3d", "tres dimensiones", "en 3d")
        val actions = listOf("crea", "crear", "hacé", "hace", "hacer", "generá", "genera", "convertí", "convierte", "convertir")
        return objectWords.any { q.contains(it) } && threeD.any { q.contains(it) } && actions.any { q.contains(it) }
    }

    fun start(activity: Activity, imageUri: Uri, prompt: String) {
        val key = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().trim()
        if (key.isBlank()) askForKey(activity) { startWithKey(activity, imageUri, prompt, it) }
        else startWithKey(activity, imageUri, prompt, key)
    }

    private fun askForKey(activity: Activity, after: (String) -> Unit) {
        val input = EditText(activity).apply {
            hint = "msy_..."
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 8, 40, 0)
            addView(input, android.widget.LinearLayout.LayoutParams(-1, 56))
        }
        AlertDialog.Builder(activity)
            .setTitle("Lix · Generación 3D")
            .setMessage("Para convertir una imagen en un modelo 3D, Lix necesita una clave de API de Meshy. La clave se guarda localmente en este teléfono.")
            .setView(box)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar y generar") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isBlank()) Toast.makeText(activity, "Falta la clave de API.", Toast.LENGTH_LONG).show()
                else {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key).apply()
                    after(key)
                }
            }.show()
    }

    private fun startWithKey(activity: Activity, imageUri: Uri, prompt: String, key: String) {
        Toast.makeText(activity, "Lix está generando el modelo 3D…", Toast.LENGTH_LONG).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageData = readAsDataUri(activity, imageUri)
                val body = JSONObject().apply {
                    put("image_url", imageData)
                    put("ai_model", "latest")
                    put("should_texture", true)
                    put("image_enhancement", false)
                    put("target_formats", org.json.JSONArray().put("glb"))
                    if (prompt.isNotBlank()) put("texture_prompt", prompt.take(800))
                }.toString()
                val created = requestJson("POST", API, key, body)
                val taskId = created.getString("result")
                var task = JSONObject()
                var finished = false
                for (i in 0 until 240) {
                    task = requestJson("GET", "$API/$taskId", key, null)
                    val progress = task.optInt("progress", 0)
                    if (progress > 0 && progress % 10 == 0) withContext(Dispatchers.Main) { Toast.makeText(activity, "Modelo 3D: $progress%", Toast.LENGTH_SHORT).show() }
                    when (task.optString("status")) {
                        "SUCCEEDED" -> { finished = true; break }
                        "FAILED", "CANCELED" -> throw IllegalStateException(task.optJSONObject("task_error")?.optString("message") ?: "La generación 3D falló")
                    }
                    Thread.sleep(5000)
                }
                if (!finished) throw IllegalStateException("La generación 3D tardó demasiado.")
                val glbUrl = task.getJSONObject("model_urls").getString("glb")
                val temp = File(activity.cacheDir, "lix_character_${System.currentTimeMillis()}.glb")
                download(glbUrl, temp)
                val publicUri = exportToDownloads(activity, temp)
                withContext(Dispatchers.Main) { showResult(activity, publicUri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(activity, "No pude crear el modelo 3D: ${e.message ?: "error"}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun readAsDataUri(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer la imagen")
        val mime = context.contentResolver.getType(uri)?.takeIf { it == "image/png" || it == "image/jpeg" } ?: "image/jpeg"
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun requestJson(method: String, url: String, key: String, body: String?): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            if (body != null) doOutput = true
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Meshy HTTP $code: $text")
        return JSONObject(text)
    }

    private fun download(url: String, out: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30000
        c.readTimeout = 120000
        c.inputStream.use { input -> FileOutputStream(out).use { output -> input.copyTo(output, 64 * 1024) } }
    }

    private fun exportToDownloads(activity: Activity, source: File): Uri {
        val name = "Lix_Character_${System.currentTimeMillis()}.glb"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "model/gltf-binary")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lix")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No pude crear el archivo en Descargas")
        try {
            activity.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            activity.contentResolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            activity.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun showResult(activity: Activity, uri: Uri) {
        AlertDialog.Builder(activity)
            .setTitle("Modelo 3D listo")
            .setMessage("Lix creó el personaje y guardó el GLB en Descargas/Lix.\n\nTodavía NO se integró en Godot: esta prueba solo genera el modelo.")
            .setNegativeButton("Cerrar", null)
            .setPositiveButton("Compartir") { _, _ ->
                activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "model/gltf-binary"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Compartir modelo 3D"))
            }.show()
    }
}
