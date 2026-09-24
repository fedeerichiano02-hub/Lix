package com.example.llama

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
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
    private var continuousVoice = false
    private var wakeServiceEnabled = false
    private var taskMode = false
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
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        profileName = prefs.getString("profile_name", "compa") ?: "compa"
        loadHistory()
        tts = TextToSpeech(this, TextToSpeech.OnInitListener { if (it == TextToSpeech.SUCCESS) tts.language = Locale("es", "AR") })
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                if (utteranceId == "lix" && continuousVoice) {
                    runOnUiThread { Handler(Looper.getMainLooper()).postDelayed({ if (continuousVoice) startVoice() }, 350) }
                }
            }
            override fun onError(utteranceId: String) {}
        })
        buildUi()
        setupSpeech()
        wakeServiceEnabled = prefs.getBoolean("wake_enabled", false)
        if (wakeServiceEnabled) requestVoiceCapabilityIfNeeded()
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
            setPadding(dp(16), dp(10), dp(16), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 5, 18), Color.rgb(7, 12, 38), Color.rgb(25, 6, 42))
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LogoView(this, 34), LinearLayout.LayoutParams(dp(116), dp(58)))
        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val search = glowButton("⌕", 48)
        search.setOnClickListener { input.requestFocus() }
        header.addView(search, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        val menu = glowButton("☰", 48)
        menu.setOnClickListener { openMenu() }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Tu asistente personal"
            textSize = 12f
            setTextColor(Color.rgb(190, 205, 235))
            setPadding(dp(3), 0, 0, dp(8))
        })

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(90))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val greeting = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        greeting.addView(OrbView(this, 54), LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(10) })
        val bubble = TextView(this).apply {
            text = "Hola, compa.\n¿En qué puedo ayudarte hoy?\n\n23:14"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(14), dp(12))
            background = gradientCard(GradientDrawable.Orientation.TL_BR,
                Color.argb(220, 25, 75, 150), Color.argb(225, 94, 35, 143), 22,
                Color.argb(210, 67, 157, 255))
        }
        greeting.addView(bubble, LinearLayout.LayoutParams(0, dp(118), 1f))
        chat.addView(greeting, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val actions = arrayOf(
            "⌕   Buscar en Internet",
            "▣   Analizar un archivo",
            "✦   Crear una imagen",
            "▰   Ayudarme con un proyecto"
        )
        actions.forEachIndexed { i, text ->
            val b = TextView(this).apply {
                this.text = text
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(14), 0)
                background = gradientCard(GradientDrawable.Orientation.TL_BR,
                    if (i == 3) Color.argb(190, 36, 55, 120) else Color.argb(165, 19, 28, 65),
                    if (i == 3) Color.argb(205, 78, 74, 177) else Color.argb(150, 31, 49, 104),
                    22, Color.argb(150, 85, 129, 245))
                setOnClickListener {
                    when (i) {
                        0 -> showReferenceScreen("internet")
                        1 -> showReferenceScreen("files")
                        2 -> pickImageForVision()
                        3 -> { input.setText("Ayudame con mi proyecto: "); input.requestFocus() }
                    }
                }
            }
            chat.addView(b, LinearLayout.LayoutParams(dp(if (i == 3) 250 else 230), dp(50)).apply {
                leftMargin = dp(58); bottomMargin = dp(8)
            })
        }
        chat.addView(TextView(this).apply {
            text = "+   Más opciones"
            textSize = 10f
            setTextColor(Color.rgb(165, 177, 207))
            setPadding(dp(60), dp(5), 0, dp(8))
            setOnClickListener { openMenu() }
        })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val plus = glowButton("+", 52)
        plus.setOnClickListener { showReferenceScreen("files") }
        composer.addView(plus, LinearLayout.LayoutParams(dp(52), dp(58)).apply { marginEnd = dp(7) })
        input = EditText(this).apply {
            hint = "Escribe un mensaje..."
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(118, 135, 173))
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), 0, dp(8), 0)
            background = gradientCard(GradientDrawable.Orientation.LEFT_RIGHT,
                Color.argb(160, 10, 20, 50), Color.argb(185, 17, 18, 49), 22,
                Color.argb(145, 69, 112, 219))
            isEnabled = false
        }
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(58), 1f))
        val voice = glowButton("♩", 52)
        voice.setTextColor(Color.rgb(83, 220, 255))
        voice.setOnClickListener { startVoice() }
        composer.addView(voice, LinearLayout.LayoutParams(dp(52), dp(58)).apply { marginStart = dp(7) })
        send = glowButton("↑", 56)
        send.textSize = 23f
        send.setOnClickListener { sendMessage() }
        composer.addView(send, LinearLayout.LayoutParams(dp(56), dp(58)).apply { marginStart = dp(7) })

        root.addView(composer, LinearLayout.LayoutParams(-1, dp(66)))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = gradientCard(GradientDrawable.Orientation.TL_BR,
                Color.argb(135, 6, 11, 31), Color.argb(150, 15, 8, 35), 20,
                Color.argb(100, 54, 84, 154))
        }
        val navs = arrayOf("⌂\nInicio", "☷\nHerramientas", "●\nLix", "◴\nHistorial", "♙\nPerfil")
        navs.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n
                textSize = if (i == 2) 10f else 8f
                setTextColor(if (i == 2) Color.rgb(73, 225, 255) else Color.rgb(132, 145, 180))
                gravity = Gravity.CENTER
                setOnClickListener {
                    when (i) {
                        0 -> {}
                        1 -> openMenu()
                        2 -> input.requestFocus()
                        3 -> showReferenceScreen("history")
                        4 -> showReferenceScreen("settings")
                    }
                }
            }
            nav.addView(t, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(true)
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scroll.postDelayed({ scroll.fullScroll(View.FOCUS_DOWN) }, 120)
            }
        }
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = window.decorView.rootView.height - visible.bottom
            val keyboardOpen = keyboardHeight > dp(180)
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            if (input.hasFocus()) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        window.decorView.postDelayed({ showSplashOverlay() }, 120)
    }

    private fun gradientCard(o: GradientDrawable.Orientation, c1: Int, c2: Int, radius: Int, stroke: Int): GradientDrawable =
        GradientDrawable(o, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun glowButton(symbol: String, size: Int): TextView = TextView(this).apply {
        text = symbol
        textSize = if (size >= 56) 22f else 20f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = gradientCard(GradientDrawable.Orientation.TL_BR,
            Color.argb(130, 14, 26, 61), Color.argb(155, 25, 12, 52), 20,
            Color.argb(145, 73, 115, 225))
    }

    private fun showSplashOverlay() {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(2, 5, 18))
            elevation = dp(20).toFloat()
        }
        val glow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(2, 6, 22), Color.rgb(7, 16, 48), Color.rgb(30, 5, 47))
            )
        }
        glow.addView(LogoView(this, 92), LinearLayout.LayoutParams(-1, dp(190)))
        glow.addView(TextView(this).apply {
            text = "Tu asistente, siempre contigo"
            textSize = 11f
            setTextColor(Color.rgb(194, 205, 232))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(35)))
        val bar = TextView(this).apply {
            text = "━━━━━━        "
            textSize = 13f
            setTextColor(Color.rgb(55, 224, 255))
            gravity = Gravity.CENTER
        }
        glow.addView(bar, LinearLayout.LayoutParams(-1, dp(45)))
        glow.addView(TextView(this).apply {
            text = "Cargando..."
            textSize = 10f
            setTextColor(Color.rgb(158, 172, 205))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))
        overlay.addView(glow, FrameLayout.LayoutParams(-1, -1))
        (window.decorView as ViewGroup).addView(overlay, ViewGroup.LayoutParams(-1, -1))
        overlay.postDelayed({
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }, 1500)
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
            // La pantalla principal ya tiene su saludo visual; no agregamos una burbuja histórica al cargar.
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

        if (LixMegaModules.handle(this, prompt)) {\n            input.setText("")\n            return\n        }\n\n        if (runPhoneAction(prompt)) {
            input.setText("")
            speakLix("Listo, compa.")
            return
        }
        if (LixAutomation.handle(this, prompt)) {
            input.setText("")
            return
        }

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

        taskMode = qIsTaskCommand(prompt)
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
                            if (prefs.getBoolean("tts", false) || continuousVoice) speakLix(final)
                            if (taskMode) {
                                announceTaskCompletion("Compa, terminé la tarea que me pediste.")
                                taskMode = false
                            }
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

    private fun qIsTaskCommand(prompt: String): Boolean {
        val q = prompt.lowercase(Locale("es","AR"))
        return q.contains("haceme") || q.contains("hacé") || q.contains("creame") || q.contains("creá") ||
            q.contains("modificá") || q.contains("modifica") || q.contains("preparame") || q.contains("prepará") ||
            q.contains("investigá") || q.contains("analizá")
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


    private inner class LogoView(context: android.content.Context, private val logoSize: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.textSize = dp(logoSize).toFloat()
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            paint.shader = LinearGradient(0f, 0f, w, h, Color.WHITE, Color.rgb(70, 210, 255), Shader.TileMode.CLAMP)
            paint.setShadowLayer(dp(12).toFloat(), 0f, 0f, Color.rgb(75, 90, 255))
            canvas.drawText("Lix", dp(8).toFloat(), h * .70f, paint)
            paint.shader = null
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.rgb(47, 221, 255)
            if (logoSize >= 80) {                val r = RectF(dp(20).toFloat(), h*.20f, w-dp(20).toFloat(), h*.82f)
                paint.strokeWidth = dp(5).toFloat()
                paint.color = Color.rgb(47, 221, 255)
                canvas.drawArc(r, 205f, 165f, false, paint)
                paint.color = Color.rgb(181, 62, 255)
                canvas.drawArc(r, 20f, 125f, false, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(w*.48f, h*.29f, dp(4).toFloat(), paint)
            }
        }
    }

    private inner class OrbView(context: android.content.Context, private val size: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width/2f
            val cy = height/2f
            paint.shader = RadialGradient(cx, cy, dp(size/2).toFloat(),
                intArrayOf(Color.WHITE, Color.rgb(76, 142, 255), Color.rgb(181, 62, 255), Color.TRANSPARENT),
                floatArrayOf(0f, .25f, .72f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, dp(size/2).toFloat(), paint)
            paint.shader = null
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, dp(6).toFloat(), paint)
        }
    }

    private fun pill(textValue: String, selected: Boolean): TextView = TextView(this).apply {
        text = textValue
        textSize = 9f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = gradientCard(GradientDrawable.Orientation.TL_BR,
            if (selected) Color.rgb(76, 99, 255) else Color.argb(100, 14, 22, 52),
            if (selected) Color.rgb(175, 60, 244) else Color.argb(120, 30, 12, 56),
            18, Color.argb(130, 88, 125, 236))
        setPadding(dp(10), 0, dp(10), 0)
    }

    private fun screenCard(title: String, description: String, icon: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(9), dp(8), dp(9), dp(8))
        background = gradientCard(GradientDrawable.Orientation.TL_BR,
            Color.argb(150, 18, 29, 70), Color.argb(155, 47, 15, 70), 18, Color.argb(105, 82, 113, 220))
        addView(TextView(this@MainActivity).apply {
            text = icon; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.rgb(83, 218, 255))
        }, LinearLayout.LayoutParams(dp(46), dp(56)))
        val texts = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this@MainActivity).apply { text = title; textSize = 12f; setTextColor(Color.WHITE) })
        texts.addView(TextView(this@MainActivity).apply { text = description; textSize = 9f; setTextColor(Color.rgb(164, 180, 216)); setPadding(0, dp(3), 0, 0) })
        addView(texts, LinearLayout.LayoutParams(0, dp(56), 1f))
        addView(TextView(this@MainActivity).apply { text = "›"; textSize = 22f; setTextColor(Color.rgb(180, 193, 225)); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(24), dp(56)))
    }

    private fun screenDialog(title: String, subtitle: String, body: LinearLayout): Dialog {
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 7, 23), Color.rgb(12, 8, 38), Color.rgb(26, 5, 40))).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.argb(170, 79, 108, 225))
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply { text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { dialog.dismiss() } }
        header.addView(back, LinearLayout.LayoutParams(dp(38), dp(48)))
        header.addView(LogoView(this, 27), LinearLayout.LayoutParams(dp(78), dp(48)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD) })
        titles.addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 8.5f; setTextColor(Color.rgb(120, 218, 255)) })
        header.addView(titles, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(TextView(this).apply { text = "⋮"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(dp(30), dp(48)))
        root.addView(header)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .96f).toInt(), -1)
        return dialog
    }

    private fun showReferenceScreen(which: String) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(8), dp(2), 0) }
        when (which) {
            "internet" -> {
                val search = EditText(this).apply {
                    hint = "Buscar en Internet..."
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.rgb(135, 151, 188))
                    setSingleLine(true)
                    setPadding(dp(15), 0, dp(12), 0)
                    background = gradientCard(GradientDrawable.Orientation.TL_BR, Color.argb(160, 19, 25, 65), Color.argb(170, 40, 14, 65), 20, Color.argb(150, 94, 110, 235))
                }
                body.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
                val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(10)) }
                arrayOf("Todo", "Noticias", "Imágenes", "Videos").forEachIndexed { i, value -> tabs.addView(pill(value, i == 0), LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }) }
                body.addView(tabs)
                body.addView(TextView(this).apply { text = "Resultados"; textSize = 16f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(4), dp(5), 0, dp(9)) })
                body.addView(screenCard("Información actualizada", "Lix encontró información reciente en la web.", "◉"), LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
                body.addView(screenCard("Noticias", "Últimas noticias relevantes.", "▣"), LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
                body.addView(screenCard("Fuentes confiables", "Resultados de sitios verificados.", "▤"), LinearLayout.LayoutParams(-1, dp(76)))
                val dialog = screenDialog("Internet", "BUSCAR EN LA WEB", body)
                search.setOnEditorActionListener { _, _, _ ->
                    internetMode = true
                    input.setText(search.text.toString())
                    dialog.dismiss()
                    sendMessage()
                    true
                }
            }
            "files" -> {
                arrayOf("Recientes", "Documentos", "Imágenes", "Videos").forEachIndexed { i, value ->
                    if (i == 0) body.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }, LinearLayout.LayoutParams(0, 0))
                }
                val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                arrayOf("Recientes", "Documentos", "Imágenes", "Videos").forEachIndexed { i, value -> tabs.addView(pill(value, i == 0), LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }) }
                body.addView(tabs)
                body.addView(TextView(this).apply { text = "Archivos recientes"; textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(4), dp(13), 0, dp(9)) })
                val names = arrayOf("Notas del proyecto.txt", "Mapa.jpg", "Ideas_Lix.txt", "Referencia.png", "Plan.txt")
                val times = arrayOf("Hace 2 horas", "Hace 5 horas", "Hace 1 día", "Hace 2 días", "Hace 3 días")
                for (i in names.indices) body.addView(screenCard(names[i], times[i], "▣"), LinearLayout.LayoutParams(-1, dp(69)).apply { bottomMargin = dp(7) })
                val open = TextView(this).apply { text = "Abrir archivo del teléfono"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = gradientCard(GradientDrawable.Orientation.TL_BR, Color.rgb(54, 106, 242), Color.rgb(170, 55, 236), 20, Color.argb(130, 120, 145, 255)); setOnClickListener { pickFile() } }
                body.addView(open, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
                screenDialog("Archivos", "TUS ARCHIVOS", body)
            }
            "memory" -> {
                val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                arrayOf("Todo", "Preferencias", "Proyectos", "Conversaciones").forEachIndexed { i, value -> tabs.addView(pill(value, i == 0), LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }) }
                body.addView(tabs)
                val items = arrayOf("Información sobre vos" to "Datos personales y preferencias." to "♙", "Tus proyectos" to "Ideas, planes y desarrollo." to "◇", "Cosas importantes" to "Recordatorios y notas clave." to "▮", "Aprendizajes" to "Lo que Lix ha aprendido de vos." to "✦", "Conversaciones" to "Historial organizado." to "☁")
                items.forEach { item -> body.addView(screenCard(item.first.first, item.first.second, item.second), LinearLayout.LayoutParams(-1, dp(71)).apply { bottomMargin = dp(7) }) }
                val mem = prefs.getString("memory", "").orEmpty()
                if (mem.isNotBlank()) body.addView(TextView(this).apply { text = mem.take(1000); textSize = 9.5f; setTextColor(Color.rgb(180, 194, 225)); setPadding(dp(10), dp(8), dp(10), dp(8)) })
                screenDialog("Memoria", "TU MEMORIA PERSONAL", body)
            }
            "settings" -> {
                val items = arrayOf(
                    "Tema visual" to "Colores, estilo y apariencia." to "◉",
                    "Voz de Lix" to "Activación, conversación continua y voz." to "≋",
                    "Modelo de IA" to "Qwen local y procesamiento en el dispositivo." to "▦",
                    "Permisos de Lix" to "Archivos, micrófono, ubicación, SMS y control del teléfono." to "♙",
                    "Proyecto Godot" to "Elegí la carpeta que Lix puede leer y modificar." to "◇",
                    "Asistente del sistema" to "Configurar Lix como asistente de Android." to "◎",
                    "Privacidad" to "Datos y almacenamiento local." to "♙",
                    "Notificaciones" to "Alertas y finalización de tareas." to "♧",
                    "Actualizaciones" to "Versión, estado y comprobación." to "☁",
                    "Evolución y mejoras" to "Buscar e instalar mejoras modulares de Lix." to "🧬"
                )
                items.forEach { item ->
                    val card = screenCard(item.first.first, item.first.second, item.second)
                    card.setOnClickListener {
                        when (item.first.first) {
                            "Voz de Lix" -> {
                                wakeServiceEnabled = !wakeServiceEnabled
                                prefs.edit().putBoolean("wake_enabled", wakeServiceEnabled).apply()
                                if (wakeServiceEnabled) requestVoiceCapabilityIfNeeded() else stopWakeService()
                                toast(if (wakeServiceEnabled) "Activación por voz activada." else "Activación por voz desactivada.")
                            }
                            "Proyecto Godot" -> LixAutomation.handle(this, "elegir proyecto Godot")
                            "Asistente del sistema" -> openAssistantRoleSettings()
                            "Evolución y mejoras" -> showEvolution()
                            "Permisos de Lix" -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                        }
                    }
                    body.addView(card, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
                }
                screenDialog("Configuración", "CONTROL TOTAL DE LIX", body)
            }
            "history" -> {
                history.takeLast(12).reversed().forEach { pair -> body.addView(screenCard(pair.first, pair.second.take(90), "●"), LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(7) }) }
                if (history.isEmpty()) body.addView(TextView(this).apply { text = "Todavía no hay conversaciones guardadas."; textSize = 12f; setTextColor(Color.rgb(170, 185, 215)); setPadding(dp(12), dp(20), dp(12), dp(20)) })
                screenDialog("Historial", "CONVERSACIONES RECIENTES", body)
            }
            "voice" -> {
                body.gravity = Gravity.CENTER
                body.addView(OrbView(this, 180), LinearLayout.LayoutParams(dp(180), dp(180)))
                body.addView(TextView(this).apply { text = "Escuchando..."; textSize = 19f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(18), 0, dp(2)) }, LinearLayout.LayoutParams(-1, dp(45)))
                body.addView(TextView(this).apply { text = "Podés hablar ahora"; textSize = 10f; setTextColor(Color.rgb(159,178,218)); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(30)))
                val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
                controls.addView(glowButton("⌨", 52), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(20) })
                val stop = glowButton("■", 62)
                stop.background = gradientCard(GradientDrawable.Orientation.TL_BR, Color.rgb(255,78,112), Color.rgb(245,62,135), 28, Color.argb(160,255,145,175))
                controls.addView(stop, LinearLayout.LayoutParams(dp(62), dp(62)))
                controls.addView(glowButton("×", 52), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(20) })
                body.addView(controls, LinearLayout.LayoutParams(-1, dp(75)).apply { topMargin = dp(24) })
                val dialog = screenDialog("Voz", "HABLÁ CON LIX", body)
                stop.setOnClickListener { speech?.stopListening(); dialog.dismiss() }
                startVoice()
            }
        }
    }

    private fun showEvolution() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), 0)
        }
        val status = TextView(this).apply {
            text = "Buscando mejoras disponibles..."
            textSize = 10f
            setTextColor(Color.rgb(150, 180, 220))
            setPadding(dp(8), dp(2), dp(8), dp(12))
        }
        body.addView(status)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        fun render(items: List<LixEvolution.Improvement>) {
            list.removeAllViews()
            status.text = if (items.isEmpty()) "No hay mejoras disponibles." else "Mejoras disponibles"
            items.forEach { item ->
                val installed = LixEvolution.installedVersion(this, item.id)
                val card = screenCard(item.title, item.description + "  •  v" + item.version, "✦")
                card.setOnClickListener {
                    if (LixEvolution.installImprovement(this, item)) {
                        toast(item.title + " activada.")
                        card.alpha = 0.65f
                    }
                }
                if (installed >= item.version) card.alpha = 0.55f
                list.addView(card, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) })
            }
        }

        render(LixEvolution.catalog(this))
        val refresh = glowButton("↻  BUSCAR MEJORAS", 46)
        refresh.setOnClickListener {
            refresh.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val result = try { LixEvolution.refresh(this@MainActivity) } catch (_: Exception) { emptyList() }
                withContext(Dispatchers.Main) {
                    refresh.isEnabled = true
                    if (result.isEmpty()) toast("No se pudo consultar el catálogo ahora.")
                    render(if (result.isEmpty()) LixEvolution.catalog(this@MainActivity) else result)
                }
            }
        }
        body.addView(refresh, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(4) })
        screenDialog("Evolución", "MEJORAS DE LIX", body)
    }

    private fun openMenu() {
        val d=Dialog(this)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(18),dp(16),dp(18))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3,7,22),Color.rgb(13,8,37))).apply{cornerRadius=dp(30).toFloat();setStroke(dp(1),Color.argb(170,72,113,230))}
        }
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(LogoView(this,34),LinearLayout.LayoutParams(0,dp(62),1f))
        val x=glowButton("×",48);x.setOnClickListener{d.dismiss()};head.addView(x,LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(head)
        root.addView(TextView(this).apply{text="Tu asistente personal";textSize=10f;setTextColor(Color.rgb(76,220,255));setPadding(dp(5),0,0,dp(14))})
        val entries=arrayOf("Chat" to "▢","Internet" to "◎","Memoria" to "◉","Archivos" to "□","Proyectos" to "◇","Voz" to "≋","Personalización" to "♙","Configuración" to "⚙")
        entries.forEachIndexed{idx,(title,icon)->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(13),0,dp(12),0)
                background=if(idx==0) gradientCard(GradientDrawable.Orientation.TL_BR,Color.rgb(73,70,210),Color.rgb(164,50,229),20,Color.argb(150,117,118,255)) else gradientCard(GradientDrawable.Orientation.TL_BR,Color.argb(100,13,22,53),Color.argb(115,31,12,55),20,Color.argb(80,78,110,206))
                setOnClickListener {
                    d.dismiss()
                    when (title) {
                        "Chat" -> {}
                        "Internet" -> showReferenceScreen("internet")
                        "Memoria" -> showReferenceScreen("memory")
                        "Archivos" -> showReferenceScreen("files")
                        "Proyectos" -> { input.setText("Ayudame con mi proyecto: "); input.requestFocus() }
                        "Voz" -> showReferenceScreen("voice")
                        "Personalización", "Configuración" -> showReferenceScreen("settings")
                    }
                }
            }
            row.addView(TextView(this).apply{text=icon;textSize=19f;setTextColor(Color.rgb(88,223,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(40),dp(52)))
            row.addView(TextView(this).apply{text=title;textSize=12.5f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,dp(52),1f))
            row.addView(TextView(this).apply{text="›";textSize=21f;setTextColor(Color.rgb(177,191,223));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(24),dp(52)))
            root.addView(row,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(7)})
        }
        root.addView(LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));background=gradientCard(GradientDrawable.Orientation.TL_BR,Color.argb(145,10,55,77),Color.argb(135,45,17,74),20,Color.argb(130,58,180,235)).also{
            addView(TextView(this@MainActivity).apply{text="●";textSize=16f;setTextColor(Color.rgb(72,235,128))},LinearLayout.LayoutParams(dp(28),dp(42)))
            addView(TextView(this@MainActivity).apply{text="Lix\n• En línea";textSize=10f;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,dp(42),1f))
        }},LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(8)})
        d.setContentView(root);d.setCanceledOnTouchOutside(true);d.show();d.window?.setGravity(Gravity.START);d.window?.setLayout((resources.displayMetrics.widthPixels*.78).toInt(),-1)
    }

    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4201 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString(LixAutomation.PREF_TREE_URI, uri.toString()).apply()
                toast("Proyecto Godot autorizado para Lix.")
                speakLix("Listo, compa. Ya tengo acceso autorizado a esa carpeta.")
            }
            return
        }
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let { analyzeImage(it) }
            return
        }
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
                    if (prefs.getBoolean("tts", false) || continuousVoice) speakLix(final)
                    if (taskMode) {
                        announceTaskCompletion("Compa, terminé la tarea que me pediste.")
                        taskMode = false
                    }
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
                    if (continuousVoice) {
                        input.setText(text)
                        sendMessage()
                    } else {
                        input.setText(text)
                        input.setSelection(input.text.length)
                        input.requestFocus()
                    }
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

    private fun speakLix(text: String) {
        if (text.isBlank()) return
        tts.setPitch(0.92f)
        tts.setSpeechRate(0.96f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lix")
    }

    private fun announceTaskCompletion(message: String) {
        try {
            startService(Intent(this, LixTaskService::class.java).putExtra("task_message", message))
        } catch (_: Exception) {
            speakLix(message)
        }
    }

    private fun requestVoiceCapabilityIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
        } else {
            startWakeService()
        }
    }

    private fun startWakeService() {
        if (!wakeServiceEnabled) return
        try {
            val i = Intent(this, LixWakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (_: Exception) {
            toast("Android bloqueó la activación de voz en segundo plano.")
        }
    }

    private fun stopWakeService() {
        stopService(Intent(this, LixWakeService::class.java))
    }

    private fun openAssistantRoleSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT), 4301)
                    return
                }
            }
        } catch (_: Exception) {}
        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    }

    private fun runPhoneAction(command: String): Boolean {
        val q = command.lowercase(Locale("es","AR"))
        val service = LixAccessibilityService.instance ?: return false
        return when {
            q.contains("andá al inicio") || q.contains("ir al inicio") -> service.goHome()
            q.contains("volvé atrás") || q.contains("volver atrás") -> service.goBack()
            q.contains("abrí las notificaciones") -> service.openNotifications()
            q.contains("mostrá las aplicaciones recientes") || q.contains("abrí recientes") -> service.openRecents()
            else -> false
        }
    }

    private fun pickImageForVision() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 1002)
    }

    private fun analyzeImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val image = InputImage.fromFilePath(this@MainActivity, uri)
                val textTask = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                val labelTask = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(image)
                val textResult = com.google.android.gms.tasks.Tasks.await(textTask)
                val labels = com.google.android.gms.tasks.Tasks.await(labelTask)
                val ocr = textResult.text.trim()
                val tags = labels.take(8).joinToString(", ") { it.text }
                withContext(Dispatchers.Main) {
                    val report = buildString {
                        append("Análisis visual de Lix:\n")
                        append("Objetos/conceptos detectados: ").append(if (tags.isBlank()) "sin etiquetas claras" else tags).append("\n")
                        append("Texto detectado:\n").append(if (ocr.isBlank()) "No encontré texto legible." else ocr.take(5000))
                    }
                    addMessage("LIX", report)
                    speakLix("Compa, terminé el análisis de la imagen.")
                }
            } catch (err: Exception) {
                withContext(Dispatchers.Main) { toast("No pude analizar esa imagen: " + (err.message ?: "error")) }
            }
        }
    }

    private fun premiumPanel(title: String, subtitle: String, content: String, actionText: String = "Cerrar", action: (() -> Unit)? = null) {
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(20)); background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(4,7,20), Color.rgb(20,7,38))).apply { cornerRadius = dp(26).toFloat(); setStroke(dp(1), Color.argb(150,91,133,221)) } }
        root.addView(TextView(this).apply { text=title; textSize=25f; setTextColor(Color.rgb(244,248,255)); setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text=subtitle; textSize=11f; setTextColor(Color.rgb(70,222,255)); setPadding(0,dp(4),0,dp(18)) })
        root.addView(TextView(this).apply { text=content; textSize=14f; setTextColor(Color.rgb(205,216,235)); setPadding(0,0,0,dp(18)) })
        val b=TextView(this).apply { text=actionText; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(72,112,255),Color.rgb(174,82,255))).apply { cornerRadius=dp(18).toFloat() }; setOnClickListener { action?.invoke(); dialog.dismiss() } }
        root.addView(b,LinearLayout.LayoutParams(-1,dp(50)))
        dialog.setContentView(root); dialog.setCanceledOnTouchOutside(true); dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.90).toInt(),-2)
    }
    private fun startVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
            return
        }
        if (speech == null) setupSpeech()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "AR"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Hablale a Lix")
        }
        try { speech?.startListening(intent) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    private fun showMemory() { showReferenceScreen("memory") }

    private fun remember(text: String) {
        val old = prefs.getString("memory", "") ?: ""
        prefs.edit().putString("memory", (old + "\n• " + text).trim()).apply()
    }

    private fun openSettings() { showReferenceScreen("settings") }

    private fun editName() {
        val e = EditText(this).apply { setText(profileName); setSelectAllOnFocus(true) }
        AlertDialog.Builder(this).setTitle("Tu nombre").setView(e)
            .setPositiveButton("Guardar") { _, _ ->
                profileName = e.text.toString().ifBlank { "compa" }
                prefs.edit().putString("profile_name", profileName).apply()
                toast("Perfil actualizado")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun openProfile() { showReferenceScreen("settings") }

    private fun showHistory() { showReferenceScreen("history") }

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
        continuousVoice = false
        stopWakeService()
        generation?.cancel()
        speech?.destroy()
        tts.shutdown()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }

}