package com.example.llama

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LixMegaModules {
    private const val PREF = "lix_mega"
    private const val VAULT_ALIAS = "LixVaultKeyV1"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

    fun handle(c: Context, prompt: String): Boolean {
        val q = prompt.lowercase(Locale("es", "AR")).trim()
        return when {
            q == "centro de control" || q.contains("centro de control de lix") -> { LixFeatureHub.show(c); true }
            q.contains("crear una imagen") || q.contains("generá una imagen") || q.contains("genera una imagen") -> { LixVisualCreator.open(c); true }
            q.contains("cerebro") || q.contains("memoria profunda") -> { toast(c, "🧠 Memoria profunda: la memoria y el aprendizaje contextual están activos."); true }\n            q.contains("conversación natural") || q.contains("conversacion natural") -> { toast(c, "🗣️ Conversación Natural: voz continua y TTS están disponibles."); true }\n            q.contains("traductor universal") -> { toast(c, "🌎 Traductor Universal: pedile a Lix que traduzca al idioma que quieras."); false }\n            q.contains("modo experto") -> { prefs(c).edit().putBoolean("expert", true).apply(); toast(c, "🧠 Modo Experto activado."); true }
            q.contains("modo tutor") || q.contains("activar tutor") -> { prefs(c).edit().putBoolean("tutor", true).apply(); toast(c, "📚 Modo Tutor activado."); true }
            q.contains("desactivar modo experto") -> { prefs(c).edit().putBoolean("expert", false).apply(); toast(c, "Modo Experto desactivado."); true }
            q.contains("desactivar tutor") -> { prefs(c).edit().putBoolean("tutor", false).apply(); toast(c, "Modo Tutor desactivado."); true }
            q.contains("abrí el navegador") || q.contains("abrir navegador") || q.contains("navegador inteligente") -> { openUrl(c, "https://www.google.com"); true }
            q.contains("buscar en internet") && q.length > 20 -> { openUrl(c, "https://www.google.com/search?q=" + Uri.encode(prompt)); true }
            q.contains("bóveda") || q.contains("boveda") -> { vaultUi(c); true }
            q.contains("guardián") || q.contains("guardian") -> { guardianUi(c); true }
            q.contains("laboratorio") -> { laboratoryUi(c); true }
            q.contains("controlar mi pc") || q.contains("control total del pc") || q.contains("conectar pc") -> { pcUi(c); true }
            q.contains("lix red") || q.contains("multidispositivo") || q.contains("conectar dispositivo") -> { networkUi(c); true }
            q.contains("constructor") || q.contains("constructor de proyectos") -> { constructorUi(c); true }
            q.contains("game studio") || q.contains("crear juego") || q.contains("godot studio") -> { gameStudioUi(c); true }
            q.contains("documentos") || q.contains("crear documento") -> { documentsUi(c); true }
            q.contains("multimodal") -> { multimodalUi(c); true }
            q.contains("visión en vivo") || q.contains("vision en vivo") -> { visionUi(c); true }
            q.contains("evolución") || q.contains("evolucion") -> { evolutionUi(c); true }
            q.contains("agente") || q.contains("hacelo vos") -> { agentUi(c, prompt); true }
            else -> false
        }
    }

    fun modeContext(c: Context): String {
        val p = prefs(c)
        val out = StringBuilder()
        if (p.getBoolean("expert", false)) out.append("MODO EXPERTO: profundizá, verificá supuestos y usá pasos concretos.\n")
        if (p.getBoolean("tutor", false)) out.append("MODO TUTOR: enseñá paso a paso, con ejemplos y comprobaciones.\n")
        return out.toString()
    }

    private fun openUrl(c: Context, url: String) {
        try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { toast(c, "No pude abrir el navegador.") }
    }

    private fun visionUi(c: Context) {
        AlertDialog.Builder(c).setTitle("👁️ Visión de Lix")
            .setMessage("Visión: selección de imágenes, OCR y etiquetado local. La cámara requiere permiso explícito.")
            .setPositiveButton("Elegir imagen") { _, _ ->
                if (c is Activity) c.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                }, 701)
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun multimodalUi(c: Context) {
        AlertDialog.Builder(c).setTitle("🧩 Multimodal")
            .setMessage("Texto + voz + imágenes + OCR + archivos + generación visual conectados al núcleo de Lix.")
            .setPositiveButton("OK", null).show()
    }

    private fun documentsUi(c: Context) {
        if (c !is Activity) return
        AlertDialog.Builder(c).setTitle("📝 Documentos")
            .setItems(arrayOf("Abrir documento", "Crear TXT", "Crear Markdown", "Crear JSON")) { _, which ->
                when (which) {
                    0 -> c.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 702)
                    1 -> createDocument(c, "text/plain", "lix_documento.txt")
                    2 -> createDocument(c, "text/markdown", "lix_documento.md")
                    3 -> createDocument(c, "application/json", "lix_documento.json")
                }
            }.show()
    }

    private fun createDocument(a: Activity, mime: String, name: String) {
        a.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mime; putExtra(Intent.EXTRA_TITLE, name); addCategory(Intent.CATEGORY_OPENABLE)
        }, 703)
    }

    private fun constructorUi(c: Context) {
        AlertDialog.Builder(c).setTitle("🏗️ Constructor")
            .setMessage("Constructor autorizado: elegí una carpeta de proyecto y Lix puede crear o modificar scripts, escenas y JSON.")
            .setPositiveButton("Elegir proyecto") { _, _ ->
                if (c is Activity) c.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 704)
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun gameStudioUi(c: Context) {
        AlertDialog.Builder(c).setTitle("🎮 Lix Game Studio")
            .setMessage("Herramientas Godot: autorización de carpeta, creación/modificación de scripts, escenas y JSON, más asistencia del modelo local.")
            .setPositiveButton("Elegir proyecto Godot") { _, _ -> constructorUi(c) }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun laboratoryUi(c: Context) {
        val rt = Runtime.getRuntime()
        val msg = "Arquitectura: " + System.getProperty("os.arch") + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Heap máximo: " + (rt.maxMemory() / 1024 / 1024) + " MB\n" +
                "Heap libre: " + (rt.freeMemory() / 1024 / 1024) + " MB\n" +
                "Modelo local: Qwen3 1.7B Q4_K_M\n" +
                "ABI objetivo: armeabi-v7a"
        AlertDialog.Builder(c).setTitle("🧪 Laboratorio").setMessage(msg)
            .setPositiveButton("Ajustes de batería") { _, _ ->
                try { c.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) } catch (_: Exception) {}
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun guardianUi(c: Context) {
        val p = prefs(c)
        val enabled = p.getBoolean("guardian", false)
        AlertDialog.Builder(c).setTitle("🛡️ Guardián")
            .setMessage("Estado: " + if (enabled) "ACTIVO" else "INACTIVO" + "\n\nAlertas locales y reglas configurables. No monitorea cámara o micrófono de forma oculta.")
            .setPositiveButton(if (enabled) "Desactivar" else "Activar") { _, _ ->
                p.edit().putBoolean("guardian", !enabled).apply()
                toast(c, if (!enabled) "Guardián activado." else "Guardián desactivado.")
            }.setNeutralButton("Probar alerta") { _, _ -> notify(c, "🛡️ Guardián", "Prueba de alerta de Lix.") }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun pcUi(c: Context) {
        val host = EditText(c).apply { hint = "IP o dominio del PC" }
        val port = EditText(c).apply { hint = "Puerto"; inputType = InputType.TYPE_CLASS_NUMBER; setText("8765") }
        val box = LinearLayout(c).apply { orientation=LinearLayout.VERTICAL; setPadding(30,10,30,0); addView(host); addView(port) }
        AlertDialog.Builder(c).setTitle("🖥️ Control del PC")
            .setMessage("Configurá un companion de Lix en el PC. Lix probará el endpoint antes de enviar órdenes.")
            .setView(box).setPositiveButton("Probar conexión") { _, _ ->
                val h=host.text.toString().trim(); val p=port.text.toString().trim()
                if (h.isNotBlank()) testEndpoint(c, "http://" + h + ":" + p + "/health")
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun networkUi(c: Context) {
        val host = EditText(c).apply { hint="IP o URL del nodo Lix" }
        AlertDialog.Builder(c).setTitle("🌐 Lix Red")
            .setMessage("Conexión multidispositivo mediante un endpoint controlado por vos. La app no transmite datos automáticamente.")
            .setView(host).setPositiveButton("Guardar y probar") { _, _ ->
                val u=host.text.toString().trim()
                prefs(c).edit().putString("network_endpoint", u).apply()
                if (u.isNotBlank()) testEndpoint(c, (if (u.startsWith("http")) u else "http://" + u) + "/health")
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun testEndpoint(c: Context, url: String) {
        Thread {
            val ok = try {
                val conn=URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout=4000; conn.readTimeout=4000
                val code=conn.responseCode; conn.disconnect(); code in 200..499
            } catch (_: Exception) { false }
            Handler(Looper.getMainLooper()).post { toast(c, if(ok) "Conexión establecida." else "No se pudo conectar.") }
        }.start()
    }

    private fun evolutionUi(c: Context) {
        try {
            val list=LixEvolution.catalog(c)
            val text=list.joinToString("\n\n") { it.title + " v" + it.version + "\n" + it.description }
            AlertDialog.Builder(c).setTitle("🧬 Evolución").setMessage(text.ifBlank { "Sin módulos." })
                .setPositiveButton("Actualizar catálogo") { _, _ ->
                    Thread { try { LixEvolution.refresh(c); Handler(Looper.getMainLooper()).post{toast(c,"Catálogo actualizado.")} } catch(_:Exception){Handler(Looper.getMainLooper()).post{toast(c,"No se pudo actualizar.")}} }.start()
                }.setNegativeButton("Cerrar", null).show()
        } catch (_: Exception) { toast(c,"Evolución no disponible.") }
    }

    private fun agentUi(c: Context, original: String) {
        AlertDialog.Builder(c).setTitle("🤖 Agente Lix — Hacelo vos")
            .setMessage("El agente ejecuta acciones autorizadas del teléfono y proyecto. Las acciones sensibles quedan sujetas a permisos del sistema.")
            .setPositiveButton("Ejecutar orden") { _, _ ->
                if (c is Activity && !LixAutomation.handle(c, original)) toast(c, "La orden pasó al cerebro de Lix.")
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun vaultKey(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(VAULT_ALIAS,null) as? SecretKey)?.let{return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(VAULT_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }

    private fun vaultPut(c: Context, key: String, value: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,vaultKey())
        val iv=cipher.iv; val ct=cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val stored=android.util.Base64.encodeToString(iv,android.util.Base64.NO_WRAP)+":"+
                android.util.Base64.encodeToString(ct,android.util.Base64.NO_WRAP)
        prefs(c).edit().putString("vault_"+key,stored).apply()
    }

    private fun vaultGet(c: Context,key:String):String?{
        val stored=prefs(c).getString("vault_"+key,null) ?: return null
        val parts=stored.split(":",limit=2); if(parts.size!=2)return null
        val iv=android.util.Base64.decode(parts[0],android.util.Base64.NO_WRAP)
        val ct=android.util.Base64.decode(parts[1],android.util.Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,vaultKey(),GCMParameterSpec(128,iv))
        return String(cipher.doFinal(ct),StandardCharsets.UTF_8)
    }

    private fun vaultUi(c: Context) {
        val input=EditText(c).apply{hint="Nota privada";minLines=3}
        AlertDialog.Builder(c).setTitle("🔐 Bóveda de Lix")
            .setMessage("Datos cifrados con una clave protegida por Android Keystore.")
            .setView(input).setPositiveButton("Guardar") { _, _ ->
                val v=input.text.toString(); if(v.isNotBlank()){vaultPut(c,"latest",v);toast(c,"Guardado cifrado.")}
            }.setNeutralButton("Ver última") { _, _ ->
                val v=vaultGet(c,"latest") ?: "No hay una nota guardada."
                AlertDialog.Builder(c).setTitle("Bóveda").setMessage(v).setPositiveButton("Cerrar",null).show()
            }.setNegativeButton("Cerrar",null).show()
    }

    private fun notify(c: Context,title:String,text:String){
        val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="lix_guardian"
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(channel,"Lix Guardián",NotificationManager.IMPORTANCE_DEFAULT))
        val n=Notification.Builder(c,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        nm.notify((System.currentTimeMillis()%100000).toInt(),n)
    }
}
