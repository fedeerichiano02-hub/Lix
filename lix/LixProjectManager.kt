package com.example.llama

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

/** Godot workspace bridge. The user explicitly authorizes a project folder through SAF. */
object LixProjectManager {
    data class ProjectSnapshot(val projectFile: String, val scripts: List<String>, val scenes: List<String>, val assets: List<String>, val other: List<String>)

    fun hasAuthorizedProject(activity: Activity): Boolean = activity.getSharedPreferences("lix", Context.MODE_PRIVATE).getString(LixAutomation.PREF_TREE_URI, null) != null

    fun writeAuthorizedFile(activity: Activity, relativePath: String, content: String): Boolean {
        val tree = treeUri(activity) ?: return false
        return try {
            val parts = safeParts(relativePath); if (parts.isEmpty()) return false
            var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            for (part in parts.dropLast(1)) parent = findChild(activity, tree, parent, part, true) ?: return false
            val name = parts.last()
            var file = findChild(activity, tree, parent, name, false)
            if (file == null) file = DocumentsContract.createDocument(activity.contentResolver, parent, mimeFor(name), name)
            if (file == null) return false
            activity.contentResolver.openOutputStream(file, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: return false
            true
        } catch (_: Exception) { false }
    }

    fun readAuthorizedFile(activity: Activity, relativePath: String, maxChars: Int = 12000): String? {
        val tree = treeUri(activity) ?: return null
        return try {
            val parts = safeParts(relativePath); if (parts.isEmpty()) return null
            var current = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            for (part in parts) current = findChild(activity, tree, current, part, part != parts.last()) ?: return null
            activity.contentResolver.openInputStream(current)?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(maxChars) }
        } catch (_: Exception) { null }
    }

    fun scanAuthorizedProject(activity: Activity, maxEntries: Int = 120): ProjectSnapshot? {
        val tree = treeUri(activity) ?: return null
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val scripts = mutableListOf<String>(); val scenes = mutableListOf<String>(); val assets = mutableListOf<String>(); val other = mutableListOf<String>()
        val queue = ArrayDeque<Pair<Uri, String>>(); queue.add(root to "")
        while (queue.isNotEmpty() && scripts.size + scenes.size + assets.size + other.size < maxEntries) {
            val (parent, prefix) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
            activity.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME); val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext() && scripts.size + scenes.size + assets.size + other.size < maxEntries) {
                    val name = c.getString(nameCol); val id = c.getString(idCol); val mime = c.getString(mimeCol); val rel = if (prefix.isBlank()) name else "$prefix/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) queue.add(DocumentsContract.buildDocumentUriUsingTree(tree, id) to rel)
                    else when {
                        name.equals("project.godot", true) -> other += rel
                        name.endsWith(".gd", true) || name.endsWith(".gdshader", true) -> scripts += rel
                        name.endsWith(".tscn", true) || name.endsWith(".tres", true) -> scenes += rel
                        name.endsWith(".glb", true) || name.endsWith(".gltf", true) || name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> assets += rel
                        else -> other += rel
                    }
                }
            }
        }
        return ProjectSnapshot(other.firstOrNull { it.equals("project.godot", true) } ?: "", scripts, scenes, assets, other.take(30))
    }

    fun snapshotForPrompt(activity: Activity): String {
        val s = scanAuthorizedProject(activity) ?: return "No hay un proyecto Godot autorizado."
        val project = if (s.projectFile.isBlank()) "" else readAuthorizedFile(activity, s.projectFile, 5000).orEmpty()
        return buildString {
            append("GODOT WORKSPACE ACTIVO\n")
            append("project.godot:\n").append(project).append("\n")
            append("Scripts:\n").append(s.scripts.take(30).joinToString("\n")).append("\n")
            append("Escenas:\n").append(s.scenes.take(30).joinToString("\n")).append("\n")
            append("Assets:\n").append(s.assets.take(30).joinToString("\n"))
        }.take(12000)
    }

    private fun treeUri(activity: Activity): Uri? = activity.getSharedPreferences("lix", Context.MODE_PRIVATE).getString(LixAutomation.PREF_TREE_URI, null)?.let(Uri::parse)
    private fun safeParts(path: String) = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }

    private fun findChild(context: Context, tree: Uri, parent: Uri, name: String, directory: Boolean): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME); val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) if (c.getString(nameCol).equals(name, true)) {
                val mime = c.getString(mimeCol)
                if (!directory || mime == DocumentsContract.Document.MIME_TYPE_DIR) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol))
            }
        }
        return null
    }

    private fun mimeFor(name: String): String = when {
        name.lowercase(Locale.ROOT).endsWith(".json") -> "application/json"
        name.lowercase(Locale.ROOT).endsWith(".gd") || name.lowercase(Locale.ROOT).endsWith(".tscn") || name.lowercase(Locale.ROOT).endsWith(".tres") -> "text/plain"
        else -> "text/plain"
    }
}
