package de.mb.tagesring.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat

/**
 * Liefert die Termine für das Watchface.
 *
 * 1. Hat die Handy-App Termine geschickt, gelten nur diese (Kalenderauswahl und Ganztägig-
 *    Einstellung wurden dort getroffen).
 * 2. Sonst Rückfall auf die Kalender der Uhr: zuerst der Wear-Provider (dort synchronisiert
 *    Google die Kalender des Telefons), dann der Standard-Provider.
 */
class EventSource(private val context: Context) {

    private val repo = CalendarRepository(context)

    /** true, wenn Termine vom Handy vorliegen. */
    fun hasPhoneData(): Boolean = EventStore.load(context) != null

    /** false = weder Handy-Daten noch Kalender-Berechtigung: es kann nichts angezeigt werden. */
    fun canRead(): Boolean = hasPhoneData() || repo.hasPermission()

    fun load(from: Long, to: Long): List<CalendarEvent> {
        EventStore.load(context)?.let { snap ->
            return snap.events.filter { it.end > from && it.begin < to }.sortedBy { it.begin }
        }
        return repo.loadEvents(from, to)
    }
}

/** Liest die Kalender der Uhr (Fallback, wenn keine Handy-App eingerichtet ist). */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Nur Termine mit Uhrzeit, die das Fenster [from, to) berühren. */
    fun loadEvents(from: Long, to: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val wear = query(WEAR_INSTANCES, from, to)
        return wear.ifEmpty { query(Instances.CONTENT_URI, from, to) }
    }

    private fun query(base: Uri, from: Long, to: Long): List<CalendarEvent> {
        val uri = base.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        // Der Wear-Provider kennt weder Kalendernamen noch Konto – daher nur gemeinsame Spalten.
        val projection = arrayOf(
            "event_id", Instances.TITLE, Instances.BEGIN, Instances.END, Instances.EVENT_LOCATION,
            Instances.ALL_DAY, Instances.SELF_ATTENDEE_STATUS, Instances.STATUS,
            Instances.EVENT_COLOR, Instances.CALENDAR_COLOR,
        )
        val result = ArrayList<CalendarEvent>()
        try {
            context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")
                ?.use { readAll(it, result) }
        } catch (_: Exception) {
            // Provider nicht vorhanden / Berechtigung entzogen
        }
        return result
    }

    private fun readAll(c: Cursor, out: MutableList<CalendarEvent>) {
        while (c.moveToNext()) {
            if (c.getInt(5) == 1) continue // ganztägig
            if (c.getInt(6) == Attendees.ATTENDEE_STATUS_DECLINED) continue
            if (c.getInt(7) == Events.STATUS_CANCELED) continue
            val begin = c.getLong(2)
            val end = c.getLong(3)
            val color = (c.getInt(8).takeIf { it != 0 } ?: c.getInt(9).takeIf { it != 0 })
                ?: Color.parseColor("#4FC3F7")
            out += CalendarEvent(
                id = c.getLong(0),
                title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)",
                begin = begin,
                end = if (end >= begin) end else begin,
                location = c.getString(4)?.takeIf { it.isNotBlank() },
                calendarName = null,
                color = color or 0xFF000000.toInt(),
            )
        }
    }

    private companion object {
        /** Kalender-Provider der Wear-OS-Systemdienste (WearableCalendarContract). */
        val WEAR_INSTANCES: Uri =
            Uri.parse("content://com.google.android.wearable.provider.calendar/instances/when")
    }
}
