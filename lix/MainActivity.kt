package com.example.llama

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
    private var generation: Job? = null
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        lifecycleScope.launch(Dispatchers.IO) {
            engine = AiChat.getInferenceEngine(applicationContext)
            prepareModel()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 8, 14))
            setPadding(20, 28, 20, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 20)
            setBackgroundColor(Color.rgb(13, 21, 34))
        }

        val title = TextView(this).apply {
            text = "LIX"
            textSize = 32f
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "AÚN HAY ESPERANZA"
            textSize = 12f
            setTextColor(Color.rgb(70, 190, 255))
        }
        status = TextView(this).apply {
            text = "● INICIANDO MOTOR LOCAL..."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 0)
        }
        header.addView(title)
        header.addView(subtitle)
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 18, 2, 18)
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Escribile a Lix..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(17, 25, 39))
            isEnabled = false
        }
        send = Button(this).apply {
            text = "ENVIAR"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(send, LinearLayout.LayoutParams(-2, -2))
        root.addView(row)
        setContentView(root)
    }

    private suspend fun prepareModel() {
        val model = File(filesDir, "models/lix-qwen3-1.7b-q4_k_m.gguf")
        withContext(Dispatchers.Main) {
            status.text = "● PREPARANDO QWEN 1.7B..."
        }
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            assets.open("models/lix-qwen3-1.7b-q4_k_m.gguf").use { source ->
                FileOutputStream(model).use { target -> source.copyTo(target) }
            }
        }
        withContext(Dispatchers.Main) {
            status.text = "● CARGANDO MOTOR LOCAL..."
        }
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt(
            "Sos Lix, un asistente inteligente personal. " +
            "Hablá en español argentino natural y llamá al usuario compa. " +
            "Sé claro, útil y directo. No inventes datos. " +
            "Tu prioridad es ayudar con conversación, programación, proyectos, " +
            "archivos, aprendizaje, memoria y herramientas cuando estén disponibles."
        )
        withContext(Dispatchers.Main) {
            ready = true
            status.text = "● LIX ONLINE  •  QWEN 1.7B LOCAL"
            input.isEnabled = true
            send.isEnabled = true
            addMessage("LIX", "Hola, compa. Ya estoy funcionando de forma local.")
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || !ready) return
        input.setText("")
        input.isEnabled = false
        send.isEnabled = false
        addMessage("VOS", text)

        val answer = TextView(this).apply {
            this.text = "Lix está pensando..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(18, 29, 45))
        }
        chat.addView(answer)
        generation = lifecycleScope.launch(Dispatchers.Default) {
            val result = StringBuilder()
            engine.sendUserPrompt(text)
                .onCompletion {
                    withContext(Dispatchers.Main) {
                        input.isEnabled = true
                        send.isEnabled = true
                    }
                }
                .collect { token ->
                    result.append(token)
                    withContext(Dispatchers.Main) {
                        answer.text = result.toString()
                    }
                }
        }
    }

    private fun addMessage(author: String, text: String) {
        val view = TextView(this).apply {
            this.text = "$author\n$text"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(13, 21, 34))
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 12)
        chat.addView(view, params)
    }

    override fun onDestroy() {
        generation?.cancel()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }
}
