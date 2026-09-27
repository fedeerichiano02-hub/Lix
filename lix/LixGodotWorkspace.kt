package com.example.llama

import android.app.Activity
import java.util.Locale

/** Complete Godot workspace orchestration for the focused Lix build. */
object LixGodotWorkspace {
    fun buildPrompt(activity: Activity, request: String): String {
        val snapshot = LixProjectManager.snapshotForPrompt(activity)
        return """
Sos Lix, agente de producción de juegos especializado en Godot 4.x.
Pedido del usuario: $request

$snapshot

OBJETIVO: dejar el trabajo funcional, persistente y listo para probar. No generes un prototipo si el usuario pidió una implementación.

PIPELINES DISPONIBLES:
- Código Godot: GDScript, escenas .tscn, recursos .tres, shaders, configuración y scripts auxiliares.
- Personaje 3D: imagen/referencia -> recurso GLB -> rig -> animaciones -> importación -> CharacterBody3D -> colisión -> cámara -> AnimationTree/AnimationPlayer -> control del jugador.
- Escenario 3D: recursos -> escena -> materiales -> colisiones -> navegación -> iluminación -> objetos interactuables.
- Gameplay: input -> movimiento -> cámara -> físicas -> interacción -> armas -> inventario -> salud/stamina -> IA -> animaciones.
- Verificación: ejecutar proyecto, revisar errores, corregir dependencias y repetir hasta que el cambio quede coherente.

REGLAS:
1. Trabajá únicamente dentro del proyecto autorizado.
2. Conservá funcionalidades existentes que funcionen.
3. No inventes APIs, modelos, archivos o resultados externos.
4. Si una operación necesita un servicio externo no conectado, marcala como WAITING_EXTERNAL y no simules haberla realizado.
5. Usá rutas relativas seguras; nunca rutas absolutas ni '..'.
6. Priorizá Android y el renderer Mobile.
7. Evitá tareas permanentes de alto consumo cuando no sean necesarias.
8. Cada tarea debe poder reanudarse desde un checkpoint persistente.

FORMATO DE OPERACIONES:
FILE: ruta/relativa
```text
contenido completo
```
DELETE: ruta/relativa
CHECK: comprobaciones realizadas.
TASK_STATE: COMPLETE | WAITING_EXTERNAL | NEEDS_USER | FAILED
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
        val godot = listOf("godot", "gdscript", ".tscn", ".gd", ".tres", "nodo", "escena", "shader", "script", "proyecto", "glb", "3d", "animación", "animacion", "rig")
        val actions = listOf("crear", "creá", "hacer", "hacé", "agregar", "agregá", "modificar", "modificá", "arreglar", "arreglá", "implementar", "implementá", "programar", "programá", "optimizar", "optimiza", "animar", "anima", "integrar", "integra")
        return godot.any(q::contains) && actions.any(q::contains)
    }
}
