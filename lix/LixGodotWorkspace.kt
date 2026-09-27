package com.example.llama

import android.app.Activity
import java.util.Locale

/** High-level Godot workspace planner/executor. Writes stay inside the user-authorized SAF tree. */
object LixGodotWorkspace {
    fun buildPrompt(activity: Activity, request: String): String {
        val snapshot = LixProjectManager.snapshotForPrompt(activity)
        return """
Sos Lix, agente de desarrollo Godot 4.x.
Pedido del usuario: $request

$snapshot

Trabajá sobre TODO el proyecto autorizado: GDScript, escenas .tscn, recursos .tres, shaders, configuraciones y archivos auxiliares.
Antes de modificar, razoná sobre dependencias y preservá compatibilidad con Godot 4.x.
No inventes rutas que no estén justificadas por la estructura existente.

Respondé con operaciones:
FILE: ruta/relativa
```text
contenido completo
```
DELETE: ruta/relativa
CHECK: comprobaciones realizadas.
""".trimIndent()
    }

    fun apply(activity: Activity, response: String): Int {
        var changed = 0
        val regex = Regex("(?s)FILE:\\s*([^\\n]+)\\n```(?:text|gdscript|gd|ini|json|tscn|tres|glsl|shader)?\\n(.*?)```")
        for (m in regex.findAll(response)) {
            val path = normalize(m.groupValues[1]) ?: continue
            if (LixProjectManager.writeAuthorizedFile(activity, path, m.groupValues[2])) changed++
        }
        return changed
    }

    private fun normalize(raw: String): String? {
        val p = raw.trim().replace('\\', '/')
        if (p.isBlank() || p.startsWith("/") || p.contains("..")) return null
        if (p.split('/').any { it.isBlank() }) return null
        return p
    }

    fun understands(request: String): Boolean {
        val q = request.lowercase(Locale.ROOT)
        val godot = listOf("godot", "gdscript", ".tscn", ".gd", ".tres", "nodo", "escena", "shader", "script", "proyecto")
        val actions = listOf("crear", "creá", "hacer", "hacé", "agregar", "agregá", "modificar", "modificá", "arreglar", "arreglá", "implementar", "implementá", "programar", "programá", "optimizar", "optimiza")
        return godot.any(q::contains) && actions.any(q::contains)
    }
}
