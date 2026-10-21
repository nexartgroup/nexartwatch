package de.mb.tagesring.phone.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nur in Debug-Builds: Funktionen per adb auslösen, ohne die Oberfläche zu bedienen.
 *   adb shell am broadcast -n de.mb.tagesring/.phone.data.DebugReceiver --es action sync|calendars|events|status|install|select|allday
 *   (Ausgabe im Logcat unter dem Tag TagesringPhone.)
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val action = intent.getStringExtra("action") ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val settings = Settings(app)
            val out = try {
                when (action) {
                    "sync" -> "gesendet: ${WatchSync.push(app)}"
                    "calendars" -> CalendarSource(app).calendars().joinToString("\n") { "${it.id} | ${it.account} | ${it.name}" }
                    "events" -> WatchSync.collect(app).joinToString("\n") { "${it.id} | ${it.title} | ${it.allDay} | ${it.calendarName}" }
                    "status" -> WatchLink.connection(app).let { c ->
                        WatchLink.requestStatus(app)
                        "verbunden=${c.connected.map { it.displayName }} mitApp=${c.withApp.map { it.displayName }}"
                    }
                    "counters" -> CounterSync.current.value.let { c ->
                        CounterSync.push(app, c)
                        "Zähler: $c"
                    }
                    // fake=Anrufe,WhatsApp,Mail,Sonstige – nur für die Vorschau-Tests
                    "fake" -> intent.getStringExtra("value").orEmpty().split(",").map { it.trim().toIntOrNull() ?: 0 }.let { n ->
                        CounterSync.current.value = Counters(true, n.getOrElse(0) { 0 }, n.getOrElse(1) { 0 }, n.getOrElse(2) { 0 }, n.getOrElse(3) { 0 })
                        "Zähler gesetzt: ${CounterSync.current.value}"
                    }
                    "install" -> "Empfänger: ${WatchLink.requestInstall(app)}"
                    // select=1,2,3 | all
                    "select" -> {
                        val v = intent.getStringExtra("value").orEmpty()
                        settings.selectedCalendars = if (v == "all") null else v.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
                        "Auswahl: ${settings.selectedCalendars}"
                    }
                    // color: "Kalender-ID:#RRGGBB" oder "Kalender-ID:none"
                    "color" -> intent.getStringExtra("value").orEmpty().split(":", limit = 2).let { (id, c) ->
                        settings.setCalendarColor(id.toLong(), if (c == "none") null else android.graphics.Color.parseColor(c))
                        "Farben: ${settings.calendarColors}"
                    }
                    "theme" -> { WatchSync.pushTheme(app); "Theme gesendet: ${settings.theme.toList()}" }
                    "rainbow" -> {
                        settings.colorMode = if (intent.getStringExtra("value") == "1") Settings.MODE_PALETTE else Settings.MODE_CALENDAR
                        "Farbmodus: ${settings.colorMode}"
                    }
                    "allday" -> {
                        settings.showAllDay = intent.getStringExtra("value") == "1"
                        "Ganztägig: ${settings.showAllDay}"
                    }
                    else -> "?"
                }
            } catch (e: Exception) {
                "Fehler: $e"
            }
            Log.i("TagesringPhone", "$action -> $out")
            pending.finish()
        }
    }
}
