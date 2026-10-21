package de.mb.tagesring.data

import android.content.Context
import org.json.JSONObject

/**
 * Benachrichtigungszähler vom Handy (die Handy-App zählt die aktiven Benachrichtigungen und
 * schickt die Zahlen). Wird dauerhaft gespeichert, damit das Watchface sie nach einem Neustart
 * des Prozesses sofort wieder hat.
 */
object NotificationStore {
    @Volatile
    private var cached: NotificationSnapshot? = null

    fun snapshot(context: Context): NotificationSnapshot {
        cached?.let { return it }
        val prefs = context.getSharedPreferences("counters", Context.MODE_PRIVATE)
        val loaded = prefs.getString("json", null)?.let { parse(it) } ?: NotificationSnapshot()
        cached = loaded
        return loaded
    }

    /** @return true, wenn sich die Zähler geändert haben. */
    fun update(context: Context, snapshot: NotificationSnapshot): Boolean {
        if (snapshot == this.snapshot(context)) return false
        cached = snapshot
        context.getSharedPreferences("counters", Context.MODE_PRIVATE).edit()
            .putString("json", toJson(snapshot)).apply()
        return true
    }

    private fun toJson(s: NotificationSnapshot) = JSONObject()
        .put("connected", s.connected).put("phone", s.phone).put("whatsapp", s.whatsapp)
        .put("mail", s.mail).put("other", s.other)
        .put("packages", JSONObject(s.packages as Map<*, *>)).toString()

    private fun parse(json: String): NotificationSnapshot? = try {
        val o = JSONObject(json)
        val pk = o.optJSONObject("packages")
        NotificationSnapshot(
            connected = o.optBoolean("connected"),
            phone = o.optInt("phone"), whatsapp = o.optInt("whatsapp"),
            mail = o.optInt("mail"), other = o.optInt("other"),
            packages = pk?.keys()?.asSequence()?.associateWith { pk.getInt(it) } ?: emptyMap(),
        )
    } catch (_: Exception) {
        null
    }
}
