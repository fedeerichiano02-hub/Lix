package com.example.llama

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shows the full diagnostic record of the selected background task. */
class LixTaskDetailsActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("task_id").orEmpty()
        val task = LixBackgroundStore.latest(this, 40).lastOrNull { it.id == id }
            ?: LixBackgroundStore.latest(this, 1).firstOrNull()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(10, 13, 20))
        }
        root.addView(TextView(this).apply {
            text = "🔎 Detalles de la tarea"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(14))
        })

        val body = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(210, 218, 230))
            gravity = Gravity.TOP
            setTextIsSelectable(true)
            text = if (task == null) {
                "No hay un registro de tarea disponible."
            } else {
                buildString {
                    append("Estado: ").append(task.status).append("\n")
                    append("Etapa: ").append(task.phase.ifBlank { "—" }).append("\n")
                    append("Progreso: ").append(task.progress).append("%\n")
                    append("ID: ").append(task.id).append("\n\n")
                    append("ORDEN:\n").append(task.prompt.ifBlank { "—" }).append("\n\n")
                    append("RESULTADO / ERROR:\n")
                    append(task.result.ifBlank { "No hay detalles registrados." })
                }
            }
        }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Cerrar"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(32, 39, 52))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        setContentView(root)
    }
}
