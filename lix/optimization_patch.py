from pathlib import Path

main = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = main.read_text()

# Use the smaller Qwen3 model for much faster on-device responses on the POCO C65.
s = s.replace("models/lix-qwen3-1.7b-q4_k_m.gguf", "models/lix-qwen3-0.6b-q4_0.gguf")

s = s.replace(
'''            val recent = history.takeLast(8)
            if (recent.isNotEmpty()) {
                append("CONVERSACIÓN RECIENTE:\\n")
                recent.forEach { (author, message) ->
                    append(author).append(": ").append(message.take(1800)).append("\\n")
                }
                append("\\n")
            }
''',
'''            // Conversation state is retained by the native engine; don't duplicate it.
'''
)
s = s.replace('append(memory.take(3500))', 'append(memory.takeLast(1000))')
s = s.replace('append(learning.takeLast(3500))', 'append(learning.takeLast(900))')
s = s.replace('append(fileText.take(12000))', 'append(fileText.take(2500))')
s = s.replace('append(lastSearchContext.take(10000))', 'append(lastSearchContext.take(3500))')
s = s.replace(
    'val enriched = buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)',
    'val enriched = (buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)).take(4500)'
)
s = s.replace(
    'append(prompt)\n            append("\\n\\nRespondé directamente en español argentino. No inventes datos.")',
    'append("/no_think\\n")\n            append(prompt)\n            append("\\n\\nRespondé directamente en español argentino. No inventes datos.")'
)
s = s.replace('engine.sendUserPrompt(enriched)', 'engine.sendUserPrompt(enriched, 160)')
s = s.replace('engine.sendUserPrompt(buildContextPrompt(query))', 'engine.sendUserPrompt(buildContextPrompt(query), 160)')
s = s.replace('now - lastUiUpdate >= 70L', 'now - lastUiUpdate >= 50L')

old_keyboard = '''        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = window.decorView.rootView.height - visible.bottom
            val keyboardOpen = keyboardHeight > dp(180)
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            if (input.hasFocus()) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }'''
new_keyboard = '''        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val keyboardOpen = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            composer.translationY = 0f
            scroll.setPadding(0, 0, 0, dp(90))
            if (keyboardOpen && input.hasFocus()) {
                scroll.post {
                    scroll.fullScroll(View.FOCUS_DOWN)
                    input.requestLayout()
                }
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        '''

main.write_text(s)

stage = Path("build-app/app/src/main/java/com/example/llama/LixStage1Core.kt")
if stage.exists():
    s = stage.read_text()
    s = s.replace(
        '''        val memories = readItems(c, MEM).takeLast(12)
        val lessons = readItems(c, LESSONS).takeLast(12)''',
        '''        val memories = readItems(c, MEM).takeLast(4).map { it.take(250) }
        val lessons = readItems(c, LESSONS).takeLast(4).map { it.take(250) }'''
    )
    stage.write_text(s)

cpp = Path("build-app/lib/src/main/cpp/ai_chat.cpp")
s = cpp.read_text()
s = s.replace('constexpr int   DEFAULT_CONTEXT_SIZE    = 2048;', 'constexpr int   DEFAULT_CONTEXT_SIZE    = 1536;')
s = s.replace('constexpr int   N_THREADS_MAX          = 2;', 'constexpr int   N_THREADS_MAX          = 4;')
s = s.replace('constexpr int   BATCH_SIZE              = 128;', 'constexpr int   BATCH_SIZE              = 256;')
cpp.write_text(s)
