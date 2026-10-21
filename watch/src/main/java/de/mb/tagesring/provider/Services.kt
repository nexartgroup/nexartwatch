package de.mb.tagesring.provider

import android.content.Context
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.data.WeightedElementsComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.mb.tagesring.data.CalendarEvent
import de.mb.tagesring.data.EventSource
import de.mb.tagesring.data.NotificationStore
import de.mb.tagesring.data.ThemeStore
import de.mb.tagesring.sync.Theme
import de.mb.tagesring.sync.PhoneLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.min

private fun text(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

/** Ein einziges, praktisch unsichtbares Element: hält den Slot "belegt", damit Tipps ankommen. */
private val INVISIBLE = listOf(WeightedElementsComplicationData.Element(1f, RingModel.GAP))

/**
 * Zwei Textzeilen samt Theme-Farben: Das Watchface liest den Text aus Titel/Text und die Farben aus
 * den "gewichteten Elementen" (ein Element je Farbe, Gewicht 1). So kommen Farben der Handy-App
 * ohne eigene Datenquelle zum Watchface (Komplikationen liefern nur sieben Farben).
 */
private fun lines(
    context: Context, title: ComplicationText?, body: ComplicationText, description: String, colors: IntArray,
    tap: android.app.PendingIntent?,
): ComplicationData =
    WeightedElementsComplicationData.Builder(
        colors.map { WeightedElementsComplicationData.Element(1f, it) }, text(description),
    )
        .setTitle(title)
        .setText(body)
        .setTapAction(tap)
        .build()

// ------------------------------------------------------------------ Termine am Rand

/**
 * Liefert einen Teil der Tagestermine als "gewichtete Elemente" (Gewicht = Minuten).
 * Das Watchface zeichnet sie als farbige Segmente auf dem 24-h-Ring. Tipp = Kalender öffnen.
 */
abstract class RingService(private val lane: Int, private val chunk: Int) :
    SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.WEIGHTED_ELEMENTS) return null
        return build(
            listOf(
                WeightedElementsComplicationData.Element(540f, RingModel.GAP),
                WeightedElementsComplicationData.Element(90f, 0xFF66D24F.toInt()),
                WeightedElementsComplicationData.Element(810f, RingModel.GAP),
            ),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Refresh.ensureObserver(applicationContext)
        val layout = withContext(Dispatchers.IO) { RingModel.compute(applicationContext) }
        Refresh.scheduleNextBoundary(applicationContext)
        // Auch ohne Termine ein (unsichtbares) Element liefern: Slots ohne Daten hätten keine
        // Tipp-Aktion, liegen aber vollflächig oben und würden Tipps auf die Uhr verschlucken.
        val elements = layout.lanes.getOrNull(lane)?.getOrNull(chunk) ?: INVISIBLE
        return build(elements)
    }

    private fun build(elements: List<WeightedElementsComplicationData.Element>) =
        WeightedElementsComplicationData.Builder(elements, text("Termine heute"))
            .setElementBackgroundColor(RingModel.GAP)
            .setText(text("Termine"))
            .setTapAction(TapActions.calendar(applicationContext))
            .build()
}

class RingA1Service : RingService(0, 0)
class RingA2Service : RingService(0, 1)
class RingB1Service : RingService(1, 0)

/** Dünner Außenring für ganztägige Termine (nur wenn in der Handy-App eingeschaltet). */
class AllDayRingService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.WEIGHTED_ELEMENTS) return null
        return build(listOf(WeightedElementsComplicationData.Element(1f, 0xFFE94FB5.toInt())))
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val elements = withContext(Dispatchers.IO) { RingModel.allDayElements(applicationContext) }
        return build(elements.ifEmpty { INVISIBLE })
    }

    private fun build(elements: List<WeightedElementsComplicationData.Element>) =
        WeightedElementsComplicationData.Builder(elements, text("Ganztägige Termine"))
            .setElementBackgroundColor(RingModel.GAP)
            .setText(text("Ganztägig"))
            .setTapAction(TapActions.calendar(applicationContext))
            .build()
}

// ------------------------------------------------------------------ Nächster Termin

private object NextEvent {
    private val HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm")

    class Info(val event: CalendarEvent, val running: Boolean, val tomorrow: Boolean)

    /**
     * Laufender oder nächster Termin mit Uhrzeit (heute, sonst morgen). Gibt es heute keine
     * Termine mit Uhrzeit mehr, zählt ein ganztägiger Termin von heute.
     */
    fun find(source: EventSource, now: Long): Info? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = source.load(dayStart, today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())

        val timed = events.filter { !it.allDay && it.end > now }
            .minWithOrNull(compareBy({ it.begin }, { it.end }))
        if (timed != null) {
            val tomorrow = timed.begin >= dayEnd
            if (!tomorrow) return Info(timed, timed.begin <= now, false)
            // Heute nichts mehr mit Uhrzeit: ganztägiger Termin von heute hat Vorrang vor "morgen"
            events.firstOrNull { it.allDay && it.begin < dayEnd && it.end > now }
                ?.let { return Info(it, true, false) }
            return Info(timed, false, true)
        }
        return events.firstOrNull { it.allDay && it.begin < dayEnd && it.end > now }
            ?.let { Info(it, true, false) }
    }

    fun range(e: CalendarEvent): String {
        if (e.allDay) return "ganztägig"
        val zone = ZoneId.systemDefault()
        return HOUR_MIN.format(Instant.ofEpochMilli(e.begin).atZone(zone)) + "-" +
            HOUR_MIN.format(Instant.ofEpochMilli(e.end).atZone(zone))
    }

    fun info(e: CalendarEvent): String? =
        listOfNotNull(e.calendarName?.takeIf { '@' !in it }, e.location)
            .joinToString(" · ").ifEmpty { null }
}

