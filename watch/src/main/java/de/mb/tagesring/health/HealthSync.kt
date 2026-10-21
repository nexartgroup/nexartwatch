package de.mb.tagesring.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import de.mb.tagesring.data.InfoStore
import de.mb.tagesring.provider.Refresh

/** Schritte und Puls über Health Services: passives Mitlesen, Werte landen im [InfoStore]. */
object HealthSync {

    /** Ab Wear OS 6 verlangt Health Services für den Puls die Health-Berechtigung statt BODY_SENSORS. */
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_STEPS",
    )

    fun hasPermissions(context: Context) = PERMISSIONS.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Meldet die Uhr-App für Schritte und Puls an; liefert einen Statustext für die Einrichtungsseite. */
    suspend fun enable(context: Context): String {
        val app = context.applicationContext
        val status = try {
            val client = HealthServices.getClient(app).passiveMonitoringClient
            val caps = client.getCapabilitiesAsync().await()
            val wanted = setOf(DataType.STEPS_DAILY, DataType.HEART_RATE_BPM)
            val supported = wanted.filter { it in caps.supportedDataTypesPassiveMonitoring }
            if (supported.isEmpty()) {
                "Health Services bietet weder Schritte noch Puls an."
            } else {
                val config = PassiveListenerConfig.builder().setDataTypes(supported.toSet()).build()
                client.setPassiveListenerServiceAsync(HealthListenerService::class.java, config).await()
                client.flushAsync().await()      // aktuelle Werte sofort liefern lassen
                "Angemeldet: " + supported.joinToString { if (it == DataType.STEPS_DAILY) "Schritte" else "Puls" }
            }
        } catch (e: Exception) {
            "Health Services: ${e.message ?: e.javaClass.simpleName}"
        }
        InfoStore.saveHealthStatus(app, status)
        return status
    }
}

/** Empfängt neue Schritt- und Pulswerte (auch wenn die App nicht läuft) und aktualisiert die Zusatzinfos. */
class HealthListenerService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        android.util.Log.i("TagesringHealth", "Daten: " + dataPoints.dataTypes.joinToString { it.name })
        dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { InfoStore.saveSteps(this, it.value) }
        dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { InfoStore.saveHeartRate(this, Math.round(it.value)) }
        Refresh.requestInfo(applicationContext)
    }
}
