package com.example.llama

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Lightweight public web lookup through Wikipedia's search API; no API key required. */
object LixWebSearch {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun search(query: String, callback: (Result<String>) -> Unit) {
        if (query.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Escribí qué querés buscar.")))
            return
        }
        executor.execute {
            val result = runCatching {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val url = URL("https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&utf8=1&format=json&srlimit=5")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "LixAndroidAssistant/1.0")
                }
                try {
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException("Búsqueda web: HTTP $code")
                    val results = JSONObject(raw).getJSONObject("query").getJSONArray("search")
                    if (results.length() == 0) return@runCatching "No encontré resultados para: ${query.trim()}"
                    buildString {
                        append("Resultados de Wikipedia para: ").append(query.trim()).append("\n\n")
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            append(i + 1).append(". ").append(item.optString("title")).append("\n")
                            val snippet = item.optString("snippet").replace(Regex("<[^>]*>"), "")
                            append(snippet).append("\n")
                            append("https://es.wikipedia.org/wiki/")
                            append(URLEncoder.encode(item.optString("title").replace(' ', '_'), "UTF-8"))
                            append("\n\n")
                        }
                    }
                } finally { conn.disconnect() }
            }
            main.post { callback(result) }
        }
    }
}
