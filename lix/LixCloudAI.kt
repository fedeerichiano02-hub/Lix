package com.example.llama

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

/**
 * Optional cloud reasoning for Lix. API key is entered by the user and stored only in app-private
 * SharedPreferences; it is never committed to the repository. Requests run off the UI thread.
 */
object LixCloudAI {
    private const val PREFS = "lix_cloud_ai"
    private const val KEY = "api_key"
    private const val MODEL = "model"
    private const val DEFAULT_MODEL = "gpt-4.1-mini"
    private val executor = Executors.newSingleThreadExecutor()

    fun isConfigured(context: Context): Boolean =
        prefs(context).getString(KEY, "").orEmpty().startsWith("sk-")

    fun configure(activity: Activity) {
        val p = prefs(activity)
        val key = EditText(activity).apply {
            hint = "sk-… (tu clave API)"
            setSingleLine(true)
            setText(p.getString(KEY, "").orEmpty())
        }
        val model = EditText(activity).apply {
            hint = "Modelo (por defecto gpt-4.1-mini)"
            setSingleLine(true)
            setText(p.getString(MODEL, DEFAULT_MODEL).orEmpty())
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
            addView(TextView(activity).apply {
                text = "La clave se guarda solo en el almacenamiento privado de Lix. No la compartas ni la publiques."
                textSize = 13f
            })
            addView(key, LinearLayout.LayoutParams(-1, -2))
            addView(model, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(activity)
            .setTitle("Conectar cerebro en la nube")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val value = key.text.toString().trim()
                if (value.isBlank()) {
                    p.edit().remove(KEY).apply()
                    Toast.makeText(activity, "Clave eliminada; Lix usará el modo local.", Toast.LENGTH_LONG).show()
                } else if (!value.startsWith("sk-")) {
                    Toast.makeText(activity, "La clave no parece una API key válida.", Toast.LENGTH_LONG).show()
                } else {
                    p.edit().putString(KEY, value)
                        .putString(MODEL, model.text.toString().trim().ifBlank { DEFAULT_MODEL }).apply()
                    Toast.makeText(activity, "Cerebro en la nube configurado.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Desconectar") { _, _ ->
                p.edit().remove(KEY).apply()
                Toast.makeText(activity, "Cerebro en la nube desconectado.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun ask(activity: Activity, prompt: String, callback: (String) -> Unit) {
        val apiKey = prefs(activity).getString(KEY, "").orEmpty()
        if (apiKey.isBlank()) {
            callback("Primero conectá una API key en «Conectar cerebro en la nube». Podés crearla en https://platform.openai.com/api-keys. El uso de API se factura por separado.")
            return
        }
        val model = prefs(activity).getString(MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        executor.execute {
            val answer = try {
                val connection = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "Sos Lix, un asistente personal en español argentino. Sé útil, honesto y claro. No afirmes que ejecutaste acciones, modificaste archivos ni generaste recursos si no hay verificación real. Si una tarea requiere herramientas del teléfono o archivos, explicá qué herramienta hace falta."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.4).toString()
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val detail = try { JSONObject(raw).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                    throw IllegalStateException("API HTTP $code: ${detail ?: raw.take(300)}")
                }
                val json = JSONObject(raw)
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                    .ifBlank { "La API respondió sin texto." }
            } catch (e: Exception) {
                "No pude conectar con el cerebro en la nube: ${e.message ?: "error de conexión"}. Revisá la clave, el saldo/crédito de API y la conexión."
            }
            activity.runOnUiThread { if (!activity.isFinishing) callback(answer) }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
