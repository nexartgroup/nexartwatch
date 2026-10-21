package de.mb.tagesring.provider

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import de.mb.tagesring.data.CalendarRepository
import de.mb.tagesring.data.EventSource
import java.time.LocalDate
import java.time.ZoneId

/** Stößt Aktualisierungen der Komplikationen an (Kalenderänderung, Terminwechsel, Zähler). */
object Refresh {

    private val ALL = listOf(
        RingA1Service::class.java, RingA2Service::class.java,
        RingB1Service::class.java, AllDayRingService::class.java,
        NextTitleService::class.java, CounterService::class.java,
        Info1Service::class.java, Info2Service::class.java,
    )

    private var observing = false
    private var pending = false

    fun requestAll(context: Context) {
        ALL.forEach { requestOne(context, it) }
    }

    fun requestCounters(context: Context) = requestOne(context, CounterService::class.java)

    fun requestInfo(context: Context) {
        requestOne(context, Info1Service::class.java)
        requestOne(context, Info2Service::class.java)
    }

    private fun requestOne(context: Context, service: Class<*>) {
        try {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, service))
                .requestUpdateAll()
        } catch (_: Exception) {
            // keine aktive Komplikation – nichts zu tun
        }
    }

    /** Aktualisiert sofort, sobald sich der Kalender ändert (solange der Prozess lebt). */
    @Synchronized
    fun ensureObserver(context: Context) {
        if (observing) return
        val app = context.applicationContext
        if (!CalendarRepository(app).hasPermission()) return
        val handler = Handler(Looper.getMainLooper())
        try {
            app.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI, true,
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        if (pending) return
                        pending = true
                        handler.postDelayed({ pending = false; requestAll(app) }, 3_000)
                    }
                },
            )
            observing = true
        } catch (_: SecurityException) {
        }
    }

    /** Alarm zum nächsten Terminbeginn/-ende bzw. Mitternacht, damit "JETZT"/"NÄCHSTER" stimmt. */
    fun scheduleNextBoundary(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val events = EventSource(app).load(from, midnight).filter { !it.allDay }
        val next = (events.flatMap { listOf(it.begin, it.end) } + midnight)
            .filter { it > now + 30_000 }
            .minOrNull() ?: midnight

        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            app, 0, Intent(app, RefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, intent)
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Refresh.requestAll(context)
}
