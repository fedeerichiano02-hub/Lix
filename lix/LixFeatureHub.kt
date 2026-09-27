package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Lix Godot: interfaz mínima para desarrollo de juegos. */
object LixFeatureHub {
    private val features = listOf(
        "🎮 Trabajar en Godot" to "Crear y modificar scripts, escenas y nodos",
        "👤 Crear personaje 3D" to "Imagen → modelo 3D GLB",
        "🎬 Preparar animaciones" to "Rig y animaciones para usar en Godot",
        "🏗️ Crear escenario" to "Construir escenas y objetos del proyecto",
        "🤖 Crear IA" to "Enemigos, NPC y comportamientos",
        "🔧 Probar y corregir" to "Ejecutar, detectar errores y aplicar correcciones",
        "📁 Proyecto / archivos" to "Abrir y administrar el proyecto autorizado"
    )

    fun show(context: Context) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
        }
        root.addView(TextView(context).apply {
            text = "LIX · GODOT"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 6)
        })
        root.addView(TextView(context).apply {
            text = "Herramientas esenciales · simples y directas"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        })
        features.forEach { (name, desc) ->
            val row = TextView(context).apply {
                text = "$name\n$desc"
                textSize = 14f
                setPadding(18, 16, 18, 16)
                setOnClickListener {
                    when {
                        name.contains("Proyecto / archivos") -> context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                        name.contains("Crear personaje 3D") -> context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        })
                        else -> LixMegaModules.handle(context, name)
                    }
                }
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(context)
            .setView(root)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
