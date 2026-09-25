from pathlib import Path

main = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = main.read_text()

# Remove duplicated conversation history from every native prompt.
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

# Compact external context before JNI/native inference.
s = s.replace('append(memory.take(3500))', 'append(memory.takeLast(1400))')
s = s.replace('append(learning.takeLast(3500))', 'append(learning.takeLast(1200))')
s = s.replace('append(fileText.take(12000))', 'append(fileText.take(3500))')
s = s.replace('append(lastSearchContext.take(10000))', 'append(lastSearchContext.take(4500))')
s = s.replace(
    'val enriched = buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)',
    'val enriched = (buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)).take(7000)'
)

# Qwen3 direct-answer mode avoids spending generation time on hidden reasoning for
# ordinary turns. The model's final answer remains streamed normally.
s = s.replace(
    'append(prompt)\n            append("\\n\\nRespondé directamente en español argentino. No inventes datos.")',
    'append("/no_think\\n")\n            append(prompt)\n            append("\\n\\nRespondé directamente en español argentino. No inventes datos.")'
)

# Bounded generation: enough for normal answers, without long tail latency.
s = s.replace('engine.sendUserPrompt(enriched)', 'engine.sendUserPrompt(enriched, 224)')
s = s.replace('engine.sendUserPrompt(buildContextPrompt(query))', 'engine.sendUserPrompt(buildContextPrompt(query), 224)')

# Stream less frequently to the UI, reducing Compose/View-style layout churn.
s = s.replace('now - lastUiUpdate >= 70L', 'now - lastUiUpdate >= 80L')

# Keyboard: replace global-layout polling with AndroidX IME insets.
old_keyboard = '''        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = window.decorView.rootView.height - visible.bottom
            val keyboardOpen = keyboardHeight > dp(180)
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            if (input.hasFocus()) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }'''
new_keyboard = '''        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val keyboardOpen = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE

            // Keep the composer physically above the keyboard. The user must always
            // be able to see the text being typed; never leave the EditText behind IME.
            if (keyboardOpen) {
                composer.translationY = -ime.bottom.toFloat()
                scroll.setPadding(0, 0, 0, dp(90))
                if (input.hasFocus()) {
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            } else {
                composer.translationY = 0f
                scroll.setPadding(0, 0, 0, dp(90))
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)'''
if old_keyboard in s:
    s = s.replace(old_keyboard, new_keyboard)

main.write_text(s)

stage = Path("build-app/app/src/main/java/com/example/llama/LixStage1Core.kt")
if stage.exists():
    s = stage.read_text()
    s = s.replace(
        '''        val memories = readItems(c, MEM).takeLast(12)
        val lessons = readItems(c, LESSONS).takeLast(12)''',
        '''        val memories = readItems(c, MEM).takeLast(4).map { it.take(300) }
        val lessons = readItems(c, LESSONS).takeLast(4).map { it.take(300) }'''
    )
    stage.write_text(s)

cpp = Path("build-app/lib/src/main/cpp/ai_chat.cpp")
s = cpp.read_text()
s = s.replace('constexpr int   N_THREADS_MAX          = 2;', 'constexpr int   N_THREADS_MAX          = 4;')
s = s.replace('constexpr int   BATCH_SIZE              = 128;', 'constexpr int   BATCH_SIZE              = 256;')
cpp.write_text(s)
