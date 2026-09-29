package com.example.llama

import android.content.Context

/** Lix Godot specialist: generates and applies real project-file operations. */
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
2. No hagas prototipos: implementá la funcionalidad solicitada de forma utilizable.
3. Antes de editar, inspeccioná dependencias, escenas, scripts y recursos relacionados.
4. Usá rutas relativas y nunca uses rutas absolutas ni '..'.
5. Para personajes 3D: referencia -> modelo/GLB -> rig -> animaciones -> importación Godot -> CharacterBody3D -> AnimationTree/AnimationPlayer -> cámara/colisión -> prueba.
6. Para escenarios 3D: recursos -> escena -> nodos -> materiales -> colisiones -> navegación -> iluminación -> prueba.
7. Si una capacidad externa requiere un proveedor/API no conectado, NO inventes el resultado: indicá WAITING_EXTERNAL.
8. Después de cambios importantes, verificá coherencia entre scripts, escenas y nombres de nodos.
9. Si detectás un error, corregilo y volvé a verificar.
10. Priorizá rendimiento móvil.
11. ESTA TAREA SE EJECUTA DE FORMA AUTÓNOMA. No respondas con instrucciones sobre lo que habría que hacer: generá directamente los archivos que deben existir en el proyecto.
12. La respuesta DEBE contener operaciones FILE reales. No marques COMPLETE si no generaste al menos una operación FILE válida.
13. Para cada archivo nuevo o modificado usá EXACTAMENTE este formato:
FILE: ruta/relativa.ext
```text
contenido COMPLETO DEL ARCHIVO
```
14. Podés usar cualquier etiqueta de lenguaje dentro del bloque, pero siempre debe existir una línea FILE y un bloque triple de código cerrado.
15. Al final usá exactamente una línea TASK_STATE: COMPLETE, WAITING_EXTERNAL, NEEDS_USER o FAILED.

OPERACIONES DE ARCHIVOS:
FILE: ruta/relativa.ext
```text
contenido COMPLETO DEL ARCHIVO
```

CHECK: comprobaciones realizadas y dependencias pendientes, si existen.
TASK_STATE: COMPLETE
""".trimIndent()
    }

    /** Accepts the strict format plus harmless whitespace/language-tag variations. */
    fun applyOperations(context: Context, text: String): Int {
        var count = 0
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val fileRegex = Regex(
            "(?is)\\bFILE\\s*:\\s*([^\\n`]+?)\\s*\\n\\s*```(?:[a-zA-Z0-9_+.-]+)?\\s*\\n(.*?)```"
        )
        for (m in fileRegex.findAll(normalized)) {
            val path = sanitizeRelativePath(m.groupValues[1]) ?: continue
            if (LixProjectManager.writeAuthorizedFile(context, path, m.groupValues[2])) count++
        }
        if (count == 0) {
            val looseRegex = Regex("(?is)FILE\\s*:\\s*([^\\n`]+?)\\s*\\n\\s*```\\s*(.*?)```")
            for (m in looseRegex.findAll(normalized)) {
                val path = sanitizeRelativePath(m.groupValues[1]) ?: continue
                if (LixProjectManager.writeAuthorizedFile(context, path, m.groupValues[2])) count++
            }
        }
        return count
    }

    private fun sanitizeRelativePath(raw: String): String? {
        val path = raw.trim().trim('`').replace('\\', '/')
        if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.startsWith("res://")) return null
        return path
    }
}
