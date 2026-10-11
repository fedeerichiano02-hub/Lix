package com.example.llama

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Configurable OpenAI-compatible router. Supports OpenRouter, Groq and compatible endpoints.
 * API keys are stored only in this app's private preferences.
 */
object LixModelRouter {
    private const val PREFS = "lix_model_router"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    data class Config(val endpoint: String, val model: String, val apiKey: String)

    fun getConfig(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            p.getString("endpoint", "https://openrouter.ai/api/v1")!!.trimEnd('/'),
            p.getString("model", "openrouter/free")!!,
            p.getString("api_key", "")!!
        )
    }

    fun saveConfig(context: Context, endpoint: String, model: String, apiKey: String) {
        require(endpoint.startsWith("https://")) { "El endpoint debe usar HTTPS." }
        require(model.isNotBlank()) { "Ingresá el identificador del modelo." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", endpoint.trim().trimEnd('/'))
            .putString("model", model.trim())
            .putString("api_key", apiKey.trim())
            .apply()
    }

    fun ask(context: Context, prompt: String, callback: (Result<String>) -> Unit) {
        val cfg = getConfig(context)
        if (prompt.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Escribí un mensaje.")))
            return
        }
        if (cfg.apiKey.isBlank()) {
            callback(Result.failure(IllegalStateException("Configurá primero el proveedor y su clave API.")))
            return
        }
        executor.execute {
            val result = runCatching {
                val url = URL(cfg.endpoint + "/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer " + cfg.apiKey)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("HTTP-Referer", "https://github.com/fedeerichiano02-hub/Lix")
                    setRequestProperty("X-Title", "Lix")
                }
                try {
                    val body = JSONObject()
                        .put("model", cfg.model)
                        .put("messages", JSONArray()
                            .put(JSONObject().put("role", "system").put("content",
                                "Sos Lix, un asistente personal en español rioplatense. Sé claro, honesto y útil. No afirmes haber ejecutado acciones que no ejecutaste."))
                            .put(JSONObject().put("role", "user").put("content", prompt)))
                        .put("temperature", 0.4)
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val raw = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (code !in 200..299) {
                        val detail = runCatching { JSONObject(raw).optString("error", raw) }.getOrDefault(raw)
                        throw IllegalStateException("Proveedor respondió HTTP $code: $detail")
                    }
                    val root = JSONObject(raw)
                    val answer = root.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content").trim()
                    if (answer.isBlank()) throw IllegalStateException("El proveedor devolvió una respuesta vacía.")
                    answer
                } finally {
                    conn.disconnect()
                }
            }
            main.post { callback(result) }
        }
    }
}
