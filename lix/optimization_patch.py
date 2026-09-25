from pathlib import Path

main = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = main.read_text()

# The native engine already keeps the conversation/KV state. Avoid re-sending the
# whole UI history on every turn, which was causing expensive prompt prefill.
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
'''            // Conversation turns are already retained by the native llama.cpp
            // engine/KV state; do not duplicate them in every user prompt.
'''
)

# Keep external context compact enough for the 2048-token mobile context.
s = s.replace('append(memory.take(3500))', 'append(memory.takeLast(1400))')
s = s.replace('append(learning.takeLast(3500))', 'append(learning.takeLast(1200))')
s = s.replace('append(fileText.take(12000))', 'append(fileText.take(3500))')
s = s.replace('append(lastSearchContext.take(10000))', 'append(lastSearchContext.take(4500))')

# The explicit Stage-1 context is already compacted by the Stage-1 module.
s = s.replace(
'val enriched = buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)',
'val enriched = (buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)).take(8500)'
)

# Limit generation length for much faster completion on the 32-bit POCO C65.
s = s.replace('engine.sendUserPrompt(enriched)', 'engine.sendUserPrompt(enriched, 192)')
s = s.replace('engine.sendUserPrompt(buildContextPrompt(query))', 'engine.sendUserPrompt(buildContextPrompt(query), 192)')

# Reduce UI churn while preserving streaming.
s = s.replace('now - lastUiUpdate >= 70L', 'now - lastUiUpdate >= 80L')

# Keyboard: replace the expensive global-layout polling with AndroidX IME insets.
const old_keyboard = '''        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            val keyboardHeight = window.decorView.rootView.height - visible.bottom
            val keyboardOpen = keyboardHeight > dp(180)
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            if (input.hasFocus()) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }'''
const new_keyboard = '''        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val keyboardOpen = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            nav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
            if (keyboardOpen && input.hasFocus()) {
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)'''
if old_keyboard not in s:
    raise SystemExit("keyboard callback not found")
s=s.replace(old_keyboard,new_keyboard)

# Slightly longer output than the previous build, while keeping latency bounded.
s=s.replace('engine.sendUserPrompt(enriched, 192)', 'engine.sendUserPrompt(enriched, 224)')
s=s.replace('engine.sendUserPrompt(buildContextPrompt(query), 192)', 'engine.sendUserPrompt(buildContextPrompt(query), 224)')

# Reduce native prompt prefill further without cutting the model response itself.
s=s.replace('val enriched = (buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)).take(8500)',
'''val enriched = (buildContextPrompt(prompt) + "\\n\\n" + LixStage1Core.modeContext(this)).take(7000)''')

cpp = Path("build-app/lib/src/main/cpp/ai_chat.cpp")
s = cpp.read_text()
s = s.replace('constexpr int   N_THREADS_MAX          = 2;', 'constexpr int   N_THREADS_MAX          = 4;')
s = s.replace('constexpr int   BATCH_SIZE              = 128;', 'constexpr int   BATCH_SIZE              = 256;')
cpp.write_text(s)


main.write_text(s)

stage = Path("build-app/app/src/main/java/com/example/llama/LixStage1Core.kt")
if stage.exists():
    s = stage.read_text()
    old = '''        val memories = readItems(c, MEM).takeLast(12)
        val lessons = readItems(c, LESSONS).takeLast(12)'''
    new = '''        val memories = readItems(c, MEM).takeLast(4).map { it.take(300) }
        val lessons = readItems(c, LESSONS).takeLast(4).map { it.take(300) }'''
    s = s.replace(old, new)
    stage.write_text(s)

cpp = Path("build-app/lib/src/main/cpp/ai_chat.cpp")
s = cpp.read_text()
s = s.replace(
    'constexpr int   N_THREADS_MAX          = 2;',
    'constexpr int   N_THREADS_MAX          = 4;'
)
s = s.replace(
    'constexpr int   BATCH_SIZE              = 128;',
    'constexpr int   BATCH_SIZE              = 256;'
)
needle = 'ctx_params.n_threads_batch = n_threads;'
cpp.write_text(s)
