package de.mb.tagesring.phone.data

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import de.mb.tagesring.sync.SyncContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Letzter bekannter Stand der Uhr (kommt als Nachricht von der Uhr-App). */
object WatchState {
    data class Status(
        val version: String,
        val faceSupported: Boolean,
        val faceInstalled: Boolean,
        val faceUpToDate: Boolean,
        val faceActive: Boolean,
        val canActivate: Boolean,
        val eventsAt: Long,
        val eventCount: Int,
        val error: String,
    )

    val status = MutableStateFlow<Status?>(null)

    /** Ergebnis der letzten Installation ("Watchface installiert …"). */
    val installMessage = MutableStateFlow("")

    fun parse(json: String) {
        try {
            val o = JSONObject(json)
            status.value = Status(
                version = o.optString("version"),
                faceSupported = o.optBoolean("faceSupported"),
                faceInstalled = o.optBoolean("faceInstalled"),
                faceUpToDate = o.optBoolean("faceUpToDate"),
                faceActive = o.optBoolean("faceActive"),
                canActivate = o.optBoolean("canActivate"),
                eventsAt = o.optLong("eventsAt"),
                eventCount = o.optInt("eventCount"),
                error = o.optString("error"),
            )
        } catch (_: Exception) {
        }
    }
}

/** Verbindung zur Uhr über den Wearable Data Layer. */
object WatchLink {

    private const val CAPABILITY_WATCH_APP = "tagesring_watch"

    data class Connection(
        /** Verbundene Uhren, auf denen die Uhr-App installiert ist. */
        val withApp: List<Node>,
        /** Alle verbundenen Uhren. */
        val connected: List<Node>,
    ) {
        val hasWatch get() = connected.isNotEmpty()
        val hasApp get() = withApp.isNotEmpty()
    }

    suspend fun connection(context: Context): Connection = try {
        val withApp = Wearable.getCapabilityClient(context)
            .getCapability(CAPABILITY_WATCH_APP, CapabilityClient.FILTER_REACHABLE).await().nodes.toList()
        val connected = Wearable.getNodeClient(context).connectedNodes.await()
        Connection(withApp, connected)
    } catch (_: Exception) {
        Connection(emptyList(), emptyList())
    }

    /** Schickt eine Nachricht an alle Uhren mit installierter Uhr-App. @return Anzahl Empfänger */
    suspend fun send(context: Context, path: String, data: ByteArray = ByteArray(0)): Int {
        val nodes = connection(context).withApp
        val client = Wearable.getMessageClient(context)
        nodes.forEach { client.sendMessage(it.id, path, data).await() }
        return nodes.size
    }

    suspend fun requestStatus(context: Context) = send(context, SyncContract.PATH_STATUS_REQUEST)

    suspend fun requestInstall(context: Context) = send(context, SyncContract.PATH_INSTALL)
}
