package com.example.llama

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

object LixAutomation {
    const val REQ_LOCATION_SMS = 4101
    const val PREF_TREE_URI = "godot_tree_uri"

    fun handle(activity: Activity, command: String): Boolean {
        val q = command.trim().lowercase(Locale("es","AR"))

        if (q.contains("poneme una alarma") || q.contains("pon una alarma") || q.contains("poné una alarma") || q.startsWith("alarma ")) {
            val time = extractTime(command) ?: run {
                Toast.makeText(activity, "Decime la hora, por ejemplo: alarma a las 7:30", Toast.LENGTH_SHORT).show()
                return true
            }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, time.first)
                putExtra(AlarmClock.EXTRA_MINUTES, time.second)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Alarma creada por Lix")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            Toast.makeText(activity, "Abriendo el reloj para crear la alarma.", Toast.LENGTH_SHORT).show()
            return true
        }

        if (q.contains("emergencia lix") || q.startsWith("lix emergencia") || q.contains("modo emergencia")) {
            val number = Regex("""(?:\+?\d[\d\s-]{7,}\d)""").find(command)?.value?.replace(Regex("[^0-9+]"), "")
            if (number.isNullOrBlank()) {
                Toast.makeText(activity, "Para emergencia necesito un número de contacto autorizado.", Toast.LENGTH_LONG).show()
                return true
            }
            sendEmergency(activity, number, command)
            return true
        }

        if (q.contains("abrí la configuración") || q.contains("abrime la configuración") || q.contains("ajustes del teléfono")) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            return true
        }

        if (q.contains("abrí accesibilidad") || q.contains("activar accesibilidad")) {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return true
        }

        if (q.contains("elegir proyecto godot") || q.contains("seleccionar proyecto godot") || q.contains("abrir carpeta del proyecto")) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            activity.startActivityForResult(intent, 4201)
            return true
        }

        if (q.contains("abrí mi proyecto godot")) {
            val uri = activity.getSharedPreferences("lix", Context.MODE_PRIVATE).getString(PREF_TREE_URI, null)
            if (uri != null) {
                activity.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(uri) })
            } else {
                Toast.makeText(activity, "Primero elegí la carpeta del proyecto Godot.", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        return false
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        val m = Regex("""(?i)(?:a\s+las?\s+)?(\d{1,2})(?::|\s+y\s+)(\d{2})?""").find(text)
        if (m != null) {
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return h to min
        }
        return null
    }

    private fun sendEmergency(activity: Activity, number: String, original: String) {
        val locationGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val smsGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!locationGranted || !smsGranted) {
            val needed = mutableListOf<String>()
            if (!locationGranted) needed += Manifest.permission.ACCESS_FINE_LOCATION
            if (!smsGranted) needed += Manifest.permission.SEND_SMS
            ActivityCompat.requestPermissions(activity, needed.toTypedArray(), REQ_LOCATION_SMS)
            Toast.makeText(activity, "Dale a Lix ubicación y SMS y repetí la orden.", Toast.LENGTH_LONG).show()
            return
        }
        val lm = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }

        val body = buildString {
            append("🆘 EMERGENCIA DE LIX\n")
            append("Necesito ayuda.\n")
            append("Situación: ").append(original.take(240)).append("\n")
            if (loc != null) {
                append("Ubicación: https://maps.google.com/?q=").append(loc.latitude).append(",").append(loc.longitude).append("\n")
            } else {
                append("Ubicación: no disponible en este momento.\n")
            }
            append("Hora: ").append(Calendar.getInstance().time).append("\n")
        }
        SmsManager.getDefault().sendTextMessage(number, null, body, null, null)
        Toast.makeText(activity, "Emergencia enviada por SMS.", Toast.LENGTH_LONG).show()
    }
}