/** Zeile 1: "NÄCHSTER · in 39 Min" (Titelfeld), Zeile 2: Titel des Termins (Textfeld). */
class NextTitleService : SuspendingComplicationDataSourceService() {

    private fun colors() = ThemeStore.theme(applicationContext).titleGroup()

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.WEIGHTED_ELEMENTS) return null
        return lines(applicationContext, text("NÄCHSTER · in 39 Min"), text("Team-Meeting"), "Termin", Theme.DEFAULT.titleGroup(), null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val context = applicationContext
        val source = EventSource(context)
        Refresh.ensureObserver(context)
        askPhoneIfStale(context)
        val tap = TapActions.calendar(context)
        if (!source.canRead()) {
            return lines(context, text("KALENDER"), text("Kein Zugriff"), "Kalender", colors(), tap)
        }
        val info = withContext(Dispatchers.IO) { NextEvent.find(source, System.currentTimeMillis()) }
        Refresh.scheduleNextBoundary(context)
        if (info == null) {
            return lines(context, text("HEUTE"), text("Keine Termine"), "Keine Termine", colors(), tap)
        }

        val label: ComplicationText = when {
            info.tomorrow -> text("MORGEN")
            info.event.allDay -> text("GANZTÄGIG")
            else -> {
                val target = if (info.running) info.event.end else info.event.begin
                TimeDifferenceComplicationText.Builder(
                    TimeDifferenceStyle.SHORT_WORDS_SINGLE_UNIT,
                    CountDownTimeReference(Instant.ofEpochMilli(target)),
                )
                    .setText(if (info.running) "JETZT · noch ^1" else "NÄCHSTER · in ^1")
                    .setMinimumTimeUnit(TimeUnit.MINUTES)
                    .build()
            }
        }
        return lines(context, label, text(info.event.title), "Nächster Termin", colors(), TapActions.event(context, info.event))
    }

    /** Hat die Uhr lange nichts vom Handy gehört, bitten wir die Handy-App um einen Abgleich. */
    private suspend fun askPhoneIfStale(context: Context) {
        val last = de.mb.tagesring.data.EventStore.load(context)?.receivedAt ?: 0L
        if (System.currentTimeMillis() - last > 30 * 60_000L) PhoneLink.requestSync(context)
    }
}

// ------------------------------------------------------------------ Zähler

/**
 * Alle vier Zähler in einem Bereichswert, weil ein Watchface nur 8 Komplikationen hat:
 *   min = Anrufe*100 + WhatsApp,  value = min + (Mail*100 + Sonstige),
 *   max = value + 1 (Zähler unbekannt) oder + 2 (Zähler aktiv).
 * Das Watchface rechnet das mit Ausdrücken wieder auseinander.
 */
class CounterService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE) return null
        return build(2, 5, 3, 12, true, Theme.DEFAULT)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val context = applicationContext
        val s = NotificationStore.snapshot(context)
        // Zeile 3 und 4 des nächsten Termins (Uhrzeit, Kalender · Ort) zeichnet das Watchface in dieser Hauptebene
        val next = withContext(Dispatchers.IO) {
            if (EventSource(context).canRead()) NextEvent.find(EventSource(context), System.currentTimeMillis()) else null
        }
        val range = next?.let { NextEvent.range(it.event) } ?: ""
        val info = next?.let { NextEvent.info(it.event) } ?: ""
        return build(s.phone, s.whatsapp, s.mail, s.other, s.connected, ThemeStore.theme(context), range, info)
    }

    private fun build(
        phone: Int, whatsapp: Int, mail: Int, other: Int, connected: Boolean, theme: Theme,
        range: String = "15:00-19:00", info: String = "Familie · Ort",
    ): ComplicationData {
        val a = pack(phone, whatsapp)
        val b = pack(mail, other)
        val value = (a + b).toFloat()
        // Komplikationstexte dürfen nicht leer sein
        fun nb(s: String) = text(s.ifBlank { "\u00A0" })
        return RangedValueComplicationData.Builder(
            value, a.toFloat(), value + if (connected) 2f else 1f, text("Benachrichtigungen"),
        ).setText(nb(range)).setTitle(nb(info))
            // Farben der Hauptebene des Watchfaces (siehe Theme)
            .setColorRamp(ColorRamp(mainColors(theme), false))
            .setTapAction(TapActions.calendar(applicationContext))
            .build()
    }

    private fun pack(x: Int, y: Int) = min(x, 99) * 100 + min(y, 99)

    /**
     * Ring-Spur und Fortschritt liegen unter den Termin-Komplikationen, deren Lücken-Elemente (schwarz, Alpha 16)
     * sie dreimal um je 6 % abdunkeln (0,937³ = 0,824). Beide Farben werden deshalb vorab aufgehellt,
     * damit sie auf der Uhr so aussehen wie in der Handy-App.
     */
    private fun mainColors(theme: Theme): IntArray {
        val c = theme.main()
        for (i in intArrayOf(3, 4)) c[i] = brighten(c[i], 1f / 0.824f)
        return c
    }

    private fun brighten(color: Int, k: Float): Int {
        fun ch(v: Int) = min(255, (v * k + 0.5f).toInt())
        return android.graphics.Color.argb(
            android.graphics.Color.alpha(color), ch(android.graphics.Color.red(color)),
            ch(android.graphics.Color.green(color)), ch(android.graphics.Color.blue(color)),
        )
    }
}
