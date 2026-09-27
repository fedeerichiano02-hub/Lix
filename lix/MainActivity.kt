package com.example.llama

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var pending3dPrompt = "Crear personaje 3D para Godot"

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

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        fun add(title: String, desc: String, action: () -> Unit) {
            content.addView(button(title, desc, action), LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
        }

        add("👤  Crear personaje 3D", "Elegí una imagen de referencia y generá un GLB listo para importar en Godot.") {
            pending3dPrompt = "Crear personaje 3D para Godot"
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }, 4102)
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

    private fun queueTask() {
        val prompt = input.text.toString().trim()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Escribí primero qué querés que Lix haga.", Toast.LENGTH_SHORT).show()
            input.requestFocus()
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
        LixBackgroundStore.upsert(this, LixBackgroundStore.Task(id, prompt, "queued"))
        input.setText("")
        status.text = "Tarea enviada · Lix puede seguir trabajando en segundo plano"
        Toast.makeText(this, "Tarea enviada.", Toast.LENGTH_SHORT).show()
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
            status.text = "Proyecto Godot autorizado"
        } else if (requestCode == 4103) {
            status.text = "Asset seleccionado · listo para incorporar"
            Toast.makeText(this, "Asset seleccionado.", Toast.LENGTH_SHORT).show()
        }
    }
}
