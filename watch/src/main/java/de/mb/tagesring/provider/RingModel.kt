package de.mb.tagesring.provider

import android.content.Context
import android.graphics.Color
import androidx.wear.watchface.complications.data.WeightedElementsComplicationData
import de.mb.tagesring.data.CalendarEvent
import de.mb.tagesring.data.EventSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Berechnet, wie die heutigen Termine auf den 24-h-Ring kommen.
 *
 * Eine "gewichtete Elemente"-Komplikation darf höchstens 7 Elemente haben (Lücken zählen mit).
 * Deshalb werden die Termine einer Spur auf mehrere Komplikationen ("Chunks") verteilt, die alle
 * denselben Ring zeichnen; die Lücken sind transparent. Überschneidende Termine kommen in
 * eine zweite Spur (innerer, dünnerer Ring).
 */
object RingModel {
    const val DAY_MINUTES = 1440f
    const val MAX_ELEMENTS = 7
    private const val MIN_MINUTES = 10f
    /**
     * Lücken zwischen Terminen. Komplett transparent (0) geht nicht: die Watchface-Laufzeit macht
     * daraus "#0" und stürzt beim Parsen ab. Alpha >= 16 liefert 8 Hex-Ziffern; Schwarz mit
     * Alpha 16 ist auf dem schwarzen Zifferblatt unsichtbar.
     */
    const val GAP = 0x10000000

    /** Spuren: Spur 0 = Hauptring, Spur 1 = überlappende Termine. Je Spur eine Liste von Chunks. */
    data class Layout(val lanes: List<List<List<WeightedElementsComplicationData.Element>>>)

    data class Placed(val event: CalendarEvent, val start: Float, val end: Float)

    fun compute(context: Context, now: Long = System.currentTimeMillis()): Layout {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = EventSource(context).load(from, to).filter { !it.allDay }
        return layout(events, zone, today, now)
    }

    /** Ganztägige Termine von heute als gleich große, farbige Abschnitte eines dünnen Außenrings. */
    fun allDayElements(context: Context): List<WeightedElementsComplicationData.Element> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return EventSource(context).load(from, to).filter { it.allDay }
            .sortedWith(compareBy({ it.color }, { it.title }))
            .take(MAX_ELEMENTS)
            .map { WeightedElementsComplicationData.Element(1f, vivid(it.color)) }
    }

    /** Anzahl der Komplikationen je Spur (das Watchface hat nur 8 Slots, zwei davon gehören den Zusatzinfos). */
    const val A_CHUNKS = 2
    const val B_CHUNKS = 1

    fun layout(events: List<CalendarEvent>, zone: ZoneId, today: LocalDate, now: Long): Layout {
        val placed = events.mapNotNull { e ->
            val s = minutesOfDay(e.begin, zone, today)
            val en = minutesOfDay(e.end, zone, today)
            if (s >= DAY_MINUTES || en <= 0f) null else Placed(e, s, max(en, min(DAY_MINUTES, s + MIN_MINUTES)))
        }.sortedWith(compareBy({ it.start }, { it.end }))

        // Spuren zuweisen (Greedy): erster Termin, der nicht überlappt, bleibt in Spur 0
        val laneEnds = mutableListOf(-1f, -1f)
        val lanes = listOf(mutableListOf<Placed>(), mutableListOf<Placed>())
        for (p in placed) {
            val lane = laneEnds.indexOfFirst { it <= p.start }.let { if (it < 0) 1 else it }
            lanes[lane] += p
            laneEnds[lane] = max(laneEnds[lane], p.end)
        }

        // Zu viele Termine für die Komplikationen einer Spur: erst vergangene, dann späte Termine in die
        // andere Spur verschieben (wenn dort Platz ist) oder weglassen
        while (splitChunks(lanes[0]).size > A_CHUNKS) {
            val victim = dropCandidate(lanes[0], now)
            lanes[0].remove(victim)
            if (lanes[1].none { overlaps(it, victim) }) lanes[1].add(victim)
            lanes[1].sortWith(compareBy({ it.start }, { it.end }))
        }
        while (splitChunks(lanes[1]).size > B_CHUNKS) lanes[1].remove(dropCandidate(lanes[1], now))
        return Layout(lanes.map { l -> splitChunks(l).map { toElements(it, now) } })
    }

    private fun overlaps(a: Placed, b: Placed) = a.start < b.end && b.start < a.end

    /** Am wenigsten wichtig: der früheste vergangene Termin, sonst der späteste kommende. */
    private fun dropCandidate(events: List<Placed>, now: Long): Placed =
        events.filter { it.event.end <= now }.minByOrNull { it.start } ?: events.maxByOrNull { it.start }!!

    /** Verteilt die Termine einer Spur auf Komplikationen mit je höchstens [MAX_ELEMENTS] Elementen. */
    private fun splitChunks(events: List<Placed>): List<List<Placed>> {
        val chunks = mutableListOf<List<Placed>>()
        var current = mutableListOf<Placed>()
        for (e in events) {
            val candidate = current + e
            if (elementCount(candidate) > MAX_ELEMENTS && current.isNotEmpty()) {
                chunks += current
                current = mutableListOf(e)
            } else {
                current = candidate.toMutableList()
            }
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun elementCount(events: List<Placed>): Int {
        var n = 0
        var cursor = 0f
        for (e in events) {
            if (e.start > cursor + 0.01f) n++      // Lücke davor
            n++                                    // Termin
            cursor = e.end
        }
        if (cursor < DAY_MINUTES - 0.01f) n++      // Lücke danach
        return n
    }

    private fun toElements(events: List<Placed>, now: Long): List<WeightedElementsComplicationData.Element> {
        val out = mutableListOf<WeightedElementsComplicationData.Element>()
        var cursor = 0f
        for (e in events) {
            if (e.start > cursor + 0.01f) out += WeightedElementsComplicationData.Element(e.start - cursor, GAP)
            val len = max(0.5f, e.end - max(e.start, cursor))
            val past = e.event.end <= now
            out += WeightedElementsComplicationData.Element(len, if (past) dim(e.event.color) else vivid(e.event.color))
            cursor = max(cursor, e.start) + len
        }
        if (cursor < DAY_MINUTES - 0.01f) out += WeightedElementsComplicationData.Element(DAY_MINUTES - cursor, GAP)
        return out
    }

    fun minutesOfDay(ms: Long, zone: ZoneId, today: LocalDate): Float {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val date = t.toLocalDate()
        return when {
            date.isBefore(today) -> 0f
            date.isAfter(today) -> DAY_MINUTES
            else -> t.toLocalTime().toSecondOfDay() / 60f
        }
    }

    /** Dunkle Kalenderfarben für schwarzen Hintergrund aufhellen; die Sättigung bleibt (damit die Palette stimmt). */
    fun vivid(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = max(hsv[2], 0.85f)
        return Color.HSVToColor(hsv)
    }

    /** Vergangene Termine: deckend abgedunkelt. */
    fun dim(color: Int): Int {
        val c = vivid(color)
        return Color.rgb((Color.red(c) * 0.38f).toInt(), (Color.green(c) * 0.38f).toInt(), (Color.blue(c) * 0.38f).toInt())
    }
}
