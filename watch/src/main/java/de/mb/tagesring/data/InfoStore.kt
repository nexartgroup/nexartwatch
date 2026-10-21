package de.mb.tagesring.data

import android.content.Context
import de.mb.tagesring.sync.InfoType

/** Auswahl der beiden Zusatzinfos (von der Handy-App) und die Werte, die die Uhr dafür sammelt. */
object InfoStore {
    private fun prefs(context: Context) = context.getSharedPreferences("info", Context.MODE_PRIVATE)

    /** [index] 1 = oberes Feld, 2 = unteres Feld. Standard: Schritte und Puls. */
    fun slot(context: Context, index: Int): InfoType =
        InfoType.fromId(prefs(context).getString("slot$index", null), if (index == 1) InfoType.STEPS else InfoType.HEART_RATE)

    fun phoneBattery(context: Context): Int = prefs(context).getInt("battery", -1)
    fun phoneCharging(context: Context): Boolean = prefs(context).getBoolean("charging", false)
    fun alarmAt(context: Context): Long = prefs(context).getLong("alarmAt", 0L)

    /** @return true, wenn sich etwas geändert hat. */
    fun savePhone(context: Context, slot1: InfoType, slot2: InfoType, battery: Int, charging: Boolean, alarmAt: Long): Boolean {
        val p = prefs(context)
        val changed = p.getString("slot1", null) != slot1.id || p.getString("slot2", null) != slot2.id ||
            p.getInt("battery", -1) != battery || p.getBoolean("charging", false) != charging || p.getLong("alarmAt", 0L) != alarmAt
        if (changed) {
            p.edit().putString("slot1", slot1.id).putString("slot2", slot2.id).putInt("battery", battery)
                .putBoolean("charging", charging).putLong("alarmAt", alarmAt).apply()
        }
        return changed
    }

    class Reading(val value: Long, val at: Long)

    fun steps(context: Context): Reading? = reading(context, "steps")
    fun heartRate(context: Context): Reading? = reading(context, "hr")

    fun saveSteps(context: Context, steps: Long, at: Long = System.currentTimeMillis()) = save(context, "steps", steps, at)
    fun saveHeartRate(context: Context, bpm: Long, at: Long = System.currentTimeMillis()) = save(context, "hr", bpm, at)

    private fun reading(context: Context, key: String): Reading? {
        val p = prefs(context)
        val at = p.getLong("${key}_at", 0L)
        return if (at == 0L) null else Reading(p.getLong(key, 0L), at)
    }

    private fun save(context: Context, key: String, value: Long, at: Long) {
        prefs(context).edit().putLong(key, value).putLong("${key}_at", at).apply()
    }

    /** Wurde "Schritte"/"Puls" schon einmal erfolgreich bei Health Services angemeldet? */
    fun healthStatus(context: Context): String = prefs(context).getString("health_status", "") ?: ""
    fun saveHealthStatus(context: Context, status: String) = prefs(context).edit().putString("health_status", status).apply()
}
