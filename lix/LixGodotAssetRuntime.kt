package com.example.llama

import android.app.Activity
import java.util.Locale

/** Turns user-created 3D assets into Godot-ready gameplay structures. */
object LixGodotAssetRuntime {
    fun buildPrompt(activity: Activity, request: String): String {
        val snapshot = LixProjectManager.snapshotForPrompt(activity)
        return """
Sos Lix, integrador técnico de assets para Godot 4.x.
El usuario crea los modelos 3D y vos los convertís en contenido jugable.
Pedido: $request

$snapshot

Si es un PERSONAJE 3D, analizá el asset existente y prepará, según corresponda:
- CharacterBody3D
- Mesh/escena del modelo
- CollisionShape3D
- AnimationPlayer/AnimationTree usando animaciones existentes
- movimiento caminar/correr/saltar/agacharse cuando corresponda
- cámara si fue solicitada
- interacción, vida y stamina si el proyecto las usa
- puntos de armas/equipamiento si existen
- scripts GDScript necesarios

Si es un ESCENARIO/OBJETO 3D, prepará según corresponda:
- escena .tscn
- MeshInstance3D/recursos existentes
- StaticBody3D y colisiones
- navegación cuando sea necesaria
- luces/cámara solo si fueron solicitadas
- interacción y scripts necesarios

NO reemplaces ni inventes el modelo artístico. Usá el asset proporcionado.
No borres archivos existentes salvo pedido explícito.
Respetá la estructura y sistemas ya presentes en el proyecto.

Emití cambios como:
FILE: ruta/relativa
```text
contenido completo
```
CHECK: pruebas/verificaciones que deberían ejecutarse.
""".trimIndent()
    }

    fun isAssetIntegrationRequest(request: String): Boolean {
        val q = request.lowercase(Locale.ROOT)
        val asset = listOf("modelo 3d", "modelo3d", "personaje 3d", "personaje3d", "asset", "mesh", "glb", "gltf", "fbx", "escenario 3d", "modelo")
        val action = listOf("integr", "usar", "convert", "agregar", "agreg", "poner", "prepar", "funcion", "movimiento", "anim")
        return asset.any(q::contains) && action.any(q::contains)
    }

    fun apply(activity: Activity, response: String): Int = LixGodotWorkspace.apply(activity, response)
}
