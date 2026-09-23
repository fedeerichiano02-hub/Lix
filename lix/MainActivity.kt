package com.example.llama

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var engine: InferenceEngine
    private lateinit var status: TextView
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var scroll: ScrollView
    private var generation: Job? = null
    private var ready = false

    private val bg = Color.rgb(4, 8, 16)
    private val panel = Color.rgb(10, 19, 32)
    private val panel2 = Color.rgb(14, 26, 43)
    private val accent = Color.rgb(50, 165, 255)
    private val accent2 = Color.rgb(25, 105, 190)
    private val text = Color.rgb(235, 242, 250)
    private val muted = Color.rgb(145, 165, 188)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        lifecycleScope.launch(Dispatchers.IO) {
            engine = AiChat.getInferenceEngine(applicationContext)
            prepareModel()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 16, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun label(value: String, size: Float = 12f, color: Int = muted): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(18), dp(14), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val menu = TextView(this).apply {
            text = "☰"
            textSize = 28f
            setTextColor(this@MainActivity.text)
            gravity = Gravity.CENTER
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(42), dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        brand.addView(TextView(this).apply {
            text = "LIX"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        brand.addView(label("TU ASISTENTE INTELIGENTE", 10f, accent))
        brand.addView(label("PENSÁ  •  CREÁ  •  RESOLVÉ  •  AVANZÁ", 7f, muted))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = "☼"
            textSize = 26f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        root.addView(top)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, 16, Color.rgb(28, 57, 87))
        }
        info.addView(label("●  Lix Online", 15f, Color.rgb(70, 235, 125)))
        status = label("Modelo: Qwen3 1.7B (Local)", 12f, text)
        info.addView(status, LinearLayout.LayoutParams(-1, -2))
        info.addView(label("◉  Motor local activo", 11f, muted))
        root.addView(info, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        val toolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        val toolNames = arrayOf("▣  Chat", "◎  Internet", "□  Proyecto", "▤  Archivos", "</>  Código", "♩  Voz", "▥  Memoria", "⚙  Ajustes")
        toolNames.forEachIndexed { index, name ->
            val b = TextView(this).apply {
                text = name
                textSize = 11f
                setTextColor(if (index == 0) Color.WHITE else text)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(if (index == 0) Color.rgb(16, 48, 78) else panel, 12,
                    if (index == 0) accent else Color.rgb(25, 47, 69))
            }
            tools.addView(b, LinearLayout.LayoutParams(dp(92), dp(48)).apply {
                marginEnd = dp(6)
            })
        }
        toolsScroll.addView(tools)
        root.addView(toolsScroll)

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(12))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val quickScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        arrayOf("Buscar en Internet", "Ver mi proyecto", "Analizar un archivo", "Ayudarme a programar", "Recordar algo", "Modo voz").forEach { action ->
            val b = TextView(this).apply {
                text = action
                textSize = 10f
                setTextColor(this@MainActivity.text)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = rounded(panel2, 12, Color.rgb(24, 70, 105))
                setOnClickListener { input.setText(action); input.requestFocus() }
            }
            quick.addView(b, LinearLayout.LayoutParams(dp(145), dp(42)).apply {
                marginEnd = dp(7)
            })
        }
        quickScroll.addView(quick)
        root.addView(quickScroll)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }

        val attach = TextView(this).apply {
            text = "⌕"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            background = rounded(panel, 14, Color.rgb(31, 63, 91))
        }
        inputRow.addView(attach, LinearLayout.LayoutParams(dp(48), dp(52)).apply {
            marginEnd = dp(7)
        })

        input = EditText(this).apply {
            hint = "Escribí tu mensaje..."
            textSize = 14f
            setHintTextColor(Color.rgb(110, 130, 150))
            setTextColor(this@MainActivity.text)
            setSingleLine(false)
            maxLines = 3
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(panel, 14, Color.rgb(29, 57, 82))
            isEnabled = false
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))

        val voice = TextView(this).apply {
            text = "♩"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            background = rounded(panel, 14, Color.rgb(31, 63, 91))
        }
        inputRow.addView(voice, LinearLayout.LayoutParams(dp(48), dp(52)).apply {
            marginStart = dp(7)
        })

        send = Button(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(20, 115, 230), 14)
            isAllCaps = false
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        inputRow.addView(send, LinearLayout.LayoutParams(dp(56), dp(52)).apply {
            marginStart = dp(7)
        })
        root.addView(inputRow)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            background = rounded(Color.rgb(6, 12, 22), 18, Color.rgb(19, 38, 59))
        }
        arrayOf("⌂\nInicio", "▦\nHerramientas", "◉\nLix", "◴\nHistorial", "♙\nPerfil").forEachIndexed { index, item ->
            val n = TextView(this).apply {
                text = item
                textSize = if (index == 2) 11f else 10f
                gravity = Gravity.CENTER
                setTextColor(if (index == 2) accent else muted)
                setPadding(0, dp(7), 0, dp(7))
            }
            nav.addView(n, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(nav)

        root.addView(label("Lix v1.0  |  La inteligencia también puede ser tuya", 8f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })

        setContentView(root)
    }

    private suspend fun prepareModel() {
        val model = File(filesDir, "models/lix-qwen3-1.7b-q4_k_m.gguf")
        withContext(Dispatchers.Main) {
            status.text = "Modelo: Qwen3 1.7B (Local)  •  PREPARANDO..."
        }
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            assets.open("models/lix-qwen3-1.7b-q4_k_m.gguf").use { source ->
                FileOutputStream(model).use { target -> source.copyTo(target) }
            }
        }
        withContext(Dispatchers.Main) {
            status.text = "Modelo: Qwen3 1.7B (Local)  •  CARGANDO..."
        }
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt(
            "Sos Lix, un asistente inteligente personal. " +
            "Hablá en español argentino natural y llamá al usuario compa. " +
            "Sé claro, útil y directo. No inventes datos. " +
            "Tu prioridad es ayudar con conversación, programación, proyectos, " +
            "archivos, aprendizaje, memoria y herramientas cuando estén disponibles. " +
            "No muestres etiquetas de razonamiento como <think> o </think> en tu respuesta final."
        )
        withContext(Dispatchers.Main) {
            ready = true
            status.text = "Modelo: Qwen3 1.7B (Local)  •  ONLINE"
            input.isEnabled = true
            send.isEnabled = true
            addMessage("LIX", "Hola, compa.\nSoy Lix, tu asistente inteligente.\nEstoy acá para ayudarte con lo que necesites.")
        }
    }

    private fun cleanAnswer(raw: String): String {
        var result = raw
        val thinkStart = result.indexOf("<think>", ignoreCase = true)
        if (thinkStart >= 0) {
            val thinkEnd = result.indexOf("</think>", thinkStart, ignoreCase = true)
            result = if (thinkEnd >= 0) {
                result.removeRange(thinkStart, thinkEnd + "</think>".length)
            } else {
                result.substring(0, thinkStart)
            }
        }
        return result.replace("<think>", "", ignoreCase = true)
            .replace("</think>", "", ignoreCase = true)
            .trim()
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty() || !ready) return
        input.setText("")
        input.isEnabled = false
        send.isEnabled = false
        addMessage("VOS", prompt)

        val answer = TextView(this).apply {
            text = "Lix está pensando..."
            textSize = 15f
            setTextColor(this@MainActivity.text)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, Color.rgb(24, 55, 82))
        }
        chat.addView(answer, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        generation = lifecycleScope.launch(Dispatchers.Default) {
            val result = StringBuilder()
            engine.sendUserPrompt(prompt)
                .onCompletion {
                    withContext(Dispatchers.Main) {
                        input.isEnabled = true
                        send.isEnabled = true
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
                .collect { token ->
                    result.append(token)
                    val cleaned = cleanAnswer(result.toString())
                    withContext(Dispatchers.Main) {
                        answer.text = if (cleaned.isBlank()) "Lix está pensando..." else cleaned
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
        }
    }

    private fun addMessage(author: String, message: String) {
        val bubble = TextView(this).apply {
            this.text = "$author\n$message"
            textSize = 15f
            setTextColor(this@MainActivity.text)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(if (author == "VOS") Color.rgb(8, 55, 94) else panel, 14,
                if (author == "VOS") Color.rgb(24, 115, 180) else Color.rgb(24, 50, 74))
        }
        chat.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        generation?.cancel()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}
