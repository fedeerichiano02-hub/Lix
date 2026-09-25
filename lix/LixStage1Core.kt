package com.example.llama

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object LixStage1Core {
    private const val PREFS = "lix"
    private const val MEM = "memory_items"
    private const val LESSONS = "learning_items"
    private const val PROJECT = "active_project"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun handle(c: Context, prompt: String): Boolean {
        val q = prompt.lowercase(Locale("es", "AR")).trim()

        if (q.startsWith("recordá esto:") || q.startsWith("recuerda esto:") || q.startsWith("acordate que ")) {
            val value = if (q.contains(":")) prompt.substringAfter(":").trim() else prompt.substringAfter("que ", "").trim()
            if (value.isNotBlank()) {
                remember(c, value, "explícito")
                toast(c, "Guardado en la memoria permanente de Lix.")
            }
            return true
        }

        if (q.contains("borrá toda mi memoria") || q.contains("borra toda mi memoria")) {
            prefs(c).edit().remove(MEM).remove(LESSONS).apply()
            toast(c, "Memoria y aprendizajes locales eliminados.")
            return true
        }

        if (q.contains("qué recordás") || q.contains("que recordas") || q.contains("mostrame mi memoria")) {
            showMemory(c)
            return true
        }

        if (q.contains("diagnóstico de lix") || q.contains("diagnostico de lix") || q == "diagnóstico" || q == "diagnostico") {
            showDiagnostics(c)
            return true
        }

        if (q.contains("planificá la tarea") || q.contains("planifica la tarea") || q.startsWith("dividí esta tarea") || q.startsWith("divide esta tarea")) {
            showTaskPlan(c, prompt.substringAfter(":", prompt).trim())
            return true
        }

        if (q.contains("proyecto activo") || q.contains("qué proyecto tengo") || q.contains("que proyecto tengo")) {
            toast(c, prefs(c).getString(PROJECT, null) ?: "No hay proyecto activo guardado.")
            return true
        }

        if (q.startsWith("aprendé que ") || q.startsWith("aprende que ")) {
            val lesson = prompt.substringAfter("que ").trim()
            if (lesson.isNotBlank()) {
                learn(c, lesson)
                toast(c, "Aprendido y guardado.")
            }
            return true
        }

        if (q.contains("abrí los ajustes de lix") || q.contains("abrime los ajustes de lix")) {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:" + c.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        }
        return false
    }

    fun modeContext(c: Context): String {
        val memories = readItems(c, MEM).takeLast(12)
        val lessons = readItems(c, LESSONS).takeLast(12)
        val project = prefs(c).getString(PROJECT, null)
        return buildString {
            if (project != null) append("Proyecto activo: ").append(project).append("\n")
            if (memories.isNotEmpty()) {
                append("Memoria relevante:\n")
                memories.forEach { append("- ").append(it).append("\n") }
            }
            if (lessons.isNotEmpty()) {
                append("Aprendizajes explícitos:\n")
                lessons.forEach { append("- ").append(it).append("\n") }
            }
        }.trim()
    }

    fun remember(c: Context, text: String, source: String) {
        appendItem(c, MEM, JSONObject().apply {
            put("text", text.take(1000))
            put("source", source)
            put("time", System.currentTimeMillis())
        }.toString(), 200)
    }

    fun learn(c: Context, text: String) {
        appendItem(c, LESSONS, JSONObject().apply {
            put("text", text.take(1000))
            put("time", System.currentTimeMillis())
        }.toString(), 200)
    }

    fun setActiveProject(c: Context, project: String) {
        prefs(c).edit().putString(PROJECT, project.take(500)).apply()
    }

    private fun appendItem(c: Context, key: String, value: String, max: Int) {
        val old = try { JSONArray(prefs(c).getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        val out = JSONArray()
        val start = maxOf(0, old.length() - max + 1)
        for (i in start until old.length()) out.put(old.optString(i))
        out.put(value)
        prefs(c).edit().putString(key, out.toString()).apply()
    }

    private fun readItems(c: Context, key: String): List<String> {
        val a = try { JSONArray(prefs(c).getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        val out = mutableListOf<String>()
        for (i in 0 until a.length()) {
            val raw = a.optString(i)
            try { out += JSONObject(raw).optString("text", raw) } catch (_: Exception) { out += raw }
        }
        return out
    }

    private fun showMemory(c: Context) {
        val mem = readItems(c, MEM)
        val lessons = readItems(c, LESSONS)
        val body = StringBuilder("MEMORIA\n\n")
        if (mem.isEmpty()) body.append("No hay recuerdos explícitos.\n") else mem.takeLast(30).forEach { body.append("• ").append(it).append("\n") }
        body.append("\nAPRENDIZAJES\n\n")
        if (lessons.isEmpty()) body.append("No hay aprendizajes explícitos.") else lessons.takeLast(30).forEach { body.append("• ").append(it).append("\n") }
        AlertDialog.Builder(c).setTitle("🧠 Memoria de Lix").setMessage(body.toString()).setPositiveButton("Cerrar", null).show()
    }

    private fun showTaskPlan(c: Context, task: String) {
        val clean = task.ifBlank { "tarea sin descripción" }
        val steps = listOf(
            "Entender el objetivo: " + clean.take(180),
            "Separar requisitos y restricciones.",
            "Identificar herramientas y archivos necesarios.",
            "Ejecutar el primer bloque verificable.",
            "Comprobar el resultado y detectar errores.",
            "Corregir lo necesario.",
            "Entregar un resumen de lo realizado."
        )
        val body = steps.mapIndexed { i, s -> (i + 1).toString() + ". " + s }.joinToString("\n")
        AlertDialog.Builder(c).setTitle("🤖 Plan de tarea").setMessage(body)
            .setPositiveButton("Usar este plan") { _, _ -> toast(c, "Plan preparado; Lix puede continuar la tarea desde el chat.") }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun showDiagnostics(c: Context) {
        val dm = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        dm.getMemoryInfo(info)
        val stat = StatFs(c.filesDir.absolutePath)
        val free = stat.availableBytes / (1024L * 1024L)
        val total = stat.totalBytes / (1024L * 1024L)
        val version = c.packageManager.getPackageInfo(c.packageName, 0).versionName
        val body = "Lix " + version + "\n" +
            "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
            "ABI: " + Build.SUPPORTED_ABIS.joinToString() + "\n" +
            "RAM libre: " + (info.availMem / (1024L * 1024L)) + " MB\n" +
            "RAM total: " + (info.totalMem / (1024L * 1024L)) + " MB\n" +
            "Almacenamiento libre: " + free + " MB\n" +
            "Almacenamiento total: " + total + " MB\n" +
            "Modelo local: Qwen3 1.7B Q4_K_M"
        AlertDialog.Builder(c).setTitle("🧪 Diagnóstico de Lix").setMessage(body).setPositiveButton("Cerrar", null).show()
    }

    private fun toast(c: Context, message: String) = Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
}
