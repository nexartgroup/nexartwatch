package de.mb.tagesring.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.mb.tagesring.data.CalendarEvent
import de.mb.tagesring.data.EventStore
import de.mb.tagesring.data.NotificationSnapshot
import de.mb.tagesring.data.NotificationStore
import de.mb.tagesring.data.ThemeStore
import de.mb.tagesring.provider.Refresh
import de.mb.tagesring.push.WatchFaceInstaller
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Verbindung zur Handy-App: Termine empfangen, Watchface-Installation auslösen, Status melden. */
class PhoneSyncService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            val item = event.dataItem
            if (item.uri.path == SyncContract.PATH_THEME && event.type == DataEvent.TYPE_CHANGED) {
                val colors = DataMapItem.fromDataItem(item).dataMap
                    .getIntegerArrayList(SyncContract.T_COLORS).orEmpty()
                if (ThemeStore.save(this, Theme.fromList(colors))) Refresh.requestAll(applicationContext)
                continue
            }
            if (item.uri.path == SyncContract.PATH_COUNTERS && event.type == DataEvent.TYPE_CHANGED) {
                val m = DataMapItem.fromDataItem(item).dataMap
                val packages = m.getString(SyncContract.C_PACKAGES).orEmpty().split(',')
                    .mapNotNull { it.substringBefore('=').takeIf { k -> k.isNotBlank() }?.let { k -> k to (it.substringAfter('=').toIntOrNull() ?: 0) } }
                    .toMap()
                val changed = NotificationStore.update(
                    this,
                    NotificationSnapshot(
                        connected = m.getBoolean(SyncContract.C_CONNECTED),
                        phone = m.getInt(SyncContract.C_PHONE),
                        whatsapp = m.getInt(SyncContract.C_WHATSAPP),
                        mail = m.getInt(SyncContract.C_MAIL),
                        other = m.getInt(SyncContract.C_OTHER),
                        packages = packages,
                    ),
                )
                if (changed) Refresh.requestCounters(applicationContext)
                continue
            }
            if (item.uri.path != SyncContract.PATH_EVENTS) continue
            if (event.type == DataEvent.TYPE_DELETED) {
                EventStore.clear(this)
            } else {
                val map = DataMapItem.fromDataItem(item).dataMap
                val list = map.getDataMapArrayList(SyncContract.KEY_EVENTS).orEmpty().map {
                    CalendarEvent(
                        id = it.getLong(SyncContract.E_ID),
                        title = it.getString(SyncContract.E_TITLE).orEmpty(),
                        begin = it.getLong(SyncContract.E_BEGIN),
                        end = it.getLong(SyncContract.E_END),
                        location = it.getString(SyncContract.E_LOCATION)?.ifEmpty { null },
                        calendarName = it.getString(SyncContract.E_CALENDAR)?.ifEmpty { null },
                        color = it.getInt(SyncContract.E_COLOR),
                        allDay = it.getBoolean(SyncContract.E_ALL_DAY),
                    )
                }
                EventStore.save(this, list)
            }
            Refresh.requestAll(applicationContext)
        }
    }

    /** Läuft auf einem Hintergrund-Thread; blockierende Aufrufe sind hier in Ordnung. */
    override fun onMessageReceived(message: MessageEvent) {
        when (message.path) {
            SyncContract.PATH_INSTALL -> runBlocking {
                val installer = WatchFaceInstaller(this@PhoneSyncService)
                var result = installer.installOrUpdate()
                if (result.startsWith("Watchface") && installer.canActivate()) {
                    result += ". " + installer.setActive()
                } else if (result.startsWith("Watchface")) {
                    result += ". Zum Aktivieren die App neXart Watch auf der Uhr öffnen."
                }
                reply(message.sourceNodeId, SyncContract.PATH_INSTALL_RESULT, result.toByteArray())
            }

            SyncContract.PATH_STATUS_REQUEST -> runBlocking {
                reply(message.sourceNodeId, SyncContract.PATH_STATUS, statusJson().toByteArray())
            }
        }
    }

    private suspend fun statusJson(): String {
        val s = WatchFaceInstaller(this).status()
        val phone = EventStore.load(this)
        return JSONObject()
            .put("version", packageManager.getPackageInfo(packageName, 0).versionName)
            .put("faceSupported", s.supported)
            .put("faceInstalled", s.installed)
            .put("faceUpToDate", s.upToDate)
            .put("faceActive", s.active)
            .put("canActivate", s.canActivate)
            .put("eventsAt", phone?.receivedAt ?: 0L)
            .put("eventCount", phone?.events?.size ?: 0)
            .put("error", s.error ?: "")
            .toString()
    }

    private suspend fun reply(nodeId: String, path: String, data: ByteArray) {
        try {
            Wearable.getMessageClient(this).sendMessage(nodeId, path, data).await()
        } catch (_: Exception) {
        }
    }
}
