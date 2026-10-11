package com.example.llama

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var taskState: TextView
    private lateinit var taskPhase: TextView
    private lateinit var taskActivity: TextView
    private lateinit var taskProgressLabel: TextView
    private lateinit var taskProgress: ProgressBar
    private lateinit var taskDetailsButton: TextView
    private var pending3dPrompt = "Crear personaje 3D para Godot"
    private val monitorHandler = Handler(Looper.getMainLooper())
    private val monitorRunnable = object : Runnable {
        override fun run() {
            refreshTaskMonitor()
            monitorHandler.postDelayed(this, 1000L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(color: Int = Color.rgb(20, 24, 32)): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(55, 65, 80)) }

    private fun button(title: String, description: String, action: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = card()
            isClickable = true
            setOnClickListener { action() }
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = description
            textSize = 11f
            setTextColor(Color.rgb(160, 170, 185))
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)
        buildUi()
        monitorHandler.post(monitorRunnable)
    }

    override fun onDestroy() {
        monitorHandler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(6, 9, 16), Color.rgb(12, 16, 27), Color.rgb(18, 10, 25))
            )
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Lix · Godot"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(TextView(this).apply {
            text = "●"
            textSize = 12f
            setTextColor(Color.rgb(70, 225, 125))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(56)))
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Asistente de desarrollo · solo herramientas para Godot"
            textSize = 11f
            setTextColor(Color.rgb(155, 165, 180))
            setPadding(dp(3), 0, 0, dp(10))
        })

        status = TextView(this).apply {
            text = "Listo para trabajar"
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(190, 200, 215))
            setPadding(dp(14), 0, dp(14), 0)
            background = card(Color.rgb(16, 20, 27))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(10) })

        root.addView(buildTaskMonitor(), LinearLayout.LayoutParams(-1, dp(206)).apply { bottomMargin = dp(10) })

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        fun add(title: String, desc: String, action: () -> Unit) {
            content.addView(button(title, desc, action), LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
        }

        add("👤  Crear personaje 3D", "Escribí directamente qué personaje querés y Lix generará el GLB para Godot.") {
            input.setText("Creá un personaje 3D realista para The Last Launch, listo para Godot")
            input.requestFocus()
        }

        add("🧠  Conectar cerebro IA", "Configurá una API para conversar y razonar con un modelo en la nube.") {
            LixCloudAI.configure(this)
        }

        add("💬  Conversar con Lix IA", "Mandá una consulta al modelo en la nube sin iniciar una tarea Godot.") {
            val prompt = input.text.toString().trim()
            if (prompt.isBlank()) {
                Toast.makeText(this, "Escribí primero tu consulta.", Toast.LENGTH_SHORT).show()
                input.requestFocus()
            } else {
                input.setText("")
                status.text = "Lix está pensando…"
                LixCloudAI.ask(this, prompt) { answer ->
                    status.text = "Respuesta recibida"
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Lix · IA")
                        .setMessage(answer)
                        .setPositiveButton("Cerrar", null)
                        .show()
                }
            }
        }

        add("🎞  Animar personaje", "Prepará rigging y animaciones para el personaje usando herramientas compatibles con Godot.") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mixamo.com/")))
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo abrir el navegador.", Toast.LENGTH_SHORT).show()
            }
        }

        add("📁  Autorizar proyecto Godot", "Elegí la carpeta del proyecto para que Lix pueda leer y modificar archivos.") {
            LixAutomation.handle(this, "elegir proyecto godot")
        }

        add("🧩  Trabajar sobre el proyecto", "Escribí una tarea: escenas, scripts, personaje, IA, inventario, combate, movimiento o sistemas.") {
            input.requestFocus()
        }

        add("⚙  Trabajar en segundo plano", "Mandá la orden actual a una tarea persistente para que Lix pueda seguir trabajando.") {
            queueTask()
        }

        add("📦  Importar GLB / asset", "Seleccioná cualquier recurso para incorporarlo o analizarlo en el proyecto.") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, 4103)
        }

        content.addView(TextView(this).apply {
            text = "Lix está enfocada exclusivamente en el desarrollo del juego."
            textSize = 10f
            setTextColor(Color.rgb(120, 130, 145))
            setPadding(dp(4), dp(8), dp(4), dp(18))
        })

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Ordená a Lix qué hacer en Godot..."
            textSize = 13f
            maxLines = 3
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(115, 125, 140))
            setPadding(dp(13), 0, dp(8), 0)
            background = card(Color.rgb(17, 21, 28))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(58), 1f))
        val send = TextView(this).apply {
            text = "↑"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = card(Color.rgb(28, 34, 44))
            setOnClickListener { queueTask() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(8) })
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(8) })

        setContentView(root)
    }

    private fun buildTaskMonitor(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = card(Color.rgb(15, 19, 27))
        }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "📊  Monitor de tarea"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        taskState = TextView(this).apply {
            text = "Sin tarea"
            textSize = 11f
            setTextColor(Color.rgb(155, 165, 180))
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(taskState, LinearLayout.LayoutParams(dp(150), dp(28)))
        box.addView(titleRow)

        taskPhase = TextView(this).apply {
            text = "Lix está lista"
            textSize = 10f
            setTextColor(Color.rgb(160, 170, 185))
        }
        box.addView(taskPhase)

        taskProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        box.addView(taskProgress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8); bottomMargin = dp(4) })

        taskProgressLabel = TextView(this).apply {
            text = "0% · progreso por etapas"
            textSize = 9f
            setTextColor(Color.rgb(125, 135, 150))
        }
        box.addView(taskProgressLabel)

        taskActivity = TextView(this).apply {
            text = "Última actividad: —"
            textSize = 9f
            setTextColor(Color.rgb(125, 135, 150))
        }
        box.addView(taskActivity)

        taskDetailsButton = TextView(this).apply {
            text = "🔎  VER DETALLES DEL ERROR"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = card(Color.rgb(38, 44, 56))
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener {
                val current = LixBackgroundStore.pending(this@MainActivity)
                    ?: LixBackgroundStore.latest(this@MainActivity, 1).firstOrNull()
                if (current == null) {
                    Toast.makeText(this@MainActivity, "No hay un registro de tarea disponible.", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this@MainActivity, LixTaskDetailsActivity::class.java).apply {
                        putExtra("task_id", current.id)
                    })
                }
            }
        }
        box.addView(taskDetailsButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        return box
    }

    private fun queueTask() {
        val prompt = input.text.toString().trim()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Escribí primero qué querés que Lix haga.", Toast.LENGTH_SHORT).show()
            input.requestFocus()
            return
        }
        if (LixTextTo3D.isRequest(prompt)) {
            input.setText("")
            status.text = "Lix 3D está generando..."
            LixTextTo3D.start(this, prompt)
            return
        }
        if (!LixProjectManager.hasAuthorizedProject(this)) {
            status.text = "Necesitás autorizar primero un proyecto Godot"
            Toast.makeText(this, "Primero elegí la carpeta de tu proyecto Godot.", Toast.LENGTH_LONG).show()
            LixAutomation.handle(this, "elegir proyecto godot")
            return
        }
        val id = "godot_${System.currentTimeMillis()}"
        val data = androidx.work.Data.Builder()
            .putString(LixBackgroundWorker.KEY_ID, id)
            .putString(LixBackgroundWorker.KEY_PROMPT, LixGodotAgent.workspacePrompt(this, prompt))
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<LixBackgroundWorker>()
            .setInputData(data)
            .addTag("lix_godot")
            .build()
        androidx.work.WorkManager.getInstance(this).enqueue(request)
        val now = System.currentTimeMillis()
        LixBackgroundStore.upsert(this, LixBackgroundStore.Task(id, prompt, "queued", progress = 0, phase = "En cola", lastActivity = now, startedAt = now))
        input.setText("")
        status.text = "Tarea enviada · el monitor mostrará si sigue activa o se queda sin actividad"
        Toast.makeText(this, "Tarea enviada. Mirá el monitor para ver su estado real.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshTaskMonitor() {
        val task = LixBackgroundStore.pending(this) ?: LixBackgroundStore.latest(this, 1).firstOrNull()
        if (task == null) {
            taskState.text = "Sin tarea"
            taskState.setTextColor(Color.rgb(155, 165, 180))
            taskPhase.text = "Lix está lista"
            taskProgress.progress = 0
            taskProgressLabel.text = "0% · progreso por etapas"
            taskActivity.text = "Última actividad: —"
            taskDetailsButton.visibility = View.GONE
            return
        }

        val now = System.currentTimeMillis()
        val age = if (task.lastActivity > 0) now - task.lastActivity else Long.MAX_VALUE
        val displayStatus: String
        val statusColor: Int
        when (task.status) {
            "queued" -> {
                displayStatus = "🟡 EN COLA"
                statusColor = Color.rgb(240, 190, 70)
            }
            "running" -> {
                if (age <= 15000L) {
                    displayStatus = "🟢 TRABAJANDO"
                    statusColor = Color.rgb(70, 225, 125)
                } else if (age <= 60000L) {
                    displayStatus = "🟡 SIN ACTIVIDAD"
                    statusColor = Color.rgb(240, 190, 70)
                } else {
                    displayStatus = "🔴 POSIBLEMENTE TRABADA"
                    statusColor = Color.rgb(245, 90, 90)
                }
            }
            "completed" -> {
                displayStatus = "✅ COMPLETADA"
                statusColor = Color.rgb(70, 225, 125)
            }
            "waiting_external" -> {
                displayStatus = "🟠 ESPERA EXTERNA"
                statusColor = Color.rgb(240, 160, 70)
            }
            "needs_user" -> {
                displayStatus = "🟣 NECESITA ACCIÓN"
                statusColor = Color.rgb(190, 130, 245)
            }
            "failed" -> {
                displayStatus = "❌ ERROR"
                statusColor = Color.rgb(245, 90, 90)
            }
            else -> {
                displayStatus = task.status.uppercase()
                statusColor = Color.rgb(155, 165, 180)
            }
        }
        taskState.text = displayStatus
        taskState.setTextColor(statusColor)
        taskPhase.text = task.phase.ifBlank { "Trabajando sobre el proyecto Godot" }
        taskProgress.progress = task.progress.coerceIn(0, 100)
        taskProgressLabel.text = "${task.progress.coerceIn(0, 100)}% · progreso por etapas, no tiempo restante"
        taskActivity.text = if (task.lastActivity > 0) "Última actividad: ${formatAge(age)}" else "Última actividad: —"
        taskDetailsButton.visibility = if (task.status == "failed") View.VISIBLE else View.GONE

        if (task.status == "completed") status.text = "Tarea completada y proyecto verificado"
        else if (task.status == "failed") status.text = "La tarea terminó con error"
        else if (task.status == "needs_user") status.text = "Lix necesita una acción tuya"
        else if (task.status == "waiting_external") status.text = "Lix está esperando un servicio externo"
    }

    private fun formatAge(age: Long): String = when {
        age < 1000L -> "ahora"
        age < 60000L -> "hace ${age / 1000L}s"
        age < 3600000L -> "hace ${age / 60000L}m"
        else -> "hace ${age / 3600000L}h"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 4102) {
            val uri = data.data ?: return
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            LixImageTo3D.start(this, uri, pending3dPrompt)
            status.text = "Generando personaje 3D..."
        } else if (requestCode == 4201) {
            val uri = data.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) { }
            getSharedPreferences("lix", MODE_PRIVATE).edit().putString(LixAutomation.PREF_TREE_URI, uri.toString()).apply()
            status.text = "Proyecto Godot autorizado correctamente"
            Toast.makeText(this, "Proyecto Godot autorizado.", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 4103) {
            status.text = "Asset seleccionado · listo para incorporar"
            Toast.makeText(this, "Asset seleccionado.", Toast.LENGTH_SHORT).show()
        }
    }
}
