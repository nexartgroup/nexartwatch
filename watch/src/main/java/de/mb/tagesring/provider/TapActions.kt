package de.mb.tagesring.provider

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import de.mb.tagesring.data.CalendarEvent

/** Tipp-Aktionen der Komplikationen: Kalender öffnen bzw. genau einen Termin. */
object TapActions {

    private const val SAMSUNG_CALENDAR = "com.samsung.android.calendar"
    private const val EVENT_MIME = "vnd.android.cursor.item/event"

    /** Öffnet die Kalender-App der Uhr. */
    fun calendar(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Explizites Ziel (Samsung Kalender), sonst blockiert Android implizite PendingIntents
        val samsung = ComponentName(SAMSUNG_CALENDAR, "com.samsung.android.app.calendar.view.daily.DailyActivity")
        if (context.packageManager.getActivityInfoOrNull(samsung) != null) intent.component = samsung
        return PendingIntent.getActivity(context, 1, intent, FLAGS)
    }

    /**
     * Öffnet genau diesen Termin. Samsung Kalender zeigt Termine über die Event-ID des
     * Kalender-Providers; ohne ID (oder ohne Samsung Kalender) wird der Kalender geöffnet.
     */
    fun event(context: Context, e: CalendarEvent): PendingIntent {
        if (e.id <= 0) return calendar(context)
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, EVENT_MIME)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val samsung = ComponentName(SAMSUNG_CALENDAR, "com.samsung.android.app.calendar.view.detail.DetailActivity")
        if (context.packageManager.getActivityInfoOrNull(samsung) != null) intent.component = samsung
        // Eigene Request-Nummer je Termin, damit sich die PendingIntents nicht überschreiben
        return PendingIntent.getActivity(context, (e.id % 100_000).toInt() + 100, intent, FLAGS)
    }

    private fun android.content.pm.PackageManager.getActivityInfoOrNull(c: ComponentName) = try {
        getActivityInfo(c, 0)
    } catch (_: Exception) {
        null
    }

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
