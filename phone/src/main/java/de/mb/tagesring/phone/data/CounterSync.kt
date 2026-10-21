package de.mb.tagesring.phone.data

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.mb.tagesring.sync.SyncContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

/** Letzter Stand der Benachrichtigungszähler und Versand an die Uhr. */
object CounterSync {

    val current = MutableStateFlow(Counters())

    suspend fun push(context: Context, counters: Counters = current.value) {
        val request = PutDataMapRequest.create(SyncContract.PATH_COUNTERS).apply {
            dataMap.putBoolean(SyncContract.C_CONNECTED, counters.connected)
            dataMap.putInt(SyncContract.C_PHONE, counters.phone)
            dataMap.putInt(SyncContract.C_WHATSAPP, counters.whatsapp)
            dataMap.putInt(SyncContract.C_MAIL, counters.mail)
            dataMap.putInt(SyncContract.C_OTHER, counters.other)
            dataMap.putString(SyncContract.C_PACKAGES, counters.packages.entries.joinToString(",") { "${it.key}=${it.value}" })
            dataMap.putLong(SyncContract.C_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
