package de.mb.tagesring.phone.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Liest Kalender und Termine aus dem Kalender-Provider des Handys. */
class CalendarSource(private val context: Context) {

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Alle sichtbaren Kalender, nach Konto und Name sortiert. */
    fun calendars(): List<PhoneCalendar> {
        if (!hasPermission()) return emptyList()
        val result = ArrayList<PhoneCalendar>()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR, Calendars.VISIBLE,
        )
        context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(4) == 0) continue
                result += PhoneCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { "Kalender" },
                    account = c.getString(2).orEmpty(),
                    color = c.getInt(3) or 0xFF000000.toInt(),
                )
            }
        }
        return result.sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }))
    }

    /**
     * Termine der gewählten Kalender im Fenster [von, bis).
     * @param calendarIds null = alle sichtbaren Kalender
     * @param colorOverrides vom Nutzer gewählte Farben je Kalender-ID
     */
    fun events(
        from: Long,
        to: Long,
        calendarIds: Set<Long>?,
        includeAllDay: Boolean,
        colorOverrides: Map<Long, Int> = emptyMap(),
    ): List<PhoneEvent> {
        if (!hasPermission()) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.EVENT_LOCATION,
            Instances.ALL_DAY, Instances.SELF_ATTENDEE_STATUS, Instances.STATUS,
            Instances.EVENT_COLOR, Instances.CALENDAR_COLOR, Instances.CALENDAR_DISPLAY_NAME,
            Instances.CALENDAR_ID, Instances.VISIBLE,
        )
        val zone = ZoneId.systemDefault()
        val out = ArrayList<PhoneEvent>()
        context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(12) == 0) continue
                if (calendarIds != null && c.getLong(11) !in calendarIds) continue
                if (c.getInt(6) == Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (c.getInt(7) == Events.STATUS_CANCELED) continue
                val allDay = c.getInt(5) == 1
                if (allDay && !includeAllDay) continue
                var begin = c.getLong(2)
                var end = c.getLong(3)
                if (allDay) {
                    // Ganztägige Termine stehen im Provider in UTC -> lokale Tagesgrenzen
                    val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    val last: LocalDate = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                    begin = first.atStartOfDay(zone).toInstant().toEpochMilli()
                    end = last.atStartOfDay(zone).toInstant().toEpochMilli()
                    if (end <= begin) end = first.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    if (end <= from || begin >= to) continue
                }
                val color = colorOverrides[c.getLong(11)]
                    ?: (c.getInt(8).takeIf { it != 0 } ?: c.getInt(9).takeIf { it != 0 })
                    ?: Color.parseColor("#4FC3F7")
                out += PhoneEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)",
                    begin = begin,
                    end = if (end >= begin) end else begin,
                    location = c.getString(4)?.takeIf { it.isNotBlank() },
                    calendarName = c.getString(10).orEmpty(),
                    color = color or 0xFF000000.toInt(),
                    allDay = allDay,
                )
            }
        }
        return out
    }
}
