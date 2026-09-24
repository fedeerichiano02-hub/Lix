package com.example.llama

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import java.util.Locale

object LixProjectManager {
    fun writeAuthorizedFile(activity: Activity, relativePath: String, content: String): Boolean {
        val prefs = activity.getSharedPreferences("lix", Context.MODE_PRIVATE)
        val raw = prefs.getString(LixAutomation.PREF_TREE_URI, null) ?: return false
        val tree = Uri.parse(raw)
        return try {
            val parts = relativePath.replace("\\", "/").split("/").filter { it.isNotBlank() && it != "." && it != ".." }
            if (parts.isEmpty()) return false
            var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            for (part in parts.dropLast(1)) {
                val next = findChild(activity, tree, parent, part, true) ?: return false
                parent = next
            }
            val name = parts.last()
            var file = findChild(activity, tree, parent, name, false)
            if (file == null) {
                val mime = mimeFor(name)
                file = DocumentsContract.createDocument(activity.contentResolver, parent, mime, name)
            }
            if (file == null) return false
            activity.contentResolver.openOutputStream(file, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findChild(context: Context, tree: Uri, parent: Uri, name: String, directory: Boolean): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                if (c.getString(nameCol).equals(name, true)) {
                    val mime = c.getString(mimeCol)
                    if (!directory || mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol))
                    }
                }
            }
        }
        return null
    }

    private fun mimeFor(name: String): String = when {
        name.lowercase(Locale.ROOT).endsWith(".gd") -> "text/plain"
        name.lowercase(Locale.ROOT).endsWith(".tscn") -> "text/plain"
        name.lowercase(Locale.ROOT).endsWith(".json") -> "application/json"
        else -> "text/plain"
    }
}
