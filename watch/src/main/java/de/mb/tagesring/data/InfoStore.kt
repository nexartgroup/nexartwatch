package de.mb.tagesring.data

import android.content.Context

/** Die Werte der beiden Zusatzinfos (Schritte, Puls), die Health Services der Uhr-App liefert. */
object InfoStore {
    private fun prefs(context: Context) = context.getSharedPreferences("info", Context.MODE_PRIVATE)

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

    /** Ergebnis der letzten Anmeldung bei Health Services (für die Einrichtungsseite). */
    fun healthStatus(context: Context): String = prefs(context).getString("health_status", "") ?: ""
    fun saveHealthStatus(context: Context, status: String) = prefs(context).edit().putString("health_status", status).apply()
}
