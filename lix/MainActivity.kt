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
        val top = Color.rgb(3, 7, 20)
        val bottom = Color.rgb(18, 7, 34)
        val white = Color.rgb(244, 248, 255)
        val muted = Color.rgb(157, 169, 198)
        val cyan = Color.rgb(70, 222, 255)
        val blue = Color.rgb(72, 112, 255)
        val purple = Color.rgb(174, 82, 255)
        val glass = Color.argb(150, 18, 24, 52)
        val glass2 = Color.argb(185, 20, 27, 58)
        val edge = Color.argb(125, 91, 133, 221)

        fun card(fill: Int, radius: Int = 20, stroke: Int = edge) =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }
        fun txt(v: String, size: Float, color: Int = white) = TextView(this).apply {
            text = v
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }
        fun iconButton(symbol: String) = txt(symbol, 22f, white).apply {
            gravity = Gravity.CENTER
            background = card(Color.argb(90, 24, 31, 61), 18, Color.argb(100, 93, 137, 220))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(7), dp(15), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(top, Color.rgb(7, 10, 29), bottom)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "Lix"
            textSize = 29f
            setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setShadowLayer(dp(12).toFloat(), 0f, 0f, Color.rgb(67, 111, 255))
        }
        header.addView(logo, LinearLayout.LayoutParams(0, dp(54), 1f))
        val search = iconButton("⌕")
        search.setOnClickListener { input.requestFocus() }
        header.addView(search, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(7) })
        val menu = iconButton("☰")
        menu.setOnClickListener { openMenu() }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val subtitle = txt("Tu asistente personal", 10.5f, muted)
        subtitle.setPadding(dp(2), 0, 0, dp(6))
        root.addView(subtitle)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(10))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val greetingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val orb = txt("✦", 20f, white).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, purple)
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        greetingRow.addView(orb, LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginEnd = dp(10) })

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(170, 34, 66, 128), Color.argb(185, 72, 35, 111))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(150, 81, 153, 255))
            }
        }
        bubble.addView(txt("Hola, compa.", 15f))
        bubble.addView(txt("¿En qué puedo ayudarte hoy?", 15f).apply { setPadding(0, dp(4), 0, 0) })
        bubble.addView(txt("23:14", 8f, muted).apply {
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        })
        greetingRow.addView(bubble, LinearLayout.LayoutParams(0, -2, 1f))
        chat.addView(greetingRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(18) })

        val actions = arrayOf(
            "◉   Buscar en Internet",
            "▣   Analizar un archivo",
            "✦   Crear una imagen",
            "▰   Ayudarme con un proyecto"
        )
        actions.forEachIndexed { i, action ->
            val b = txt(action, 11.5f, white).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                background = card(
                    if (i == 3) Color.argb(145, 51, 64, 133) else Color.argb(115, 31, 39, 76),
                    20,
                    if (i == 3) Color.argb(160, 103, 103, 255) else Color.argb(90, 92, 128, 211)
                )
                setOnClickListener {
                    when (i) {
                        0 -> toolAction(1)
                        1 -> pickFile()
                        2 -> toast("Generación de imágenes: función pendiente")
                        3 -> toolAction(4)
                    }
                }
            }
            chat.addView(b, LinearLayout.LayoutParams(dp(245), dp(48)).apply {
                gravity = Gravity.START
                bottomMargin = dp(8)
                leftMargin = dp(58)
            })
        }
        chat.addView(txt("＋   Más opciones", 9.5f, muted).apply {
            setPadding(dp(60), dp(6), 0, dp(10))
        })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val plus = iconButton("+")
        plus.setOnClickListener { pickFile() }
        composer.addView(plus, LinearLayout.LayoutParams(dp(48), dp(56)).apply { marginEnd = dp(7) })

        input = EditText(this).apply {
            hint = "Escribe un mensaje..."
            textSize = 13.5f
            setTextColor(white)
            setHintTextColor(Color.rgb(117, 130, 164))
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), 0, dp(8), 0)
            background = card(Color.argb(145, 18, 24, 52), 22, Color.argb(110, 82, 117, 210))
            isEnabled = false
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f))

        val voice = iconButton("♩")
        voice.setTextColor(cyan)
        voice.setOnClickListener { startVoice() }
        composer.addView(voice, LinearLayout.LayoutParams(dp(48), dp(56)).apply { marginStart = dp(7) })

        send = txt("↑", 22f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, purple)
            ).apply { cornerRadius = dp(20).toFloat() }
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(7) })
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(63)).apply {
            topMargin = dp(5)
        })

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = card(Color.argb(130, 10, 15, 34), 22, Color.argb(90, 87, 119, 190))
        }
        arrayOf("⌂\nInicio", "☷\nHerramientas", "●\nLix", "◴\nHistorial", "♙\nPerfil")
            .forEachIndexed { i, n ->
                nav.addView(txt(n, if (i == 2) 9.5f else 8.5f, if (i == 2) cyan else muted).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { bottomAction(i) }
                }, LinearLayout.LayoutParams(0, dp(49), 1f))
            }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(53)))
        setContentView(root)
    }

    private suspend fun prepareModel() {
        val model = File(filesDir, "models/lix-qwen3-1.7b-q4_k_m.gguf")

        if (!model.exists()) {
            model.parentFile?.mkdirs()
            assets.open("models/lix-qwen3-1.7b-q4_k_m.gguf").use { source ->
                FileOutputStream(model).use { target -> source.copyTo(target) }
            }
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
