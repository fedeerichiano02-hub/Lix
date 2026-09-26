from pathlib import Path

main = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = main.read_text()

hook = '        if (LixGodotAgent.shouldHandle(prompt)) { runGodotAgent(prompt); input.setText(""); return }\n'
needle = '        if (LixStage1Core.handle(this, prompt)) { input.setText(""); return }'
if hook not in s:
    if needle not in s:
        raise SystemExit("sendMessage hook point not found")
    s = s.replace(needle, hook + needle, 1)

marker = '    private fun searchInternetAndAnswer(query: String) {'
method = '''    private fun runGodotAgent(prompt: String) {
        val answer = TextView(this).apply {
            text = "Lix está trabajando en el proyecto Godot..."
            textSize = 15f
            setTextColor(textColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(panel2, 14, border)
        }
        chat.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        input.isEnabled = false
        send.isEnabled = false
        generation?.cancel()
        generation = lifecycleScope.launch(Dispatchers.IO) {
            var total = 0
            var lastReport = ""
            repeat(3) { pass ->
                val promptForModel = LixGodotAgent.workspacePrompt(this@MainActivity, prompt, pass > 0)
                val result = StringBuilder()
                engine.sendUserPrompt(promptForModel).collect { token -> result.append(token) }
                val operations = LixGodotAgent.applyOperations(this@MainActivity, result.toString())
                total += operations
                lastReport = result.toString().substringAfter("CHECK", result.toString()).trim().take(700)
                withContext(Dispatchers.Main) {
                    answer.text = if (operations > 0) "Lix modificó $operations archivo(s). Verificando el proyecto..." else "Lix revisó el proyecto. Verificando..."
                }
                if (operations == 0 && pass > 0) return@repeat
            }
            withContext(Dispatchers.Main) {
                answer.text = "Listo, compa. Lix terminó la tarea de Godot.\n\n$lastReport"
                input.isEnabled = true
                send.isEnabled = true
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

'''
if 'private fun runGodotAgent(prompt: String)' not in s:
    if marker not in s:
        raise SystemExit("method insertion marker not found")
    s = s.replace(marker, method + marker, 1)
main.write_text(s)

workflow = Path(".github/workflows/build-apk.yml")
w = workflow.read_text()
old = 'for f in LixAutomation.kt LixAccessibilityService.kt LixWakeService.kt LixTaskService.kt LixVoiceInteractionService.kt LixVoiceSessionService.kt LixProjectManager.kt LixEvolution.kt LixVisualCreator.kt LixFeatureHub.kt LixFeatureRuntime.kt LixMegaModules.kt LixStage1Core.kt LixCapabilityStore.kt; do cp "lix/$f" "build-app/app/src/main/java/com/example/llama/$f"; done'
new = 'for f in LixAutomation.kt LixAccessibilityService.kt LixWakeService.kt LixTaskService.kt LixVoiceInteractionService.kt LixVoiceSessionService.kt LixProjectManager.kt LixEvolution.kt LixVisualCreator.kt LixFeatureHub.kt LixFeatureRuntime.kt LixMegaModules.kt LixStage1Core.kt LixCapabilityStore.kt LixGodotAgent.kt; do cp "lix/$f" "build-app/app/src/main/java/com/example/llama/$f"; done'
if old in w and new not in w:
    w = w.replace(old, new, 1)
workflow.write_text(w)
