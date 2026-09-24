package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import java.util.Locale

object LixFeatureRuntime {
    fun handle(context: Context, prompt: String): Boolean {
        val q = prompt.lowercase(Locale("es","AR"))
        return when {
            q.contains("crear una imagen") || q.contains("generá una imagen") || q.contains("genera una imagen") -> {
                LixVisualCreator.open(context); true
            }
            q.contains("traduc") -> { showPrompt(context, "🌎 Traductor", "Escribí el texto y el idioma destino. Lix lo traducirá usando su IA/Internet."); true }
            q.contains("modo experto") || q.contains("actuá como experto") -> {
                Toast.makeText(context, "Modo Experto activado para esta conversación.", Toast.LENGTH_SHORT).show(); false
            }
            q.contains("tutor") || q.contains("enseñame") || q.contains("enseñame") -> {
                Toast.makeText(context, "Modo Tutor activado.", Toast.LENGTH_SHORT).show(); false
            }
            q.contains("bóveda") || q.contains("boveda") -> { openVault(context); true }
            q.contains("centro de control") -> { LixFeatureHub.show(context); true }
            q.contains("configuración") && q.contains("permis") -> {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+context.packageName))); true
            }
            else -> false
        }
    }

    private fun showPrompt(context: Context, title: String, message: String) {
        val e = EditText(context).apply { hint = "Texto / instrucciones"; minLines = 3 }
        AlertDialog.Builder(context).setTitle(title).setMessage(message).setView(e)
            .setPositiveButton("Listo", null).setNegativeButton("Cancelar", null).show()
    }

    private fun openVault(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("🔐 Bóveda de Lix")
            .setMessage("La bóveda se reserva para datos sensibles. El acceso queda bajo control del dispositivo y sus permisos.")
            .setPositiveButton("Configuración", null)
            .setNegativeButton("Cerrar", null).show()
    }
}
