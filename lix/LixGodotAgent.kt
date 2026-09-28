package com.example.llama

import android.content.Context

/**
 * Lix Godot specialist. It orchestrates the complete game-production workflow:
 * Godot project editing, 3D asset preparation, rig/animation integration,
 * testing, repair and persistent task checkpoints.
 * External 3D providers are treated as pluggable capabilities; Lix never
 * pretends a provider is available when no connector/credential exists.
 */
object LixGodotAgent {
    private val godotTerms = listOf(
        "godot", "gdscript", ".tscn", ".gd", ".tres", "escena", "nodo",
        "personaje", "enemigo", "npc", "inventario", "arma", "nivel", "mapa",
        "juego", "animación", "animacion", "rig", "3d", "glb", "modelo", "escenario"
    )

    private val actionTerms = listOf(
        "hacé", "hace", "crea", "crear", "agrega", "agregá", "modifica", "modificá",
        "implementa", "implementá", "programa", "programá", "integra", "integrá",
        "arregla", "arreglá", "desarrolla", "desarrollá", "prepara", "prepará",
        "importa", "importá", "anima", "animá", "riggea", "riggeá", "optimiza", "optimizá"
    )

    fun shouldHandle(prompt: String): Boolean {
        val q = prompt.lowercase()
        return godotTerms.any { q.contains(it) } && actionTerms.any { q.contains(it) }
    }

    fun workspacePrompt(context: Context, userPrompt: String, verification: Boolean = false): String {
        val snapshot = LixProjectManager.snapshotForPrompt(context)
        val mode = if (verification) "REVISIÓN, PRUEBA Y CORRECCIÓN" else "IMPLEMENTACIÓN COMPLETA"
        return """
SOS LIX, AGENTE ESPECIALIZADO EN GODOT 4.x PARA DESARROLLO DE JUEGOS.
MODO: $mode

PEDIDO DEL USUARIO:
$userPrompt

ESTADO DEL PROYECTO AUTORIZADO:
$snapshot

REGLAS DE TRABAJO:
1. Trabajá directamente sobre el proyecto autorizado y conservá lo que ya funciona.
2. No hagas prototipos ni implementaciones de demostración: implementá la funcionalidad solicitada de forma utilizable.
3. Antes de editar, inspeccioná dependencias, escenas, scripts y recursos relacionados.
4. Usá rutas relativas y nunca uses rutas absolutas ni '..'.
5. Para personajes 3D, tratá el flujo como: referencia -> modelo/GLB -> rig -> animaciones -> importación Godot -> CharacterBody3D -> AnimationTree/AnimationPlayer -> cámara/colisión -> prueba.
6. Para escenarios 3D, tratá el flujo como: recursos -> escena -> nodos -> materiales -> colisiones -> navegación -> iluminación -> prueba.
7. Si una capacidad externa (generación 3D, rigging o motion generation) requiere un proveedor/API que no está conectado, NO inventes el resultado. Prepará la integración y dejá claro qué credencial o conector falta.
8. Después de cambios importantes, verificá coherencia entre scripts, escenas y nombres de nodos.
9. Si detectás un error, corregilo y volvé a verificar; no te detengas en el primer fallo.
10. Guardá el trabajo en checkpoints persistentes cuando el sistema de tareas lo permita.
11. Priorizá rendimiento móvil: renderer Mobile, cargas diferidas, recursos comprimidos, geometría razonable y evitar procesos permanentes innecesarios.
12. El resultado debe quedar listo para que el usuario pueda abrir Godot y probarlo.
13. Si recibís una tarea en segundo plano, NO te limites a explicar qué habría que hacer: generá las operaciones FILE necesarias para modificar realmente el proyecto autorizado.
14. Al final, informá qué archivos cambiaste, qué comprobaste y qué quedó pendiente. Nunca afirmes que modificaste Godot si no generaste operaciones FILE válidas.

OPERACIONES DE ARCHIVOS:
FILE: ruta/relativa.ext
```text
contenido COMPLETO del archivo
```

Para eliminar:
DELETE: ruta/relativa.ext

Al final:
CHECK: comprobaciones y dependencias pendientes, si existen.
TASK_STATE: COMPLETE | WAITING_EXTERNAL | NEEDS_USER | FAILED
""".trimIndent()
    }

    fun applyOperations(context: Context, text: String): Int {
        var count = 0
        val regex = Regex("(?s)FILE:\\s*([^\\n]+)\\n```(?:text|gdscript|gd|kotlin|ini|json|tscn|tres|glsl|shader)?\\n(.*?)```")
        for (m in regex.findAll(text)) {
            val path = m.groupValues[1].trim().replace('\\', '/')
            if (path.isBlank() || path.startsWith("/") || path.contains("..")) continue
            if (LixProjectManager.writeAuthorizedFile(context, path, m.groupValues[2])) count++
        }
        return count
    }
}
