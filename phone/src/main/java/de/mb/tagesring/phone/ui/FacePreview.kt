package de.mb.tagesring.phone.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.mb.tagesring.phone.R
import de.mb.tagesring.phone.data.Counters
import de.mb.tagesring.phone.data.PhoneEvent
import de.mb.tagesring.phone.data.PhoneInfo
import de.mb.tagesring.sync.InfoType
import de.mb.tagesring.sync.Theme
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import java.time.temporal.IsoFields
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vorschau des Watchfaces mit den aktuellen Einstellungen (Farben, Regenbogen, Kalenderauswahl, Zähler).
 * Zeichnet dieselbe Geometrie wie `tools/gen_watchface.py` im 450x450-Raster. Tippen schaltet zwischen
 * normaler Anzeige und Ruhemodus (AOD) um. Wetter und Akku sind Beispielwerte.
 */
@Composable
fun WatchFacePreview(state: UiState, counters: Counters, modifier: Modifier = Modifier) {
    var ambient by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }
    val icons = FaceIcons(
        ImageBitmap.imageResource(R.drawable.ic_phone),
        ImageBitmap.imageResource(R.drawable.ic_whatsapp),
        ImageBitmap.imageResource(R.drawable.ic_mail),
        ImageBitmap.imageResource(R.drawable.wx_partly_day),
        ImageBitmap.imageResource(R.drawable.ic_logo_n),
        ImageBitmap.imageResource(R.drawable.ic_logo_n_mono),
        mapOf(
            InfoType.STEPS to painterResource(R.drawable.ic_info_steps),
            InfoType.HEART_RATE to painterResource(R.drawable.ic_info_heart),
            InfoType.BATTERY_PHONE to painterResource(R.drawable.ic_info_battery),
            InfoType.ALARM to painterResource(R.drawable.ic_info_alarm),
            InfoType.WEEK to painterResource(R.drawable.ic_info_week),
        ),
    )
    val context = LocalContext.current
    val paints = remember { FacePaints(context) }
    val events = state.events
    val model = remember(events, state.theme, counters, now, state.infoSlot1, state.infoSlot2, state.phoneInfo) {
        FaceModel.build(events, state.theme, counters, now, listOf(state.infoSlot1, state.infoSlot2), state.phoneInfo)
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.size(224.dp).clickable { ambient = !ambient }) {
            val s = size.width / 450f
            withTransform({ scale(s, s, pivot = Offset.Zero) }) {
                drawCircle(Color.Black, radius = 225f, center = Offset(225f, 225f))
                if (ambient) drawAmbient(model, icons, paints) else drawActive(model, icons, paints)
            }
        }
        Text(
            if (ambient) "Vorschau Ruhemodus (AOD) · tippen für normale Ansicht"
            else "Vorschau · tippen für Ruhemodus (AOD) · Wetter, Akku, Schritte, Puls: Beispiel",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------- Daten

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private class FaceIcons(
    val phone: ImageBitmap, val whatsapp: ImageBitmap, val mail: ImageBitmap, val weather: ImageBitmap,
    val logo: ImageBitmap, val logoMono: ImageBitmap,
    val info: Map<InfoType, Painter>,
)

/** IBM Plex Mono wie auf dem Watchface (und auf nexart.de). */
private class FacePaints(private val context: android.content.Context) {
    private fun paint(res: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, res) ?: Typeface.MONOSPACE
    }
    val light = paint(R.font.ibm_plex_mono_light)
    val regular = paint(R.font.ibm_plex_mono_regular)
    val medium = paint(R.font.ibm_plex_mono_medium)
    val semibold = paint(R.font.ibm_plex_mono_semibold)
}

private class Placed(val color: Int, val startMin: Float, val endMin: Float)

