package com.example.llama

import android.content.Context

/**
 * Lix Godot specialist. It orchestrates the complete game-production workflow:
 * Godot project editing, 3D asset preparation, rig/animation integration,
 * testing, repair and persistent task checkpoints.
 */
object LixGodotAgent {
    private val godotTerms = listOf(
        "godot", "gdscript", ".tscn", ".gd", ".tres", "escena", "nodo",
        "personaje", "enemigo", "npc", "inventario", "arma", "nivel", "mapa",
        "juego", "animación", "animacion", "rig", "3d", "glb", "modelo", "escenario",
        "suelo", "terreno"
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
5. Para personajes 3D: referencia -> modelo/GLB -> rig -> animaciones -> importación Godot -> CharacterBody3D -> AnimationTree/AnimationPlayer -> cámara/colisión -> prueba.
6. Para escenarios 3D: recursos -> escena -> nodos -> materiales -> colisiones -> navegación -> iluminación -> prueba.
7. Si una capacidad externa requiere un proveedor/API no conectado, NO inventes el resultado. Indicá WAITING_EXTERNAL.
8. Después de cambios importantes, verificá coherencia entre scripts, escenas y nombres de nodos.
9. Si detectás un error, corregilo y volvé a verificar.
10. Guardá el trabajo en checkpoints persistentes cuando el sistema de tareas lo permita.
11. Priorizá rendimiento móvil.
12. El resultado debe quedar listo para que el usuario pueda abrir Godot y probarlo.
13. ESTA TAREA SE EJECUTA DE FORMA AUTÓNOMA. No respondas con instrucciones sobre lo que habría que hacer: generá directamente los archivos que deben existir en el proyecto.
14. Tu respuesta debe contener primero y de forma obligatoria las operaciones FILE. Después podés poner CHECK y TASK_STATE.
15. Para cada archivo nuevo o modificado usá EXACTAMENTE este formato, sin variar la palabra FILE:
FILE: ruta/relativa.ext
```text
contenido COMPLETO DEL ARCHIVO
```
16. No uses bloques FILE vacíos. Si no podés realizar una parte por una dependencia externa, no inventes el archivo: usá WAITING_EXTERNAL.
17. Para eliminar un archivo usá EXACTAMENTE: DELETE: ruta/relativa.ext
18. Al final usá EXACTAMENTE una línea TASK_STATE: COMPLETE, WAITING_EXTERNAL, NEEDS_USER o FAILED.

OPERACIONES DE ARCHIVOS:
FILE: ruta/relativa.ext
```text
contenido COMPLETO DEL ARCHIVO
```

CHECK: comprobaciones realizadas y dependencias pendientes, si existen.
TASK_STATE: COMPLETE
""".trimIndent()
    }

    /**
     * Applies the model's FILE/DELETE operations. The parser deliberately accepts
     * common harmless formatting variations because the local 1.7B model can add
     * language tags or whitespace around fences.
     */
    fun applyOperations(context: Context, text: String): Int {
        var count = 0
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")

        val fileRegex = Regex(
            "(?is)\\bFILE\\s*:\\s*([^\\n`]+?)\\s*\\n\\s*```(?:[a-zA-Z0-9_+-]+)?\\s*\\n(.*?)```"
        )
        for (m in fileRegex.findAll(normalized)) {
            val path = sanitizeRelativePath(m.groupValues[1]) ?: continue
            val content = m.groupValues[2]
            if (LixProjectManager.writeAuthorizedFile(context, path, content)) count++
        }

        // Fallback for models that omit the language tag but keep the fence.
        if (count == 0) {
            val looseRegex = Regex(
                "(?is)FILE\\s*:\\s*([^\\n`]+?)\\s*\\n\\s*```\\s*(.*?)```"
            )
            for (m in looseRegex.findAll(normalized)) {
                val path = sanitizeRelativePath(m.groupValues[1]) ?: continue
                if (LixProjectManager.writeAuthorizedFile(context, path, m.groupValues[2])) count++
            }
        }

        val deleteRegex = Regex("(?im)^\\s*DELETE\\s*:\\s*([^\\n]+?)\\s*$")
        for (m in deleteRegex.findAll(normalized)) {
            val path = sanitizeRelativePath(m.groupValues[1]) ?: continue
            if (LixProjectManager.deleteAuthorizedFile(context, path)) count++
        }
        return count
    }

    private fun sanitizeRelativePath(raw: String): String? {
        val path = raw.trim().trim('`').replace('\\', '/')
        if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.startsWith("res://")) return null
        return path
    }
}
