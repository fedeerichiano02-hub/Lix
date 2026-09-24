package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LixVisualCreator {
    fun open(context: Context) {
        val input = EditText(context).apply {
            hint = "Describí la imagen que querés crear..."
            minLines = 3
            setPadding(24, 18, 24, 18)
        }
        AlertDialog.Builder(context)
            .setTitle("✦ Lix Creador Visual")
            .setMessage("Generación de imágenes mediante IA.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Generar") { _, _ ->
                val prompt = input.text.toString().trim()
                if (prompt.isNotBlank()) generate(context, prompt)
            }.show()
    }

    private fun generate(context: Context, prompt: String) {
        Toast.makeText(context, "Lix está creando la imagen...", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
                val url = URL("https://image.pollinations.ai/prompt/$encoded?model=flux&width=1024&height=1024&safe=true&private=true")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 180000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Lix/2.0 Android")
                }
                conn.connect()
                if (conn.responseCode !in 200..299) throw IllegalStateException("Servidor de imágenes: HTTP ${conn.responseCode}")
                val outFile = File(context.getExternalFilesDir(null), "Lix/imagenes/lix_${System.currentTimeMillis()}.jpg")
                outFile.parentFile?.mkdirs()
                conn.inputStream.use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
                val bitmap = BitmapFactory.decodeFile(outFile.absolutePath) ?: throw IllegalStateException("Imagen inválida")
                Handler(Looper.getMainLooper()).post {
                    val image = ImageView(context).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    val box = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(20, 20, 20, 10)
                        addView(image, LinearLayout.LayoutParams(-1, 720))
                        addView(TextView(context).apply { text = "Guardada en Lix/imagenes" })
                    }
                    AlertDialog.Builder(context).setTitle("Imagen creada").setView(box).setPositiveButton("Listo", null).show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "No pude generar la imagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