private class FaceModel(
    val now: Long,
    val minuteOfDay: Float,
    val theme: Theme,
    val lane0: List<Placed>,
    val lane1: List<Placed>,
    val allDay: List<Int>,
    val label: String,
    val title: String,
    val range: String,
    val info: String,
    val counters: Counters,
    val time: String,
    val weekday: String,
    val date: String,
    /** Art und Text der beiden Zusatzinfos (null = Feld leer). */
    val infos: List<Pair<InfoType, String>?>,
) {
    companion object {
        fun infoText(type: InfoType, phone: PhoneInfo, now: Long): String? = when (type) {
            InfoType.NONE -> null
            InfoType.STEPS -> "4.231"          // Beispielwert: die Uhr liefert den echten
            InfoType.HEART_RATE -> "72"        // Beispielwert
            InfoType.BATTERY_PHONE -> if (phone.battery < 0) "–" else "${phone.battery}%"
            InfoType.ALARM -> if (phone.alarmAt <= now) "–" else {
                val a = Instant.ofEpochMilli(phone.alarmAt).atZone(ZoneId.systemDefault())
                if (phone.alarmAt - now < 20 * 3_600_000L) HM.format(a) else WEEKDAYS[a.dayOfWeek.value - 1] + " " + HM.format(a)
            }
            InfoType.WEEK -> "KW " + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }

        fun build(
            events: List<PhoneEvent>, theme: Theme, counters: Counters, now: Long,
            infoTypes: List<InfoType>, phone: PhoneInfo,
        ): FaceModel {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayMin = 1440f
            val timed = events.filter { !it.allDay && it.end > from && it.begin < to }
            val placed = timed.map {
                val s = max(0f, (it.begin - from) / 60_000f)
                val e = min(dayMin, (it.end - from) / 60_000f)
                Placed(if (it.end <= now) dim(it.color) else vivid(it.color), s, max(e, min(dayMin, s + 10f)))
            }.sortedWith(compareBy({ it.startMin }, { it.endMin }))
            val laneEnds = floatArrayOf(-1f, -1f)
            val lanes = listOf(mutableListOf<Placed>(), mutableListOf())
            for (p in placed) {
                val lane = laneEnds.indexOfFirst { it <= p.startMin }.let { if (it < 0) 1 else it }
                lanes[lane] += p
                laneEnds[lane] = max(laneEnds[lane], p.endMin)
            }
            val allDay = events.filter { it.allDay && it.begin < to && it.end > from }
                .sortedWith(compareBy({ it.color }, { it.title })).take(7).map { vivid(it.color) }

            // Nächster Termin wie auf der Uhr: laufender/nächster mit Uhrzeit, sonst ganztägiger von heute
            val next = events.filter { !it.allDay && it.end > now }.minWithOrNull(compareBy({ it.begin }, { it.end }))
            val allDayToday = events.firstOrNull { it.allDay && it.begin < to && it.end > now }
            val label: String
            val title: String
            val range: String
            val info: String
            val ev = when {
                next != null && next.begin < to -> next
                allDayToday != null -> allDayToday
                else -> next
            }
            if (ev == null) {
                label = "HEUTE"; title = "Keine Termine"; range = ""; info = ""
            } else {
                label = when {
                    ev.allDay -> "GANZTÄGIG"
                    ev.begin >= to -> "MORGEN"
                    ev.begin <= now -> "JETZT · noch " + duration(ev.end - now)
                    else -> "NÄCHSTER · in " + duration(ev.begin - now)
                }
                title = ev.title
                range = if (ev.allDay) "ganztägig" else
                    HM.format(Instant.ofEpochMilli(ev.begin).atZone(zone)) + "-" + HM.format(Instant.ofEpochMilli(ev.end).atZone(zone))
                info = listOfNotNull(ev.calendarName.takeIf { it.isNotBlank() && '@' !in it }, ev.location?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            }
            val local = Instant.ofEpochMilli(now).atZone(zone)
            return FaceModel(
                now, local.hour * 60f + local.minute, theme, lanes[0], lanes[1], allDay,
                label, title, range, info, counters, HM.format(local),
                WEEKDAYS[local.dayOfWeek.value - 1], DATE.format(local),
                infoTypes.map { type -> infoText(type, phone, now)?.let { type to it } },
            )
        }

        private fun duration(ms: Long): String {
            val m = max(1L, (ms + 59_999L) / 60_000L)
            return if (m < 60) "$m Min." else "${m / 60} Std."
        }

        /** Dunkle Kalenderfarben für schwarzen Grund aufhellen (wie auf der Uhr); die Sättigung bleibt. */
        fun vivid(color: Int): Int {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[2] = max(hsv[2], 0.85f)
            return android.graphics.Color.HSVToColor(hsv)
        }

        fun dim(color: Int): Int {
            val c = vivid(color)
            return android.graphics.Color.rgb(
                (android.graphics.Color.red(c) * 0.38f).toInt(),
                (android.graphics.Color.green(c) * 0.38f).toInt(),
                (android.graphics.Color.blue(c) * 0.38f).toInt(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------- Geometrie

private const val C = 225f
private const val RING_R = 212.4f
private const val BAND = 14.4f
private const val BAND_B = 6.5f
private const val RING_B_R = RING_R - BAND / 2 - 2.5f - BAND_B / 2
private const val ALLDAY_R = 221.5f
private const val ALLDAY_T = 3.2f
private const val TRACK = 3.2f
private const val TICK_R = 192f
private const val NUMERAL_R = 176f

private const val Y_TIME = 111.15f
private const val TITLE_W = 186f
private const val Y_INFO1 = 213.75f
private const val Y_INFO2 = 240.75f
private const val Y_DATE = 160f
private const val Y_LABEL = 189.45f
private const val Y_TITLE = 213.75f
private const val Y_RANGE = 240.75f
private const val Y_INFO = 266.4f
private const val Y_ROW = 329.4f
private const val Y_NOTIF = 371f
private const val Y_PILL = 411.3f
private const val X0 = 76.5f
private const val TEXT_W = 297f

private fun c(argb: Int) = Color(argb)

/** Winkel für [hour] Uhr: 0 Uhr unten, im Uhrzeigersinn (Compose: 0° = 3 Uhr, im Uhrzeigersinn positiv). */
private fun angle(hour: Float) = 90f + 15f * hour

private fun point(r: Float, hour: Float): Offset {
    val a = Math.toRadians(angle(hour).toDouble())
    return Offset(C + r * cos(a).toFloat(), C + r * sin(a).toFloat())
}

private fun DrawScope.arc(r: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float) {
    if (sweepDeg <= 0f) return
    drawArc(color, startDeg, sweepDeg, false, Offset(C - r, C - r), Size(2 * r, 2 * r), style = Stroke(width))
}

/** Die vier Viertel des Rings mit Lücken (unten 12°, sonst 1,8°) – wie auf dem Watchface. */
private fun DrawScope.quarters(color: Color, width: Float, filledDeg: Float?) {
    for (q in 0..3) {
        val gs = if (q == 0) 12f else 1.8f
        val ge = if (q == 3) 12f else 1.8f
        val start = 90f + 90f * q + gs / 2
        val sweep = 90f - gs / 2 - ge / 2
        val s = if (filledDeg == null) sweep else (filledDeg - 90f * q - gs / 2).coerceIn(0f, sweep)
        arc(RING_R, start, s, color, width)
    }
}

private fun DrawScope.drawText(
    paint: TextPaint, s: String, x: Float, cy: Float, size: Float, color: Int,
    align: Paint.Align = Paint.Align.LEFT, maxWidth: Float? = null,
) {
    if (s.isEmpty()) return
    paint.textSize = size
    paint.color = color
    paint.textAlign = align
    val text = if (maxWidth != null) TextUtils.ellipsize(s, paint, maxWidth, TextUtils.TruncateAt.END).toString() else s
    drawContext.canvas.nativeCanvas.drawText(text, x, cy + size * 0.36f, paint)
}

/** Wochentag und Datum in einer Zeile, mittig (Plex Mono: 12 px pro Zeichen bei Größe 20). */
private fun DrawScope.drawDate(weekdayPaint: TextPaint, datePaint: TextPaint, m: FaceModel, color: Int) {
    val left = C - 156f / 2
    drawText(weekdayPaint, m.weekday, left, Y_DATE, 20f, color)
    drawText(datePaint, m.date, left + 36f, Y_DATE, 20f, color)
}

private const val INFO_ACCENT = 0xFF3FA9F5.toInt()
private const val INFO_TEXT = 0xFFDFE6EE.toInt()

/** Die beiden Zusatzinfos rechts neben dem Termin: Symbol + Wert. */
private fun DrawScope.drawInfoCells(m: FaceModel, icons: FaceIcons, paint: TextPaint, iconColor: Int, textColor: Int) {
    listOf(Y_INFO1, Y_INFO2).forEachIndexed { i, cy ->
        val (type, text) = m.infos.getOrNull(i) ?: return@forEachIndexed
        icons.info[type]?.let { painter ->
            withTransform({ translate(270f, cy - 10f) }) {
                with(painter) { draw(Size(20f, 20f), colorFilter = ColorFilter.tint(c(iconColor))) }
            }
        }
        drawText(paint, text, 295f, cy, 19f, textColor, maxWidth = 90f)
    }
}

private fun DrawScope.drawIcon(img: ImageBitmap, cx: Float, cy: Float, size: Float, color: Int, alpha: Float = 1f) {
    drawImage(
        img, dstOffset = IntOffset((cx - size / 2).toInt(), (cy - size / 2).toInt()),
        dstSize = IntSize(size.toInt(), size.toInt()), alpha = alpha, colorFilter = ColorFilter.tint(c(color)),
    )
}

// ---------------------------------------------------------------------------------------------- Normale Ansicht

private fun DrawScope.drawActive(m: FaceModel, icons: FaceIcons, p: FacePaints) {
    val t = m.theme
    val hours = m.minuteOfDay / 60f

    quarters(c(t.track), TRACK, null)
    quarters(c(t.progress), TRACK, 15f * hours)
    for (e in m.lane0) arc(RING_R, angle(e.startMin / 60f), 15f * (e.endMin - e.startMin) / 60f, c(e.color), BAND)
    for (e in m.lane1) arc(RING_B_R, angle(e.startMin / 60f), 15f * (e.endMin - e.startMin) / 60f, c(e.color), BAND_B)
    if (m.allDay.isNotEmpty()) {
        val step = 360f / m.allDay.size
        m.allDay.forEachIndexed { i, col -> arc(ALLDAY_R, 90f + i * step, step, c(col), ALLDAY_T) }
    }

    // Stundenmarken: 3/9/15/21 kräftig, übrige fein; 0/6/12/18 als Zahlen
    for (h in 0 until 24) {
        if (h % 6 == 0) continue
        val major = h % 3 == 0
        drawLine(
            c(t.marks).copy(alpha = if (major) 1f else 0.55f), point(TICK_R, h.toFloat()),
            point(TICK_R - if (major) 10f else 5f, h.toFloat()), strokeWidth = if (major) 2.8f else 1.5f,
        )
    }
    for ((h, label) in listOf(6 to "6", 18 to "18")) {
        val o = point(NUMERAL_R, h.toFloat())
        drawText(p.regular, label, o.x, o.y, 21f, t.marks, Paint.Align.CENTER)
    }
    val zero = point(RING_R, 0f)
    drawText(p.regular, "0", zero.x, zero.y, 20f, t.marks, Paint.Align.CENTER)
    // neXart-Logo bei 12 Uhr (oben) statt der Zahl 12
    drawImage(icons.logo, dstOffset = IntOffset(211, 35), dstSize = IntSize(28, 28))

    // Marker "jetzt"
    val now = point(RING_R, hours)
    drawCircle(Color.Black, 7.4f, now)
    drawCircle(Color.White, 5.4f, now)

    drawText(p.semibold, m.time, C, Y_TIME, 77f, t.time, Paint.Align.CENTER)
    drawDate(p.semibold, p.regular, m, t.date)
    drawText(p.regular, m.label, X0, Y_LABEL, 16f, t.label, maxWidth = TEXT_W)
    drawText(p.semibold, m.title, X0, Y_TITLE, 21f, t.title, maxWidth = TITLE_W)
    drawText(p.regular, m.range, X0, Y_RANGE, 21f, t.date, maxWidth = TITLE_W)
    drawText(p.regular, m.info, X0, Y_INFO, 18f, t.marks, maxWidth = TEXT_W)
    drawInfoCells(m, icons, p.medium, INFO_ACCENT, INFO_TEXT)

    // Wetter (Beispiel) und Akku (Beispiel)
    drawIcon(icons.weather, 104f, Y_ROW, 28f, t.text)
    drawText(p.regular, "21°C", 127f, Y_ROW, 25f, t.text)
    drawText(p.regular, "80%", 0.745f * 450f, Y_ROW, 25f, t.text, Paint.Align.RIGHT)
    val bx = 0.765f * 450f
    drawRoundRect(c(t.text), Offset(bx, Y_ROW - 7.65f), Size(27.9f, 15.3f), androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(1.6f))
    drawRoundRect(c(t.text), Offset(bx + 29.2f, Y_ROW - 3.6f), Size(2.7f, 7.2f), androidx.compose.ui.geometry.CornerRadius(1f))
    drawRoundRect(c(t.text), Offset(bx + 3f, Y_ROW - 4.65f), Size(21.9f * 0.8f, 9.3f), androidx.compose.ui.geometry.CornerRadius(1.4f))

    // Zähler: große Symbole, Zahl direkt daneben
    val cn = m.counters
    val items = listOf(icons.phone to cn.phone, icons.whatsapp to cn.whatsapp, icons.mail to cn.mail)
    val xs = listOf(140f, 215f, 290f)
    items.forEachIndexed { i, (img, n) ->
        val active = cn.connected && n > 0
        drawIcon(img, xs[i], Y_NOTIF, 30f, t.text, if (active) 1f else 120f / 255f)
        val nx = xs[i] + 19f
        if (!cn.connected) drawText(p.medium, "–", nx, Y_NOTIF, 22f, t.marks)
        else if (n > 0) drawText(p.medium, min(n, 99).toString(), nx, Y_NOTIF, 22f, t.text)
    }
    if (cn.connected && cn.other > 0) {
        val w = if (cn.other >= 10) 60f else 46f
        drawRoundRect(c(t.badge), Offset(C - w / 2, Y_PILL - 15f), Size(w, 30f), androidx.compose.ui.geometry.CornerRadius(15f))
        drawText(p.medium, min(cn.other, 99).toString(), C, Y_PILL, 21f, t.text, Paint.Align.CENTER)
    }
}

// ---------------------------------------------------------------------------------------------- Ruhemodus

private const val A_TIME = 0xFFFFFFFF.toInt()
private const val A_TEXT = 0xFFD0D0D0.toInt()
private const val A_MARKS = 0xFF909090.toInt()
private const val A_TRACK = 0xFF606060.toInt()
private const val A_LABEL = 0xFFA8A8A8.toInt()
private const val A_RANGE = 0xFFA0A0A0.toInt()
private const val A_INFO = 0xFF808080.toInt()

private fun DrawScope.drawAmbient(m: FaceModel, icons: FaceIcons, p: FacePaints) {
    val hours = m.minuteOfDay / 60f
    for (q in 0..3) {
        val gs = if (q == 0) 12f else 1.8f
        val ge = if (q == 3) 12f else 1.8f
        arc(RING_R, 90f + 90f * q + gs / 2, 90f - gs / 2 - ge / 2, c(A_TRACK), 1.6f)
    }
    for (e in m.lane0) arc(RING_R, angle(e.startMin / 60f), 15f * (e.endMin - e.startMin) / 60f, Color.White.copy(alpha = 0.92f), 4.5f)
    for (e in m.lane1) arc(RING_B_R, angle(e.startMin / 60f), 15f * (e.endMin - e.startMin) / 60f, Color.White.copy(alpha = 0.92f), 2.2f)
    if (m.allDay.isNotEmpty()) arc(ALLDAY_R, 90f, 360f, Color.White.copy(alpha = 0.86f), 2f)

    for (h in 0 until 24) {
        if (h % 3 != 0 || h % 6 == 0) continue
        drawLine(c(A_MARKS), point(TICK_R, h.toFloat()), point(TICK_R - 10f, h.toFloat()), strokeWidth = 1.6f)
    }
    for ((h, label) in listOf(6 to "6", 18 to "18", 0 to "0")) {
        val o = if (h == 0) point(RING_R, 0f) else point(NUMERAL_R, h.toFloat())
        drawText(p.light, label, o.x, o.y, 21f, A_MARKS, Paint.Align.CENTER)
    }
    drawImage(icons.logoMono, dstOffset = IntOffset(212, 36), dstSize = IntSize(26, 26), alpha = 0.8f)
    drawLine(Color.White, point(RING_R - 14f, hours), point(RING_R + 14f, hours), strokeWidth = 2.4f)

    drawText(p.light, m.time, C, Y_TIME, 72f, A_TIME, Paint.Align.CENTER)
    drawDate(p.regular, p.light, m, A_TEXT)
    drawText(p.light, m.label, X0, Y_LABEL, 16f, A_LABEL, maxWidth = TEXT_W)
    drawText(p.regular, m.title, X0, Y_TITLE, 21f, A_TIME, maxWidth = TITLE_W)
    drawText(p.light, m.range, X0, Y_RANGE, 21f, A_RANGE, maxWidth = TITLE_W)
    drawText(p.light, m.info, X0, Y_INFO, 18f, A_INFO, maxWidth = TEXT_W)
    drawInfoCells(m, icons, p.light, 0xFFFFFFFF.toInt(), A_TEXT)
    drawText(p.light, "21°C", 90f, Y_ROW, 25f, A_TEXT)
    drawText(p.light, "80%", 0.80f * 450f, Y_ROW, 25f, A_TEXT, Paint.Align.RIGHT)

    val cn = m.counters
    val items = listOf(icons.phone to cn.phone, icons.whatsapp to cn.whatsapp, icons.mail to cn.mail)
    val xs = listOf(140f, 215f, 290f)
    items.forEachIndexed { i, (img, n) ->
        drawIcon(img, xs[i], Y_NOTIF, 26f, A_TIME, if (cn.connected && n > 0) 0.94f else 0.43f)
        if (cn.connected && n > 0) drawText(p.regular, min(n, 99).toString(), xs[i] + 17f, Y_NOTIF, 21f, A_TIME)
    }
    if (cn.connected && cn.other > 0) drawText(p.regular, "·  ${min(cn.other, 99)}  ·", C, Y_PILL, 20f, A_TEXT, Paint.Align.CENTER)
}
