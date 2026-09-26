from pathlib import Path

main = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = main.read_text()

# Fast local inference: keep the small model, minimize prompt overhead, and cap normal replies.
s = s.replace("models/lix-qwen3-1.7b-q4_k_m.gguf", "models/lix-qwen3-0.6b-q4_0.gguf")
s = s.replace('append(memory.take(3500))', 'append(memory.takeLast(1000))')
s = s.replace('append(learning.takeLast(3500))', 'append(learning.takeLast(900))')
s = s.replace('append(fileText.take(12000))', 'append(fileText.take(2500))')
s = s.replace('append(lastSearchContext.take(10000))', 'append(lastSearchContext.take(3500))')
s = s.replace('engine.sendUserPrompt(enriched)', 'engine.sendUserPrompt(enriched, 160)')
s = s.replace('engine.sendUserPrompt(buildContextPrompt(query))', 'engine.sendUserPrompt(buildContextPrompt(query), 160)')
s = s.replace('now - lastUiUpdate >= 70L', 'now - lastUiUpdate >= 50L')
main.write_text(s)

stage = Path("build-app/app/src/main/java/com/example/llama/LixStage1Core.kt")
if stage.exists():
    s = stage.read_text()
    s = s.replace('readItems(c, MEM).takeLast(12)', 'readItems(c, MEM).takeLast(4).map { it.take(250) }')
    s = s.replace('readItems(c, LESSONS).takeLast(12)', 'readItems(c, LESSONS).takeLast(4).map { it.take(250) }')
    stage.write_text(s)

cpp = Path("build-app/lib/src/main/cpp/ai_chat.cpp")
s = cpp.read_text()
s = s.replace('constexpr int   DEFAULT_CONTEXT_SIZE    = 2048;', 'constexpr int   DEFAULT_CONTEXT_SIZE    = 1536;')
s = s.replace('constexpr int   N_THREADS_MAX          = 2;', 'constexpr int   N_THREADS_MAX          = 4;')
s = s.replace('constexpr int   BATCH_SIZE              = 128;', 'constexpr int   BATCH_SIZE              = 256;')
cpp.write_text(s)
