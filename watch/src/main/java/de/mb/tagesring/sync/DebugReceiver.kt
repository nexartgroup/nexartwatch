package de.mb.tagesring.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import de.mb.tagesring.data.CalendarEvent
import de.mb.tagesring.data.EventStore
import de.mb.tagesring.provider.Refresh
import android.util.Log
import de.mb.tagesring.push.WatchFaceInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Nur in Debug-Builds: Termine per adb einspielen, ohne Handy-App.
 *   adb shell am broadcast -n de.mb.tagesring/.sync.DebugReceiver --es json '[{"title":..}]'
 *   (Leere Liste "[]" löscht die Handy-Termine wieder.)
 *   adb shell am broadcast -n de.mb.tagesring/.sync.DebugReceiver --es counters 2,5,3,12   (Anrufe,WhatsApp,Mail,Sonstige)
 *   adb shell am broadcast -n de.mb.tagesring/.sync.DebugReceiver --es action install|activate|remove|status
 *   (Ergebnis im Logcat unter dem Tag TagesringDebug.)
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        intent.getStringExtra("action")?.let { action ->
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                val installer = WatchFaceInstaller(context)
                val result = when (action) {
                    "install" -> installer.installOrUpdate()
                    "activate" -> installer.setActive()
                    "remove" -> installer.remove()
                    // Test: Watchface-APK aus filesDir/wf_test.apk mit Token aus dem Extra "token"
                    "install_file" -> installer.installOrUpdate(intent.getStringExtra("token").orEmpty()) {
                        java.io.File(context.filesDir, "wf_test.apk").inputStream()
                    }
                    else -> installer.status().toString()
                }
                Log.i("TagesringDebug", "$action: $result")
                pending.finish()
            }
            return
        }
        intent.getStringExtra("theme")?.let { t ->
            val colors = t.split(',').map { android.graphics.Color.parseColor(it.trim()) }
            de.mb.tagesring.data.ThemeStore.save(context, de.mb.tagesring.sync.Theme.fromList(colors))
            Refresh.requestAll(context)
            return
        }
        intent.getStringExtra("counters")?.let { c ->
            val n = c.split(',').map { it.trim().toIntOrNull() ?: 0 } + listOf(0, 0, 0, 0)
            de.mb.tagesring.data.NotificationStore.update(
                context,
                de.mb.tagesring.data.NotificationSnapshot(true, n[0], n[1], n[2], n[3], mapOf("test" to n.take(4).sum())),
            )
            Refresh.requestCounters(context)
            return
        }
        val json = intent.getStringExtra("json") ?: return
        val arr = JSONArray(json)
        if (arr.length() == 0) {
            EventStore.clear(context)
        } else {
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CalendarEvent(
                    id = o.optLong("id"),
                    title = o.getString("title"),
                    begin = o.getLong("begin"),
                    end = o.getLong("end"),
                    location = o.optString("location").ifEmpty { null },
                    calendarName = o.optString("calendar").ifEmpty { null },
                    color = Color.parseColor(o.optString("color", "#4FC3F7")),
                    allDay = o.optBoolean("allDay"),
                )
            }
            EventStore.save(context, list)
        }
        Refresh.requestAll(context)
    }
}
