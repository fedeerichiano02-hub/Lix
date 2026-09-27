from pathlib import Path
import re

p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
s = p.read_text()

new_ui = r'''    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.rgb(12, 14, 18))
        }
        root.addView(TextView(this).apply {
            text = "Lix · Godot"
            textSize = 22f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, dp(40)))
        status = TextView(this).apply {
            text = "Preparando IA..."
            textSize = 12f
            setTextColor(Color.rgb(160, 170, 180))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(28)))
        val character = Button(this).apply {
            text = "Personaje 3D"
            setAllCaps(false)
            setOnClickListener { open3DPicker() }
        }
        root.addView(character, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        val project = Button(this).apply {
            text = "Abrir proyecto Godot"
            setAllCaps(false)
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, 4101)
            }
        }
        root.addView(project, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        scroll = ScrollView(this).apply { isFillViewport = true }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "Decile a Lix qué hacer en Godot..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 140, 150))
            maxLines = 4
            isEnabled = false
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })
        send = Button(this).apply {
            text = "Enviar"
            setOnClickListener { sendMessage() }
        }
        root.addView(send, LinearLayout.LayoutParams(-1, dp(52)))
        setContentView(root)
    }

    private fun open3DPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, 4102)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 4102) LixImageTo3D.start(this, uri, "Personaje 3D para Godot")
    }

'''

pattern = r'    private fun buildUi\(\) \{.*?\n    private fun gradientCard\('
replacement = new_ui + '    private fun gradientCard('
s2, count = re.subn(pattern, replacement, s, flags=re.S)
if count != 1:
    raise SystemExit(f'buildUi replacement failed: {count}')
p.write_text(s2)
print('Focused Godot UI applied')
