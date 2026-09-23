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
    private lateinit var send: Button
    private lateinit var scroll: ScrollView
    private var generation: Job? = null
    private var ready = false
    private var internetMode = false
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
            setOnClickListener { openMenu() }
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
            setOnClickListener { openSettings() }
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
                setOnClickListener { toolAction(index) }
            }
            tools.addView(chip, LinearLayout.LayoutParams(dp(132), dp(50)).apply {
                marginEnd = dp(8)
            })
        }

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
                setOnClickListener { quickAction(action) }
            }
            quick.addView(chip, LinearLayout.LayoutParams(dp(176), dp(45)).apply {
                marginEnd = dp(8)
            })
        }

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
            setOnClickListener { pickFile() }
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
            setOnClickListener { startVoice() }
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
                setOnClickListener { bottomAction(index) }
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

        if (prompt.startsWith("Recordá esto:", true)) {
            remember(prompt.substringAfter(":").trim())
            addMessage("VOS", prompt)
            addMessage("LIX", "Listo, compa. Lo guardé en la memoria de Lix.")
            input.setText("")
            return
        }

        if (prompt.startsWith("¿Qué recordás?", true) || prompt.startsWith("Que recordas", true)) {
            addMessage("VOS", prompt)
            addMessage("LIX", prefs.getString("memory", "")?.ifBlank { "No tengo recuerdos guardados todavía." } ?: "No tengo recuerdos guardados todavía.")
            input.setText("")
            return
        }

        if (internetMode) {
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


    private fun toolAction(index: Int) {
        when (index) {
            0 -> { internetMode = false; toast("Chat local activo") }
            1 -> { internetMode = true; input.hint = "¿Qué querés buscar?"; input.requestFocus(); toast("Modo Internet activo") }
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
                append(memory.take(6000))
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
            engine.sendUserPrompt(buildContextPrompt(query)).collect { token ->
                result.append(token)
                val cleaned = cleanAnswer(result.toString())
                withContext(Dispatchers.Main) {
                    answer.text = if (cleaned.isBlank()) "Lix está procesando la búsqueda..." else cleaned
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }
            withContext(Dispatchers.Main) {
                val final = cleanAnswer(result.toString())
                if (final.isNotBlank()) {
                    history.add("LIX" to final)
                    saveHistory()
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
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Lix)")
                setRequestProperty("Accept-Language", "es-AR,es;q=0.9,en;q=0.6")
            }
            connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val html = reader.readText()
                    val cleaned = html
                        .replace(Regex("(?is)<script.*?</script>"), " ")
                        .replace(Regex("(?is)<style.*?</style>"), " ")
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&quot;", """)
                        .replace("&#x27;", "'")
                        .replace("&amp;", "&")
                        .replace(Regex("\s+"), " ")
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
