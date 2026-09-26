package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object LixCapabilityStore {
    data class Capability(
        val id: String,
        val title: String,
        val description: String,
        val category: String,
        val action: String,
        val requiresPermission: Boolean = false
    )

    private const val PREF = "lix_capabilities"

    private val capabilities = listOf(
        Capability("optimizer", "⚡ Optimizador automático", "Analiza recursos y guarda perfiles de rendimiento.", "Rendimiento", "optimizer"),
        Capability("memory_plus", "🧠 Memoria Plus", "Perfil ampliado para memoria contextual y organización.", "Inteligencia", "memory"),
        Capability("agent_plus", "🤖 Agente autónomo", "Planificación, ejecución y verificación de tareas autorizadas.", "Agente", "agent"),
        Capability("godot_agent", "🎮 Agente Godot", "Trabaja sobre un proyecto Godot autorizado y prepara cambios verificables.", "Desarrollo", "godot"),
        Capability("godot_scripts", "🧩 Constructor de scripts", "Crea y modifica scripts GDScript dentro de la carpeta autorizada.", "Desarrollo", "godot"),
        Capability("godot_scenes", "🏗️ Constructor de escenas", "Prepara archivos de escenas y recursos de proyecto.", "Desarrollo", "godot"),
        Capability("godot_debug", "🧪 Diagnóstico Godot", "Organiza pruebas y análisis de errores del proyecto.", "Desarrollo", "godot"),
        Capability("code_review", "🔍 Revisor de código", "Revisa código y señala errores, riesgos y oportunidades de mejora.", "Desarrollo", "code"),
        Capability("project_backup", "💾 Backup de proyectos", "Mantiene una política de copias antes de cambios autorizados.", "Desarrollo", "backup"),
        Capability("web_deep", "🌐 Búsqueda profunda", "Perfil para búsquedas web más estructuradas y verificadas.", "Internet", "web"),
        Capability("browser_agent", "🗺️ Agente navegador", "Permite encadenar navegación y tareas web autorizadas.", "Internet", "web"),
        Capability("translator", "🌎 Traductor universal", "Activa el perfil de traducción contextual.", "Lenguaje", "translator"),
        Capability("expert", "🧠 Modo experto", "Respuestas más profundas con comprobaciones y pasos concretos.", "Inteligencia", "expert"),
        Capability("tutor", "📚 Tutor", "Aprendizaje paso a paso con ejercicios y comprobaciones.", "Educación", "tutor"),
        Capability("vision", "👁️ Visión avanzada", "Perfil para OCR, imágenes y análisis visual.", "Visión", "vision"),
        Capability("live_vision", "👁️ Visión en vivo", "Prepara el flujo de cámara con permiso explícito.", "Visión", "vision", true),
        Capability("multimodal", "🧩 Multimodal", "Integra texto, voz, imágenes, archivos y generación visual.", "Multimodal", "multimodal"),
        Capability("voice", "🗣️ Voz continua", "Perfil para interacción de voz y TTS.", "Voz", "voice", true),
        Capability("wake", "🎙️ Activación por voz", "Habilita el servicio de activación de Lix cuando Android lo permita.", "Voz", "voice", true),
        Capability("visual_creator", "🖼️ Creador visual", "Acceso al generador visual conectado.", "Creatividad", "visual"),
        Capability("documents", "📝 Suite de documentos", "Crear, abrir y trabajar con TXT, Markdown y JSON.", "Productividad", "documents"),
        Capability("vault", "🔐 Bóveda segura", "Almacenamiento cifrado mediante Android Keystore.", "Seguridad", "vault"),
        Capability("guardian", "🛡️ Guardián", "Alertas y reglas locales configurables.", "Seguridad", "guardian"),
        Capability("pc_agent", "🖥️ Agente PC", "Conecta con un companion autorizado mediante endpoint.", "Dispositivos", "pc"),
        Capability("lix_network", "📡 Lix Red", "Conecta nodos autorizados de Lix.", "Dispositivos", "network"),
        Capability("task_engine", "⏱️ Motor de tareas", "Perfil para tareas programadas y seguimiento.", "Automatización", "tasks"),
        Capability("diagnostics", "🩺 Diagnóstico de Lix", "Muestra estado de recursos, servicios y componentes.", "Sistema", "diagnostics"),
        Capability("recovery", "🛠️ Recuperación", "Conserva estados y permite volver a perfiles seguros.", "Sistema", "recovery"),
        Capability("lab", "🧪 Laboratorio", "Herramientas experimentales y mediciones del dispositivo.", "Sistema", "lab"),
        Capability("evolution", "🧬 Evolución", "Catálogo de capacidades y actualizaciones modulares.", "Sistema", "evolution")
    )

    fun show(context: Context) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 12)
        }
        root.addView(TextView(context).apply {
            text = "🧬 CAPACIDADES DE LIX\nInstalá, activá o desactivá funciones sin reconstruir el núcleo."
            textSize = 18f
            setPadding(0, 0, 0, 18)
        })
        capabilities.forEach { capability ->
            val installed = isInstalled(context, capability.id)
            root.addView(TextView(context).apply {
                text = if (installed) "✓ " + capability.title else "+ " + capability.title
                textSize = 14f
                setPadding(16, 14, 16, 14)
                setOnClickListener { installOrToggle(context, capability) }
            })
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(root, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(context)
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Actualizar catálogo") { _, _ ->
                Thread {
                    try {
                        LixEvolution.refresh(context)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Catálogo actualizado.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "No se pudo actualizar el catálogo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }.show()
    }

    fun isInstalled(context: Context, id: String): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("installed_" + id, false)

    private fun installOrToggle(context: Context, capability: Capability) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "installed_" + capability.id
        val current = prefs.getBoolean(key, false)
        if (current) {
            prefs.edit().putBoolean(key, false).apply()
            Toast.makeText(context, capability.title + " desactivado.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putBoolean(key, true).apply()
        Toast.makeText(context, capability.title + " instalado y activado.", Toast.LENGTH_SHORT).show()
        when (capability.action) {
            "expert" -> prefs.edit().putBoolean("expert", true).apply()
            "tutor" -> prefs.edit().putBoolean("tutor", true).apply()
            "guardian" -> prefs.edit().putBoolean("guardian", true).apply()
            "godot" -> if (context is android.app.Activity) LixMegaModules.handle(context, "game studio")
            "visual" -> LixVisualCreator.open(context)
            "vault" -> LixMegaModules.handle(context, "bóveda")
            "pc" -> LixMegaModules.handle(context, "control total del pc")
            "network" -> LixMegaModules.handle(context, "lix red")
            "vision" -> LixMegaModules.handle(context, "visión en vivo")
            "multimodal" -> LixMegaModules.handle(context, "multimodal")
            "documents" -> LixMegaModules.handle(context, "documentos")
            "lab" -> LixMegaModules.handle(context, "laboratorio")
            "evolution" -> LixEvolution.catalog(context)
        }
    }
}
