import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
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
    private const val RIG_API = "https://lix-3d-proxy.workers.dev/rigging"
    private const val ANIM_API = "https://lix-3d-proxy.workers.dev/animations"

    fun isRequest(prompt: String): Boolean {
        val q = prompt.lowercase()
        val has3d = q.contains("3d") || q.contains("tres dimensiones")
        val create = listOf("crea", "creá", "crear", "creá", "hacé", "hace", "hacer", "generá", "genera", "generar", "modelá", "modela", "modelo").any { q.contains(it) }
        return has3d && create
    }

    fun start(activity: Activity, prompt: String) {
        Toast.makeText(activity, "Lix está creando el personaje 3D completo…", Toast.LENGTH_LONG).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isHumanoid = prompt.lowercase().let { it.contains("personaje") || it.contains("humano") || it.contains("hombre") || it.contains("mujer") }
                val previewBody = JSONObject().apply {
                    put("mode", "preview")
                    put("prompt", prompt.take(800))
                    put("ai_model", "latest")
                    put("model_type", "standard")
                    put("should_remesh", true)
                    put("target_polycount", 50000)
                    if (isHumanoid) put("pose_mode", "a-pose")
                    put("target_formats", org.json.JSONArray().put("glb"))
                }.toString()

                val previewId = requestJson("POST", API, previewBody).getString("result")
                waitForTask(activity, API + "/" + previewId, "Generando geometría", 180)

                val refineBody = JSONObject().apply {
                    put("mode", "refine")
                    put("preview_task_id", previewId)
                    put("enable_pbr", true)
                    put("texture_resolution", "2k")
                    put("target_formats", org.json.JSONArray().put("glb"))
                    put("auto_size", true)
                }.toString()

                val refineId = requestJson("POST", API, refineBody).getString("result")
                val finalTask = waitForTask(activity, API + "/" + refineId, "Texturizando", 240)
                val glbUrl = finalTask.getJSONObject("model_urls").getString("glb")

                if (!isHumanoid) {
                    saveOne(activity, glbUrl, "Lix_3D")
                    return@launch
                }

                // Rig humanoide: agrega esqueleto y entrega personaje listo para animar.
                val rigBody = JSONObject().apply {
                    put("input_task_id", refineId)
                    put("height_meters", 1.7)
                }.toString()
                val rigId = requestJson("POST", RIG_API, rigBody).getString("result")
                val rigTask = waitForTask(activity, RIG_API + "/" + rigId, "Creando esqueleto", 180)
                val rigResult = rigTask.getJSONObject("result")
                val riggedUrl = rigResult.getString("rigged_character_glb_url")

                // Un solo GLB con múltiples clips: idle, caminar, correr, atacar, disparar,
                // recibir daño, morir, rodar/esquivar, recargar de pie y recargar corriendo.
                val actions = org.json.JSONArray()
                    .put(0)   // Idle
                    .put(30)  // Casual_Walk
                    .put(16)  // RunFast
                    .put(4)   // Attack
                    .put(98)  // Run_and_Shoot
                    .put(177) // Gunshot_Reaction
                    .put(184) // Shot_and_Fall_Forward
                    .put(158) // Roll_Dodge
                    .put(170) // Standing_Reload
                    .put(166) // Running_Reload

                val animBody = JSONObject().apply {
                    put("rig_task_id", rigId)
                    put("action_ids", actions)
                }.toString()
                val animId = requestJson("POST", ANIM_API, animBody).getString("result")
                val animTask = waitForTask(activity, ANIM_API + "/" + animId, "Creando animaciones", 240)
                val animUrl = animTask.getJSONObject("result").getString("animation_glb_url")

                val rigFile = File(activity.cacheDir, "lix_character_" + System.currentTimeMillis() + ".glb")
                val animFile = File(activity.cacheDir, "lix_animations_" + System.currentTimeMillis() + ".glb")
                download(riggedUrl, rigFile)
                download(animUrl, animFile)

                val rigUri = exportToDownloads(activity, rigFile, "Lix_Character_Rigged")
                val animUri = exportToDownloads(activity, animFile, "Lix_Character_Animations")
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(activity)
                        .setTitle("Personaje 3D completo listo")
                        .setMessage("Lix creó el personaje, texturas, esqueleto y 10 animaciones para Godot. Guardado en Descargas/Lix.")
                        .setPositiveButton("Compartir personaje") { _, _ -> share(activity, rigUri) }
                        .setNeutralButton("Compartir animaciones") { _, _ -> share(activity, animUri) }
                        .setNegativeButton("Cerrar", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "No pude completar el personaje 3D: " + (e.message ?: "error"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun waitForTask(activity: Activity, url: String, label: String, maxPolls: Int): JSONObject {
        var lastProgress = -1
        for (i in 0 until maxPolls) {
            val task = requestJson("GET", url, null)
            val progress = task.optInt("progress", 0)
            if (progress != lastProgress && (progress == 0 || progress == 100 || progress % 10 == 0)) {
                lastProgress = progress
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Lix 3D: $label $progress%", Toast.LENGTH_SHORT).show()
                }
            }
            when (task.optString("status")) {
                "SUCCEEDED" -> return task
                "FAILED", "CANCELED" -> throw IllegalStateException(task.optJSONObject("task_error")?.optString("message") ?: "$label falló")
            }
            Thread.sleep(5000)
        }
        throw IllegalStateException("$label tardó demasiado.")
    }

    private suspend fun saveOne(activity: Activity, url: String, prefix: String) {
        val temp = File(activity.cacheDir, "lix_3d_" + System.currentTimeMillis() + ".glb")
        download(url, temp)
        exportToDownloads(activity, temp, prefix)
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, "Modelo 3D listo en Descargas/Lix.", Toast.LENGTH_LONG).show()
        }
    }

    private fun share(activity: Activity, uri: Uri) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "model/gltf-binary"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(android.content.Intent.createChooser(send, "Compartir modelo 3D"))
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

    private fun exportToDownloads(activity: Activity, source: File, prefix: String): Uri {
        val name = prefix + "_" + System.currentTimeMillis() + ".glb"
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
