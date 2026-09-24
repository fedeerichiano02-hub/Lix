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
import android.graphics.Shader
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
        val spacer = Space(this)
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
                background = gradientCard(GradientDrawable.Orientation.LT_BR,
                    if (i == 3) Color.argb(190, 36, 55, 120) else Color.argb(165, 19, 28, 65),
                    if (i == 3) Color.argb(205, 78, 74, 177) else Color.argb(150, 31, 49, 104),
                    22, Color.argb(150, 85, 129, 245))
                setOnClickListener {
                    when (i) {
                        0 -> showReferenceScreen("internet")
                        1 -> showReferenceScreen("files")
                        2 -> toast("La generación de imágenes se conecta en la siguiente etapa.")
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
                setOnClickListener{d.dismiss();when(title){"Chat"->{}, "Internet"->showReferenceScreen("internet"), "Memoria"->showReferenceScreen("memory"), "Archivos"->showReferenceScreen("files"), "Proyectos"->{input.setText("Ayudame con mi proyecto: ");input.requestFocus()}, "Voz"->showReferenceScreen("voice"), "Personalización"->showReferenceScreen("settings"), "Configuración"->showReferenceScreen("settings")}}
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
        speech?.startListening(intent)
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
        generation?.cancel()
        speech?.destroy()
        tts.shutdown()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}
