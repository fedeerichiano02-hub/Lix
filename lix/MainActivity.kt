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

    private val bg = Color.rgb(3, 7, 14)
    private val panel = Color.rgb(8, 18, 30)
    private val panel2 = Color.rgb(11, 25, 41)
    private val panel3 = Color.rgb(14, 31, 50)
    private val border = Color.rgb(24, 61, 91)
    private val accent = Color.rgb(52, 166, 255)
    private val accentDark = Color.rgb(18, 73, 120)
    private val textColor = Color.rgb(237, 243, 250)
    private val muted = Color.rgb(142, 163, 185)
    private val green = Color.rgb(71, 232, 124)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        lifecycleScope.launch(Dispatchers.IO) {
            engine = AiChat.getInferenceEngine(applicationContext)
            prepareModel()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 16,
        strokeColor: Int? = null,
        strokeWidth: Int = 1
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            strokeColor?.let { setStroke(dp(strokeWidth), it) }
        }

    private fun label(value: String, size: Float = 12f, color: Int = muted): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }

    private fun horizontalScroller(): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
        }

        val menu = TextView(this).apply {
            text = "☰"
            textSize = 29f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, 14)
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(58)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        brand.addView(TextView(this).apply {
            text = "LIX"
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, dp(40)))
        brand.addView(label("TU ASISTENTE INTELIGENTE", 10f, accent).apply {
            gravity = Gravity.CENTER
        })
        brand.addView(label("PENSÁ  •  CREÁ  •  RESOLVÉ  •  AVANZÁ", 7.5f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        header.addView(TextView(this).apply {
            text = "☼"
            textSize = 29f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(58)))

        root.addView(header)

        // Status card
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(panel, 18, border)
        }

        info.addView(label("●  Lix Online", 15f, green).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = label("Modelo: Qwen3 1.7B (Local)", 12.5f, textColor)
        info.addView(status, LinearLayout.LayoutParams(-1, dp(24)))
        info.addView(label("◉  Motor local activo", 11.5f, muted))

        root.addView(info, LinearLayout.LayoutParams(-1, dp(122)).apply {
            topMargin = dp(7)
        })

        // Main tool tabs
        val toolsScroll = horizontalScroller()
        toolsScroll.setPadding(0, dp(9), 0, dp(4))
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toolNames = arrayOf(
            "▣  Chat", "◎  Internet", "□  Proyecto", "▤  Archivos",
            "</>  Código", "♩  Voz", "▥  Memoria", "⚙  Ajustes"
        )

        toolNames.forEachIndexed { index, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 11f
                setTextColor(if (index == 0) Color.WHITE else textColor)
                gravity = Gravity.CENTER
                setPadding(dp(13), 0, dp(13), 0)
                background = rounded(
                    if (index == 0) Color.rgb(14, 48, 78) else panel,
                    15,
                    if (index == 0) accent else border
                )
            }
            tools.addView(chip, LinearLayout.LayoutParams(dp(132), dp(50)).apply {
                marginEnd = dp(8)
            })
        })

        toolsScroll.addView(tools)
        root.addView(toolsScroll, LinearLayout.LayoutParams(-1, dp(63)))

        // Chat area
        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(4), dp(1), dp(10))
        }

        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Quick actions
        val quickScroll = horizontalScroller()
        quickScroll.setPadding(0, dp(2), 0, dp(4))
        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        arrayOf(
            "Buscar en Internet",
            "Ver mi proyecto",
            "Analizar un archivo",
            "Ayudarme a programar",
            "Recordar algo",
            "Modo voz"
        ).forEach { action ->
            val chip = TextView(this).apply {
                text = action
                textSize = 10.5f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setPadding(dp(13), 0, dp(13), 0)
                background = rounded(panel2, 14, border)
                setOnClickListener {
                    input.setText(action)
                    input.setSelection(input.text.length)
                    input.requestFocus()
                }
            }
            quick.addView(chip, LinearLayout.LayoutParams(dp(176), dp(45)).apply {
                marginEnd = dp(8)
            })
        })

        quickScroll.addView(quick)
        root.addView(quickScroll, LinearLayout.LayoutParams(-1, dp(53)))

        // Composer
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }

        val attach = TextView(this).apply {
            text = "+"
            textSize = 28f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            background = rounded(panel, 15, border)
        }
        inputRow.addView(attach, LinearLayout.LayoutParams(dp(52), dp(58)).apply {
            marginEnd = dp(8)
        })

        input = EditText(this).apply {
            hint = "Escribí tu mensaje..."
            textSize = 14f
            setHintTextColor(Color.rgb(105, 126, 148))
            setTextColor(textColor)
            setSingleLine(false)
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), 0, dp(12), 0)
            background = rounded(panel, 15, border)
            isEnabled = false
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(58), 1f))

        val voice = TextView(this).apply {
            text = "◉"
            textSize = 20f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            background = rounded(panel, 15, border)
        }
        inputRow.addView(voice, LinearLayout.LayoutParams(dp(52), dp(58)).apply {
            marginStart = dp(8)
        })

        send = Button(this).apply {
            text = "➤"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(22, 119, 238), 15)
            isAllCaps = false
            isEnabled = false
            setPadding(0, 0, 0, 0)
            setOnClickListener { sendMessage() }
        }
        inputRow.addView(send, LinearLayout.LayoutParams(dp(62), dp(58)).apply {
            marginStart = dp(8)
        })

        root.addView(inputRow)

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(4), dp(2), 0)
            background = rounded(Color.rgb(5, 12, 22), 18, Color.rgb(19, 40, 61))
        }

        arrayOf(
            "⌂\nInicio",
            "▦\nHerramientas",
            "◉\nLix",
            "◴\nHistorial",
            "♙\nPerfil"
        ).forEachIndexed { index, item ->
            val navItem = TextView(this).apply {
                text = item
                textSize = if (index == 2) 10.5f else 9.5f
                gravity = Gravity.CENTER
                setTextColor(if (index == 2) accent else muted)
                setPadding(0, dp(7), 0, dp(6))
                includeFontPadding = false
            }
            nav.addView(navItem, LinearLayout.LayoutParams(0, dp(53), 1f))
        }

        root.addView(nav, LinearLayout.LayoutParams(-1, dp(59)))

        root.addView(label("Lix v1.0  |  La inteligencia también puede ser tuya", 8f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(18)))

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
            addMessage(
                "LIX",
                "Hola, compa.\nSoy Lix, tu asistente inteligente.\nEstoy acá para ayudarte con lo que necesites."
            )
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

        return result
            .replace("<think>", "", ignoreCase = true)
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
            setTextColor(textColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, border)
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
                        answer.text =
                            if (cleaned.isBlank()) "Lix está pensando..." else cleaned
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
        }
    }

    private fun addMessage(author: String, message: String) {
        val bubble = TextView(this).apply {
            this.text = "$author\n$message"
            textSize = 15f
            setTextColor(textColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(
                if (author == "VOS") Color.rgb(8, 55, 94) else panel,
                15,
                if (author == "VOS") Color.rgb(24, 115, 180) else border
            )
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
