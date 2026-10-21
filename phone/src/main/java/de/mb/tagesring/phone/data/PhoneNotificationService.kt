package de.mb.tagesring.phone.data

import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Zählt die aktiven Benachrichtigungen auf dem Handy (verpasste Anrufe, WhatsApp, Mail, Rest)
 * und schickt die Zahlen an die Uhr. Der Nutzer muss den Zugriff einmalig in den Einstellungen
 * unter "Benachrichtigungszugriff" erlauben.
 *
 * Wichtig für den Tiefschlaf (Doze): Es gibt bewusst keine Verzögerung/Bündelung per Handler,
 * denn deren Uhr läuft bei ausgeschaltetem Bildschirm nicht weiter – die Zahlen kämen erst, wenn
 * man das Handy aufweckt. Stattdessen wird sofort gezählt und gesendet, solange ein kurzer
 * Wake-Lock die CPU wach hält.
 */
class PhoneNotificationService : NotificationListenerService() {

    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tagesring:counters")
    }

    override fun onListenerConnected() = recount()

    override fun onListenerDisconnected() {
        publish(Counters(connected = false), force = true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = try {
            activeNotifications?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        publish(
            NotificationCounter.count(
                active, packageName,
                debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            ),
            force = false,
        )
    }

    private fun publish(counters: Counters, force: Boolean) {
        if (!force && counters == CounterSync.current.value && counters.connected) return
        CounterSync.current.value = counters
        runCatching { wakeLock.acquire(WAKE_MS) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CounterSync.push(applicationContext, counters)
            } catch (_: Exception) {
                // Uhr nicht erreichbar – beim nächsten Abgleich wieder
            } finally {
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
            }
        }
    }

    private companion object {
        const val WAKE_MS = 15_000L
    }
}
