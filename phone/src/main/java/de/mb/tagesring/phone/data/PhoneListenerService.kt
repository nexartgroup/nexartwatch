package de.mb.tagesring.phone.data

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.mb.tagesring.sync.SyncContract
import kotlinx.coroutines.runBlocking

/** Nachrichten der Uhr-App: Terminabgleich anfordern, Installationsergebnis, Status. */
class PhoneListenerService : WearableListenerService() {

    override fun onMessageReceived(message: MessageEvent) {
        when (message.path) {
            SyncContract.PATH_REQUEST -> runBlocking {
                try {
                    WatchSync.push(applicationContext)
                    WatchSync.pushTheme(applicationContext)
                    if (CounterSync.current.value.connected) CounterSync.push(applicationContext)
                } catch (_: Exception) {
                }
            }

            SyncContract.PATH_INSTALL_RESULT -> {
                WatchState.installMessage.value = String(message.data)
                android.util.Log.i("TagesringPhone", "Uhr meldet: ${String(message.data)}")
            }
            SyncContract.PATH_STATUS -> {
                WatchState.parse(String(message.data))
                android.util.Log.i("TagesringPhone", "Uhr-Status: ${String(message.data)}")
            }
        }
    }
}
