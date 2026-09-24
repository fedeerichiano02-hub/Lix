package com.example.llama

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
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
import android.widget.Toast
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var engine: InferenceEngine
    private lateinit var status: TextView
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var scroll: ScrollView
    private var generation: Job? = null
    private var ready = false
    private var internetMode = false
    private var autoWebBusy = false
    private var lastSearchContext = ""
    private var lastUiUpdate = 0L
    private var selectedFileText: String? = null
    private var profileName = "compa"
    private val history = mutableListOf<Pair<String, String>>()
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("lix", MODE_PRIVATE) }

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
        profileName = prefs.getString("profile_name", "compa") ?: "compa"
        loadHistory()
        tts = TextToSpeech(this, TextToSpeech.OnInitListener { if (it == TextToSpeech.SUCCESS) tts.language = Locale("es", "AR") })
        buildUi()
        setupSpeech()
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
        val bgTop = Color.rgb(5, 8, 19)
        val bgBottom = Color.rgb(12, 7, 28)
        val glass = Color.argb(135, 20, 27, 49)
        val glassStrong = Color.argb(185, 17, 24, 45)
        val glassSoft = Color.argb(95, 38, 46, 75)
        val line = Color.argb(110, 119, 164, 218)
        val cyan = Color.rgb(53, 224, 255)
        val blue = Color.rgb(76, 128, 255)
        val violet = Color.rgb(177, 92, 255)
        val white = Color.rgb(244, 248, 255)
        val muted = Color.rgb(158, 170, 198)

        fun card(color: Int, radius: Int = 20, stroke: Int = line) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }
        fun t(value: String, size: Float, color: Int = white) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }
        fun hs() = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bgTop, Color.rgb(8, 12, 28), bgBottom)
            )
            setPadding(dp(14), dp(9), dp(14), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(t("☰", 23f).apply {
            gravity = Gravity.CENTER
            setOnClickListener { openMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        brand.addView(t("Lix", 24f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        brand.addView(t("ASISTENTE INTELIGENTE", 8f, cyan).apply {
            gravity = Gravity.CENTER
            letterSpacing = .16f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(t("⋯", 28f).apply {
            gravity = Gravity.CENTER
            setOnClickListener { openSettings() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        root.addView(header)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(12))
            background = card(glassStrong, 24)
        }
        val infoTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        infoTop.addView(t("Lix", 16f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = t("Preparando…", 11f, muted)
        infoTop.addView(status)
        info.addView(infoTop)
        info.addView(t("Tu espacio privado para pensar, crear y resolver.", 12f, muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(info, LinearLayout.LayoutParams(-1, dp(80)).apply { topMargin = dp(4) })

        val toolsScroll = hs().apply { setPadding(0, dp(9), 0, dp(4)) }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        arrayOf("Chat", "Internet", "Proyecto", "Archivos", "Código", "Voz", "Memoria", "Ajustes")
            .forEachIndexed { index, name ->
                val chip = t((if (index == 0) "●  " else "○  ") + name, 11f, if (index == 0) white else muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(13), 0, dp(13), 0)
                    background = card(
                        if (index == 0) Color.argb(170, 37, 93, 143) else glassSoft,
                        18,
                        if (index == 0) Color.argb(190, 70, 221, 255) else line
                    )
                    setOnClickListener { toolAction(index) }
                }
                tools.addView(chip, LinearLayout.LayoutParams(dp(118), dp(43)).apply {
                    marginEnd = dp(8)
                })
            }
        toolsScroll.addView(tools)
        root.addView(toolsScroll, LinearLayout.LayoutParams(-1, dp(56)))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(7), dp(1), dp(12))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(23), dp(27), dp(23), dp(27))
            background = card(
                Color.argb(105, 28, 35, 61),
                28,
                Color.argb(100, 120, 157, 219)
            )
        }
        welcome.addView(t("Lix", 34f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        welcome.addView(t("¿En qué trabajamos hoy?", 17f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        welcome.addView(t(
            "Preguntá, escribí, investigá o hablá. Lix se adapta a vos.",
            12f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        })
        chat.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val quickScroll = hs().apply { setPadding(0, dp(1), 0, dp(5)) }
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        arrayOf(
            "Buscar algo",
            "Analizar archivo",
            "Ayudarme a programar",
            "Recordar esto",
            "Hablar con Lix"
        ).forEach { action ->
            val chip = t(action, 10.5f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = card(glassSoft, 16)
                setOnClickListener { quickAction(action) }
            }
            quick.addView(chip, LinearLayout.LayoutParams(dp(160), dp(42)).apply {
                marginEnd = dp(8)
            })
        }
        quickScroll.addView(quick)
        root.addView(quickScroll, LinearLayout.LayoutParams(-1, dp(51)))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(7))
        }
        composer.addView(t("＋", 25f).apply {
            gravity = Gravity.CENTER
            background = card(glassSoft, 18)
            setOnClickListener { pickFile() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)).apply {
            marginEnd = dp(7)
        })

        input = EditText(this).apply {
            hint = "Escribile a Lix…"
            textSize = 14f
            setTextColor(white)
            setHintTextColor(Color.rgb(117, 130, 157))
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(10), 0)
            background = card(Color.argb(145, 20, 27, 48), 19)
            isEnabled = false
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f))

        composer.addView(t("◉", 19f, cyan).apply {
            gravity = Gravity.CENTER
            background = card(glassSoft, 18)
            setOnClickListener { startVoice() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)).apply {
            marginStart = dp(7)
        })

        send = t("↑", 22f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, violet)
            ).apply { cornerRadius = dp(18).toFloat() }
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(55), dp(56)).apply {
            marginStart = dp(7)
        })
        root.addView(composer)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = card(
                Color.argb(125, 13, 18, 34),
                21,
                Color.argb(90, 104, 139, 193)
            )
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        arrayOf("Inicio", "Herramientas", "Lix", "Historial", "Perfil")
            .forEachIndexed { index, name ->
                nav.addView(t(
                    name,
                    if (index == 2) 10.5f else 9.5f,
                    if (index == 2) cyan else muted
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(7))
                    setOnClickListener { bottomAction(index) }
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(t(
            "Lix  •  privado, local y pensado para vos",
            8f, Color.rgb(116, 126, 151)
        ).apply {
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
            "La aplicación puede consultar Internet automáticamente cuando una pregunta requiere información actual o verificación; " +
            "usá los resultados entregados como fuente y no inventes datos que no estén respaldados. Respondé rápido y de forma directa. " +
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

        if (prompt.startsWith("Recordá esto:", true)) {
            remember(prompt.substringAfter(":").trim())
            addMessage("VOS", prompt)
            addMessage("LIX", "Listo, compa. Lo guardé en la memoria de Lix.")
            input.setText("")
            return
        }

        if (prompt.startsWith("¿Qué recordás", true) || prompt.startsWith("Que recordas", true)) {
            addMessage("VOS", prompt)
            addMessage("LIX", prefs.getString("memory", "")?.ifBlank { "No tengo recuerdos guardados todavía." } ?: "No tengo recuerdos guardados todavía.")
            input.setText("")
            return
        }

        if (internetMode || shouldSearchWebAutomatically(prompt)) {
            input.setText("")
            input.isEnabled = false
            send.isEnabled = false
            internetMode = false
            addMessage("VOS", prompt)
            searchInternetAndAnswer(prompt)
            return
        }

        input.setText("")
        input.isEnabled = false
        send.isEnabled = false
        addMessage("VOS", prompt)
        history.add("VOS" to prompt)
        saveHistory()

        val enriched = buildContextPrompt(prompt)
        selectedFileText = null

        val answer = TextView(this).apply {
            text = "Lix está pensando..."
            textSize = 15f
            setTextColor(textColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, border)
        }

        chat.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        generation = lifecycleScope.launch(Dispatchers.Default) {
            val result = StringBuilder()
            engine.sendUserPrompt(enriched)
                .onCompletion {
                    withContext(Dispatchers.Main) {
                        val final = cleanAnswer(result.toString())
                        if (final.isNotBlank()) {
                            history.add("LIX" to final)
                            saveHistory()
                            learnFromConversation(prompt, final)
                            if (prefs.getBoolean("tts", false)) tts.speak(final, TextToSpeech.QUEUE_FLUSH, null, "lix")
                        }
                        input.isEnabled = true
                        send.isEnabled = true
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
                .collect { token ->
                    result.append(token)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 70L) {
                        lastUiUpdate = now
                        val cleaned = cleanAnswer(result.toString())
                        withContext(Dispatchers.Main) {
                            answer.text = if (cleaned.isBlank()) "Lix está pensando..." else cleaned
                            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                        }
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


    private fun shouldSearchWebAutomatically(prompt: String): Boolean {
        val q = prompt.trim().lowercase(Locale("es", "AR"))
        if (q.length < 4) return false
        val localOnly = listOf("recordá esto", "que recordas", "qué recordás", "mi historial", "mi memoria")
        if (localOnly.any { q.startsWith(it) }) return false
        val webSignals = listOf(
            "hoy", "ahora", "actual", "actualmente", "último", "última", "últimos", "últimas",
            "reciente", "recientemente", "noticias", "precio", "precios", "cuánto vale", "cotización",
            "clima", "tiempo", "horario", "abierto", "cerrado", "resultados", "partido", "elecciones",
            "quién es", "quién fue", "cuándo sale", "cuándo es", "fecha de", "versión actual",
            "buscar", "buscá", "investigá", "verificá", "comprobá", "en internet", "en github",
            "fuente", "fuentes", "link", "enlace"
        )
        if (webSignals.any { q.contains(it) }) return true
        val question = q.startsWith("qué ") || q.startsWith("que ") ||
            q.startsWith("cómo ") || q.startsWith("como ") ||
            q.startsWith("por qué ") || q.startsWith("porque ") ||
            q.startsWith("dónde ") || q.startsWith("donde ") ||
            q.startsWith("cuándo ") || q.startsWith("cuando ") ||
            q.startsWith("cuánto ") || q.startsWith("cuanto ") ||
            q.startsWith("quién ") || q.startsWith("quien ")
        val knowledgeTerms = listOf("es cierto", "es verdad", "existe", "funciona", "significa", "sirve para")
        return question && knowledgeTerms.any { q.contains(it) }
    }

    private fun learnFromConversation(user: String, assistant: String) {
        val u = user.trim()
        if (u.isBlank()) return
        val lower = u.lowercase(Locale("es", "AR"))
        val signals = listOf("quiero", "prefiero", "me gusta", "no me gusta", "no quiero", "siempre", "nunca", "acordate", "recordá", "llamame", "llámame")
        if (!signals.any { lower.contains(it) }) return
        val old = prefs.getString("learning", "") ?: ""
        val lesson = "• Usuario: " + u.take(500) + "\n• Respuesta: " + assistant.take(700)
        prefs.edit().putString("learning", (old + "\n" + lesson).trim().takeLast(12000)).apply()
    }

    private fun toolAction(index: Int) {
        when (index) {
            0 -> { internetMode = false; toast("Chat local activo") }
            1 -> { internetMode = true; input.hint = "¿Qué querés buscar?"; input.requestFocus(); toast("Internet automático activo") }
            2, 3 -> pickFile()
            4 -> { input.setText("Ayudame a programar: "); input.setSelection(input.text.length); input.requestFocus() }
            5 -> startVoice()
            6 -> showMemory()
            7 -> openSettings()
        }
    }

    private fun quickAction(action: String) {
        when (action) {
            "Buscar en Internet" -> { internetMode = true; input.setText(""); input.hint = "¿Qué querés buscar?"; input.requestFocus() }
            "Ver mi proyecto", "Analizar un archivo" -> pickFile()
            "Ayudarme a programar" -> { input.setText("Ayudame a programar: "); input.setSelection(input.text.length); input.requestFocus() }
            "Recordar algo" -> { input.setText("Recordá esto: "); input.setSelection(input.text.length); input.requestFocus() }
            "Modo voz" -> startVoice()
        }
    }

    private fun bottomAction(index: Int) {
        when (index) {
            0 -> toast("Inicio")
            1 -> openMenu()
            2 -> input.requestFocus()
            3 -> showHistory()
            4 -> openProfile()
        }
    }

    private fun openMenu() {
        val items = arrayOf("Chat", "Internet", "Proyecto / archivos", "Código", "Voz", "Memoria", "Historial", "Ajustes", "Perfil")
        AlertDialog.Builder(this).setTitle("Lix").setItems(items) { _, which ->
            when (which) {
                0 -> toolAction(0)
                1 -> toolAction(1)
                2 -> pickFile()
                3 -> toolAction(4)
                4 -> startVoice()
                5 -> showMemory()
                6 -> showHistory()
                7 -> openSettings()
                8 -> openProfile()
            }
        }.show()
    }

    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val text = readUri(uri)
                    withContext(Dispatchers.Main) {
                        selectedFileText = text
                        input.setText("Analizá el archivo que adjunté")
                        input.requestFocus()
                        toast("Archivo cargado")
                    }
                }
            }
        }
    }

    private fun readUri(uri: Uri): String = try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.take(12000)
            ?: "No se pudo leer el archivo."
    } catch (e: Exception) {
        "No se pudo leer este archivo: ${e.message}"
    }

    private fun buildContextPrompt(prompt: String): String {
        return buildString {
            append("CONTEXTO DE LIX:\n")
            val memory = prefs.getString("memory", "")?.trim().orEmpty()
            if (memory.isNotBlank()) {
                append("MEMORIA DEL USUARIO:\n")
                append(memory.take(3500))
                append("\n\n")
            }
            val learning = prefs.getString("learning", "")?.trim().orEmpty()
            if (learning.isNotBlank()) {
                append("APRENDIZAJES DE INTERACCIÓN:\n")
                append(learning.takeLast(3500))
                append("\n\n")
            }
            val recent = history.takeLast(8)
            if (recent.isNotEmpty()) {
                append("CONVERSACIÓN RECIENTE:\n")
                recent.forEach { (author, message) ->
                    append(author).append(": ").append(message.take(1800)).append("\n")
                }
                append("\n")
            }
            selectedFileText?.takeIf { it.isNotBlank() }?.let { fileText ->
                append("ARCHIVO ADJUNTO:\n")
                append(fileText.take(12000))
                append("\n\n")
            }
            if (lastSearchContext.isNotBlank()) {
                append("RESULTADOS DE INTERNET:\n")
                append(lastSearchContext.take(10000))
                append("\n\n")
            }
            append("MENSAJE ACTUAL:\n")
            append(prompt)
            append("\n\nRespondé directamente en español argentino. No inventes datos.")
        }
    }

    private fun searchInternetAndAnswer(query: String) {
        val answer = TextView(this).apply {
            text = "Lix está buscando en Internet..."
            textSize = 15f
            setTextColor(textColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, border)
        }
        chat.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        generation = lifecycleScope.launch(Dispatchers.IO) {
            val web = fetchSearchResults(query)
            lastSearchContext = web
            if (web.startsWith("No pude")) {
                withContext(Dispatchers.Main) {
                    answer.text = web
                    input.isEnabled = true
                    send.isEnabled = true
                }
                return@launch
            }
            val result = StringBuilder()
            var lastUpdate = 0L
            engine.sendUserPrompt(buildContextPrompt(query)).collect { token ->
                result.append(token)
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 70L) {
                    lastUpdate = now
                    val cleaned = cleanAnswer(result.toString())
                    withContext(Dispatchers.Main) {
                        answer.text = if (cleaned.isBlank()) "Lix está procesando la búsqueda..." else cleaned
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                val final = cleanAnswer(result.toString())
                if (final.isNotBlank()) {
                    history.add("LIX" to final)
                    saveHistory()
                    learnFromConversation(query, final)
                    if (prefs.getBoolean("tts", false)) tts.speak(final, TextToSpeech.QUEUE_FLUSH, null, "lix")
                }
                input.isEnabled = true
                send.isEnabled = true
                lastSearchContext = ""
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun fetchSearchResults(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val connection = (URL("https://html.duckduckgo.com/html/?q=$encoded").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Lix)")
                setRequestProperty("Accept-Language", "es-AR,es;q=0.9,en;q=0.6")
            }
            connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val html = reader.readText().take(90000)
                    val cleaned = html
                        .replace(Regex("(?is)<script.*?</script>"), " ")
                        .replace(Regex("(?is)<style.*?</style>"), " ")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&quot;", "\"")
                        .replace("&#x27;", "'")
                        .replace("&amp;", "&")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (cleaned.isBlank()) "No pude obtener resultados de Internet." else cleaned.take(14000)
                }
            }
        } catch (e: Exception) {
            "No pude acceder a Internet ahora: " + (e.message ?: "error de conexión")
        }
    }

    private fun setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    input.setText(text)
                    input.setSelection(input.text.length)
                    input.requestFocus()
                }
            }
            override fun onError(error: Int) { toast("No pude reconocer la voz") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "AR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Hablale a Lix")
        }
        speech?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    private fun showMemory() {
        val mem = prefs.getString("memory", "") ?: ""
        AlertDialog.Builder(this).setTitle("Memoria de Lix")
            .setMessage(if (mem.isBlank()) "No hay recuerdos guardados." else mem)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Borrar memoria") { _, _ -> prefs.edit().remove("memory").apply(); toast("Memoria borrada") }
            .show()
    }

    private fun remember(text: String) {
        val old = prefs.getString("memory", "") ?: ""
        prefs.edit().putString("memory", (old + "\n• " + text).trim()).apply()
    }

    private fun openSettings() {
        val items = arrayOf("Nombre: $profileName", "Leer respuestas en voz alta", "Borrar historial", "Borrar memoria", "Información del modelo")
        AlertDialog.Builder(this).setTitle("Ajustes").setItems(items) { _, which ->
            when (which) {
                0 -> editName()
                1 -> prefs.edit().putBoolean("tts", !prefs.getBoolean("tts", false)).apply().also { toast("Lectura por voz actualizada") }
                2 -> { history.clear(); saveHistory(); chat.removeAllViews(); toast("Historial borrado") }
                3 -> { prefs.edit().remove("memory").apply(); toast("Memoria borrada") }
                4 -> toast("Qwen3 1.7B Q4_K_M • local • sin nube")
            }
        }.show()
    }

    private fun editName() {
        val e = EditText(this).apply { setText(profileName); setSelectAllOnFocus(true) }
        AlertDialog.Builder(this).setTitle("Tu nombre").setView(e)
            .setPositiveButton("Guardar") { _, _ ->
                profileName = e.text.toString().ifBlank { "compa" }
                prefs.edit().putString("profile_name", profileName).apply()
                toast("Perfil actualizado")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun openProfile() {
        AlertDialog.Builder(this).setTitle("Perfil")
            .setMessage("Usuario: $profileName\n\nLix funciona localmente con Qwen3 1.7B.\nTus recuerdos e historial se guardan en el teléfono.")
            .setPositiveButton("Cerrar", null).show()
    }

    private fun showHistory() {
        val lines = history.takeLast(20).joinToString("\n\n") { "${it.first}: ${it.second.take(180)}" }
        AlertDialog.Builder(this).setTitle("Historial")
            .setMessage(if (lines.isBlank()) "Todavía no hay conversaciones." else lines)
            .setPositiveButton("Cerrar", null).show()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.takeLast(50).forEach { (a, m) -> arr.put(JSONObject().apply { put("a", a); put("m", m) }) }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history.add(o.getString("a") to o.getString("m"))
            }
        } catch (_: Exception) {}
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        generation?.cancel()
        speech?.destroy()
        tts.shutdown()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}
