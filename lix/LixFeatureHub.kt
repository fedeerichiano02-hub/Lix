package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object LixFeatureHub {
    private val features = listOf(
        "👁️ Visión en Vivo" to "Cámara + asistencia visual",
        "🤖 Agente — Hacelo vos" to "Ejecución de tareas autorizadas",
        "🧠 Cerebro / Memoria profunda" to "Memoria y aprendizaje contextual",
        "🛡️ Guardián" to "Condiciones y alertas",
        "🖥️ Control Total del PC" to "Control remoto autorizado",
        "🧩 Multimodal" to "Texto + imagen + archivos + voz",
        "🏗️ Constructor" to "Crear y modificar proyectos",
        "🧪 Laboratorio" to "Herramientas experimentales",
        "🗣️ Conversación Natural" to "Voz continua",
        "🌎 Traductor Universal" to "Traducción contextual",
        "🧠 Modo Experto" to "Respuestas especializadas",
        "📚 Tutor" to "Aprendizaje guiado",
        "🗺️ Navegador Inteligente" to "Internet y búsqueda",
        "📝 Documentos" to "Archivos y documentos",
        "🖼️ Creador Visual" to "Generación real de imágenes",
        "🎮 Game Studio / Godot" to "Trabajo sobre proyectos Godot",
        "🔐 Bóveda" to "Almacenamiento protegido",
        "📡 Centro de Control" to "Permisos y módulos",
        "🧬 Evolución" to "Mejoras modulares",
        "🌐 Lix Red" to "Base multidispositivo"
    )

    fun show(context: Context) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(context).apply {
            text = "CENTRO DE CONTROL DE LIX"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        })
        features.forEach { (name, desc) ->
            val row = TextView(context).apply {
                text = "$name\n$desc"
                textSize = 13f
                setPadding(18, 15, 18, 15)
                setOnClickListener {
                    when {
                        name.contains("Creador Visual") -> LixVisualCreator.open(context)
                        name.contains("Navegador") -> context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")))
                        name.contains("Control Total") -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        name.contains("Visión") -> context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        })
                        else -> LixMegaModules.handle(context, name)
                    }
                }
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(context).setView(root).setPositiveButton("Cerrar", null).show()
    }
}
