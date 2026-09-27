package com.example.llama

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent task journal: UI state survives Activity recreation/process restarts. */
object LixBackgroundStore {
    private const val PREFS = "lix_background"
    private const val TASKS = "tasks"
    private const val MAX_TASKS = 40
    data class Task(val id: String, val prompt: String, val status: String, val result: String = "")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun upsert(context: Context, task: Task) {
        val all = JSONArray(prefs(context).getString(TASKS, "[]") ?: "[]")
        val out = JSONArray(); var replaced = false
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("id") == task.id) { out.put(toJson(task)); replaced = true } else out.put(o)
        }
        if (!replaced) out.put(toJson(task))
        val trimmed = JSONArray(); val start = maxOf(0, out.length() - MAX_TASKS)
        for (i in start until out.length()) trimmed.put(out.getJSONObject(i))
        prefs(context).edit().putString(TASKS, trimmed.toString()).apply()
    }

    fun pending(context: Context): Task? {
        val all = JSONArray(prefs(context).getString(TASKS, "[]") ?: "[]")
        for (i in all.length() - 1 downTo 0) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("status") == "queued" || o.optString("status") == "running")
                return Task(o.optString("id"), o.optString("prompt"), o.optString("status"), o.optString("result"))
        }
        return null
    }

    fun latest(context: Context, limit: Int = MAX_TASKS): List<Task> {
        val all = JSONArray(prefs(context).getString(TASKS, "[]") ?: "[]")
        val result = mutableListOf<Task>()
        for (i in maxOf(0, all.length() - limit) until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            result += Task(o.optString("id"), o.optString("prompt"), o.optString("status"), o.optString("result"))
        }
        return result
    }

    private fun toJson(task: Task) = JSONObject().apply {
        put("id", task.id); put("prompt", task.prompt); put("status", task.status); put("result", task.result)
    }
}
