package com.example.llama

import android.app.Activity

object LixGodotAgent {
    fun shouldHandle(prompt: String): Boolean {
        val q = prompt.lowercase()
        val godot = listOf("godot", "gdscript", ".tscn", ".gd", "escena", "nodo", "personaje", "enemigo", "inventario", "arma", "nivel", "mapa", "juego")
        val intent = listOf("hacé", "hace", "crea", "crear", "agrega", "agregá", "modifica", "modificá", "implementa", "implementá", "programa", "programá", "integra", "integrá", "arregla", "arreglá", "desarrolla", "desarrollá")
        return godot.any { q.contains(it) } && intent.any { q.contains(it) }
    }

    fun workspacePrompt(activity: Activity, userPrompt: String, verification: Boolean = false): String {
        val snapshot = LixProjectManager.snapshotForPrompt(activity)
        val mode = if (verification) "REVISIÓN Y CORRECCIÓN" else "IMPLEMENTACIÓN"
        return """
SOS EL AGENTE DE DESARROLLO GODOT DE LIX. MODO: $mode.
Objetivo del usuario: $userPrompt

$snapshot

Trabajá directamente sobre el proyecto autorizado. Priorizá cambios pequeños, coherentes y compatibles con Godot 4.x.

DEVOLVÉ SOLO operaciones de archivos con este formato:
FILE: ruta/relativa.ext
```text
contenido COMPLETO del archivo
```

Podés incluir varios FILE. Para eliminar: DELETE: ruta/relativa.ext
Después agregá CHECK con una explicación breve. No uses rutas absolutas ni ..
""".trimIndent()
    }

    fun applyOperations(activity: Activity, text: String): Int {
        var count = 0
        val regex = Regex("(?s)FILE:\\s*([^\\n]+)\\n```(?:text|gdscript|kotlin|ini|json)?\\n(.*?)```")
        for (m in regex.findAll(text)) {
            val path = m.groupValues[1].trim().replace('\\', '/')
            if (path.isBlank() || path.startsWith("/") || path.contains("..")) continue
            if (LixProjectManager.writeAuthorizedFile(activity, path, m.groupValues[2])) count++
        }
        return count
    }
}
