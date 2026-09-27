from pathlib import Path

p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
s = p.read_text()
imports = '''import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
'''
anchor = 'import androidx.lifecycle.lifecycleScope\n'
if 'import androidx.work.ExistingWorkPolicy' not in s:
    s = s.replace(anchor, anchor + imports, 1)
if 'restoreBackgroundTasks()' not in s:
    s = s.replace('        loadHistory()\n', '        loadHistory()\n        restoreBackgroundTasks()\n', 1)
marker = '        generation = lifecycleScope.launch(Dispatchers.Default) {'
if 'enqueueBackgroundTask(prompt)' not in s:
    replacement = '''        if (taskMode) {
            enqueueBackgroundTask(prompt)
            taskMode = false
            input.isEnabled = true
            send.isEnabled = true
            addMessage("LIX", "Listo, compa. La tarea quedó ejecutándose en segundo plano. Podés cerrar Lix; te aviso cuando termine.")
            return
        }

        generation = lifecycleScope.launch(Dispatchers.Default) {'''
    s = s.replace(marker, replacement, 1)
functions = r'''
    private fun enqueueBackgroundTask(prompt: String) {
        val id = UUID.randomUUID().toString()
        LixBackgroundStore.upsert(this, LixBackgroundStore.Task(id, prompt, "queued"))
        val data = workDataOf(LixBackgroundWorker.KEY_ID to id, LixBackgroundWorker.KEY_PROMPT to prompt)
        val request = OneTimeWorkRequestBuilder<LixBackgroundWorker>().setInputData(data).build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork("lix_task_$id", ExistingWorkPolicy.REPLACE, request)
    }

    private fun restoreBackgroundTasks() {
        val shown = prefs.getStringSet("shown_background_tasks", emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false
        LixBackgroundStore.latest(this).filter { it.status == "completed" && !shown.contains(it.id) }.takeLast(5).forEach { task ->
            addMessage("LIX", "Tarea terminada en segundo plano:\n${task.result}")
            shown.add(task.id); changed = true
        }
        if (changed) prefs.edit().putStringSet("shown_background_tasks", shown).apply()
    }
'''
if 'private fun enqueueBackgroundTask' not in s:
    qmarker = '    private fun qIsTaskCommand(prompt: String): Boolean'
    s = s.replace(qmarker, functions + '\n' + qmarker, 1)
p.write_text(s)
