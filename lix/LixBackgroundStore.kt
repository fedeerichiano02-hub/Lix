package com.example.llama

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent task journal: UI state survives Activity recreation/process restarts. */
object LixBackgroundStore {
    private const val PREFS = "lix_background"
    private const val TASKS = "tasks"
    private const val MAX_TASKS = 40
    data class Task(
        val id: String,
        val prompt: String,
        val status: String,
        val result: String = "",
        val progress: Int = 0,
        val phase: String = "",
        val lastActivity: Long = 0L,
        val startedAt: Long = 0L,
        val finishedAt: Long = 0L
    )
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
                return fromJson(o)
        }
        return null
    }

    fun latest(context: Context, limit: Int = MAX_TASKS): List<Task> {
        val all = JSONArray(prefs(context).getString(TASKS, "[]") ?: "[]")
        val result = mutableListOf<Task>()
        for (i in maxOf(0, all.length() - limit) until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            result += fromJson(o)
        }
        return result
    }

    private fun fromJson(o: JSONObject) = Task(
        id = o.optString("id"),
        prompt = o.optString("prompt"),
        status = o.optString("status"),
        result = o.optString("result"),
        progress = o.optInt("progress", 0),
        phase = o.optString("phase"),
        lastActivity = o.optLong("lastActivity", 0L),
        startedAt = o.optLong("startedAt", 0L),
        finishedAt = o.optLong("finishedAt", 0L)
    )

    private fun toJson(task: Task) = JSONObject().apply {
        put("id", task.id)
        put("prompt", task.prompt)
        put("status", task.status)
        put("result", task.result)
        put("progress", task.progress.coerceIn(0, 100))
        put("phase", task.phase)
        put("lastActivity", task.lastActivity)
        put("startedAt", task.startedAt)
        put("finishedAt", task.finishedAt)
    }
}
