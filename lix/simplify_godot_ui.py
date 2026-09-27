from pathlib import Path
import re

p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
s = p.read_text()
start = s.index('    private fun buildUi() {')
end = s.index('    private fun gradientCard(', start)

new_ui = r'''    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(Color.rgb(4, 9, 20))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LogoView(this, 34), LinearLayout.LayoutParams(dp(90), dp(52)))
        header.addView(TextView(this).apply {
            text = "GODOT"
            textSize = 10f
            setTextColor(Color.rgb(80, 210, 255))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        val menu = glowButton("☰", 46)
        menu.setOnClickListener { openMenu() }
        header.addView(menu, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header)

        status = TextView(this).apply {
            text = if (ready) "Listo para trabajar" else "Cargando Lix..."
            textSize = 10f
            setTextColor(Color.rgb(145, 170, 205))
            setPadding(dp(3), 0, 0, dp(8))
        }
        root.addView(status)

        scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(16))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val title = TextView(this).apply {
            text = "¿Qué hacemos en Godot?"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(2), dp(10), 0, dp(12))
        }
        chat.addView(title)

        fun actionButton(text: String, subtitle: String, action: () -> Unit): TextView = TextView(this).apply {
            this.text = "$text\n$subtitle"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(11), dp(12), dp(8))
            background = rounded(panel2, 16, border)
            setOnClickListener { action() }
        }

        chat.addView(actionButton("🧍  Personaje 3D", "Elegí una imagen y generá un GLB listo para llevar a Godot.") {
            pickImageFor3D()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        chat.addView(actionButton("🎮  Trabajar en el proyecto", "Leer, modificar y preparar el proyecto Godot autorizado.") {
            input.setText("Trabajá en mi proyecto Godot: ")
            input.setSelection(input.text.length)
            input.requestFocus()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        chat.addView(actionButton("📁  Importar asset", "GLB, GLTF, escenas, scripts y recursos del proyecto.") {
            pickFile()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        chat.addView(actionButton("🔍  Analizar imagen", "Vision local para revisar referencias, texturas y documentación visual.") {
            pickImageForVision()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        chat.addView(actionButton("⚙  Programar / corregir", "Pedile a Lix que implemente o repare sistemas de Godot.") {
            input.setText("Implementá en Godot: ")
            input.setSelection(input.text.length)
            input.requestFocus()
        }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })

        chat.addView(TextView(this).apply {
            text = "También podés escribir cualquier pedido abajo. Lix conserva el historial y las tareas persistentes."
            textSize = 9.5f
            setTextColor(Color.rgb(125, 148, 180))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val plus = glowButton("+", 50)
        plus.setOnClickListener { pickFile() }
        composer.addView(plus, LinearLayout.LayoutParams(dp(50), dp(56)).apply { marginEnd = dp(6) })
        input = EditText(this).apply {
            hint = "Pedile algo a Lix..."
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(110, 130, 165))
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            background = rounded(panel, 18, border)
            isEnabled = false
        }
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f))
        val voice = glowButton("🎙", 50)
        voice.setOnClickListener { startVoice() }
        composer.addView(voice, LinearLayout.LayoutParams(dp(50), dp(56)).apply { marginStart = dp(6) })
        send = glowButton("↑", 50)
        send.textSize = 21f
        send.setOnClickListener { sendMessage() }
        composer.addView(send, LinearLayout.LayoutParams(dp(50), dp(56)).apply { marginStart = dp(6) })
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(62)))

        setContentView(root)
        input.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) scroll.postDelayed({ scroll.fullScroll(View.FOCUS_DOWN) }, 100) }
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = window.decorView.rootView.height - visible.bottom
            if (keyboardHeight > dp(180)) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

'''
s = s[:start] + new_ui + s[end:]

# Add a dedicated image picker for 3D without disturbing the existing vision picker.
needle = '    private fun pickImageForVision() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 1002) }'
replacement = needle + '\n    private fun pickImageFor3D() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 1003) }'
if 'private fun pickImageFor3D()' not in s:
    s = s.replace(needle, replacement)

# Handle the 3D image result before the generic file picker result.
needle2 = '    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {'
insert = '''    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {'''
if 'requestCode == 1003' not in s:
    marker = 'if (requestCode == 1002 && resultCode == RESULT_OK) { data?.data?.let { analyzeImage(it) }; return }'
    add = marker + ' if (requestCode == 1003 && resultCode == RESULT_OK) { data?.data?.let { uri -> LixImageTo3D.start(this, uri, "personaje 3D para Godot") }; return }'
    s = s.replace(marker, add)

p.write_text(s)
'''}