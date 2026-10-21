package de.mb.tagesring.phone.data

import android.content.Context
import android.graphics.Color
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.mb.tagesring.sync.SyncContract
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

/** Schickt die Termine der gewählten Kalender an die Uhr. */
object WatchSync {

    /** Termine ab heute 0 Uhr für 3 Tage (heute, morgen, übermorgen), mit den Farben der Kalender. */
    fun collectRaw(context: Context): List<PhoneEvent> {
        val settings = Settings(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        return CalendarSource(context)
            .events(from, to, settings.selectedCalendars, settings.showAllDay, settings.calendarColors)
            .sortedWith(compareBy({ it.begin }, { it.end }, { it.title }))
            .take(MAX_EVENTS)
    }

    /** Wendet bei Bedarf die Palette an (sonst bleiben die Kalenderfarben). */
    fun colorize(events: List<PhoneEvent>, mode: Int, paletteStart: Int): List<PhoneEvent> =
        if (mode == Settings.MODE_PALETTE) palette(events, paletteStart) else events

    /** Wie [collectRaw], aber mit den Farben, die auch die Uhr bekommt. */
    fun collect(context: Context): List<PhoneEvent> {
        val settings = Settings(context)
        return colorize(collectRaw(context), settings.colorMode, settings.paletteStart)
    }

    /**
     * Palette: Die Termine bekommen der Reihe nach (in zeitlicher Reihenfolge) die Farben von [Palette.COLORS];
     * nach der letzten Farbe geht es wieder von vorn los. Aufeinanderfolgende Termine haben so immer
     * deutlich verschiedene, zueinander passende Farben.
     */
    fun palette(events: List<PhoneEvent>, start: Int): List<PhoneEvent> =
        events.mapIndexed { i, e -> e.copy(color = Palette.COLORS[(start + i) % Palette.COLORS.size]) }

    /** @return Anzahl der gesendeten Termine, -1 wenn keine Berechtigung */
    suspend fun push(context: Context): Int {
        if (!CalendarSource(context).hasPermission()) return -1
        val events = collect(context)
        val now = System.currentTimeMillis()
        val request = PutDataMapRequest.create(SyncContract.PATH_EVENTS).apply {
            dataMap.putLong(SyncContract.KEY_SYNCED_AT, now)
            dataMap.putDataMapArrayList(
                SyncContract.KEY_EVENTS,
                ArrayList(events.map { e ->
                    com.google.android.gms.wearable.DataMap().apply {
                        putLong(SyncContract.E_ID, e.id)
                        putString(SyncContract.E_TITLE, e.title)
                        putLong(SyncContract.E_BEGIN, e.begin)
                        putLong(SyncContract.E_END, e.end)
                        putString(SyncContract.E_LOCATION, e.location.orEmpty())
                        putString(SyncContract.E_CALENDAR, e.calendarName)
                        putInt(SyncContract.E_COLOR, e.color)
                        putBoolean(SyncContract.E_ALL_DAY, e.allDay)
                    }
                }),
            )
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        Settings(context).apply {
            lastSyncAt = now
            lastSyncCount = events.size
        }
        return events.size
    }

    /** Schickt die Farben des Watchfaces an die Uhr. */
    suspend fun pushTheme(context: Context) {
        val theme = Settings(context).theme
        val request = PutDataMapRequest.create(SyncContract.PATH_THEME).apply {
            dataMap.putIntegerArrayList(SyncContract.T_COLORS, ArrayList(theme.toList()))
            dataMap.putLong(SyncContract.T_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    private const val MAX_EVENTS = 150
}
