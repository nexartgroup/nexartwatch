package de.mb.tagesring.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Verbindung zum Handy: bittet die Handy-App, die Termine zu senden. */
object PhoneLink {

    /** @return Anzahl der erreichten Handys, -1 bei Fehler (z. B. keine Play-Dienste). */
    suspend fun requestSync(context: Context): Int = try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val messages = Wearable.getMessageClient(context)
        nodes.forEach { messages.sendMessage(it.id, SyncContract.PATH_REQUEST, ByteArray(0)).await() }
        nodes.size
    } catch (_: Exception) {
        -1
    }
}
