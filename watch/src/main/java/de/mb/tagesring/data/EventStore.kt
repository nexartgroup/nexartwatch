package de.mb.tagesring.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Termine, die die Handy-App geschickt hat (nach Kalenderauswahl und Ganztägig-Einstellung).
 * Liegt als JSON-Datei auf der Uhr, damit das Watchface auch ohne Handy weiter funktioniert.
 */
object EventStore {

    class Snapshot(val receivedAt: Long, val events: List<CalendarEvent>)

    private const val FILE = "phone_events.json"

    @Synchronized
    fun save(context: Context, events: List<CalendarEvent>, receivedAt: Long = System.currentTimeMillis()) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("title", e.title).put("begin", e.begin).put("end", e.end)
                    .put("location", e.location ?: "").put("calendar", e.calendarName ?: "")
                    .put("color", e.color).put("allDay", e.allDay),
            )
        }
        val json = JSONObject().put("receivedAt", receivedAt).put("events", arr).toString()
        File(context.filesDir, FILE).writeText(json)
    }

    @Synchronized
    fun load(context: Context): Snapshot? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("events")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CalendarEvent(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    begin = o.getLong("begin"),
                    end = o.getLong("end"),
                    location = o.optString("location").ifEmpty { null },
                    calendarName = o.optString("calendar").ifEmpty { null },
                    color = o.optInt("color"),
                    allDay = o.optBoolean("allDay"),
                )
            }
            Snapshot(root.getLong("receivedAt"), list)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
