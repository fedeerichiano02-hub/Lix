package com.example.llama

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object LixEvolution {
    data class Improvement(
        val id: String,
        val title: String,
        val description: String,
        val version: Int,
        val size: String,
        val category: String
    )

    private const val PREF = "lix_evolution"
    private const val CATALOG_URL = "https://raw.githubusercontent.com/fedeerichiano02-hub/Lix/main/lix/evolution_catalog.json"

    fun catalog(context: Context): List<Improvement> {
        return try {
            val local = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val json = local.getString("catalog", null) ?: return defaultCatalog()
            parseCatalog(json)
        } catch (_: Exception) { defaultCatalog() }
    }

    fun refresh(context: Context): List<Improvement> {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 5000
            requestMethod = "GET"
        }
        return try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parseCatalog(text)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("catalog", text).apply()
            parsed
        } finally { connection.disconnect() }
    }

    fun installImprovement(context: Context, improvement: Improvement): Boolean {
        // Solo habilita módulos declarativos/verificables. No ejecuta código remoto.
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("module_" + improvement.id, improvement.version).apply()
        return true
    }

    fun installedVersion(context: Context, id: String): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("module_" + id, 0)

    private fun parseCatalog(text: String): List<Improvement> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Improvement(o.getString("id"), o.getString("title"), o.getString("description"),
                o.optInt("version", 1), o.optString("size", "—"), o.optString("category", "General"))
        }
    }

    private fun defaultCatalog() = listOf(
        Improvement("speed", "Optimización de velocidad", "Ajustes de rendimiento y caché.", 1, "Configuración", "Rendimiento"),
        Improvement("memory", "Memoria profunda", "Mejor recuperación y organización de recuerdos.", 1, "Configuración", "Inteligencia"),
        Improvement("voice", "Conversación natural", "Mejoras de voz y continuidad.", 1, "Configuración", "Voz"),
        Improvement("vision", "Visión avanzada", "Mejoras para análisis visual.", 1, "Configuración", "Visión"),
        Improvement("agent", "Agente Lix", "Mejoras de planificación y verificación.", 1, "Configuración", "Agente"),
        Improvement("web", "Navegador inteligente", "Mejoras de búsqueda y verificación web.", 1, "Configuración", "Internet"),
        Improvement("godot", "Game Studio", "Mejoras para proyectos Godot.", 1, "Configuración", "Desarrollo")
    )
}
