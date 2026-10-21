package de.mb.tagesring.phone.data

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Gleicht die Termine im Hintergrund mit der Uhr ab (regelmäßig und bei Kalenderänderungen). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            WatchSync.pushTheme(applicationContext)   // gleiche Daten werden vom Data Layer nicht doppelt zugestellt
            WatchSync.push(applicationContext)
            keepCountersAlive(applicationContext)
        } catch (_: Exception) {
            // Uhr nicht erreichbar – beim nächsten Lauf wieder
        }
        // Nach einer Änderung wieder auf die nächste warten
        SyncScheduler.watchCalendarChanges(applicationContext)
        return Result.success()
    }
}

/**
 * Samsung & Co. beenden Listener-Dienste gern im Hintergrund: Ist der Zugriff erlaubt, der Dienst aber
 * nicht verbunden, wird er neu angefordert; sonst gehen die Zähler regelmäßig erneut an die Uhr.
 */
private suspend fun keepCountersAlive(context: Context) {
    val enabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    if (!enabled) return
    if (!CounterSync.current.value.connected) {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(context, PhoneNotificationService::class.java),
            )
        } catch (_: Exception) {
        }
    } else {
        CounterSync.push(context)
    }
}

object SyncScheduler {
    private const val PERIODIC = "tagesring_periodic_sync"
    private const val ON_CHANGE = "tagesring_calendar_change"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build(),
        )
        watchCalendarChanges(context)
    }

    /** Wird ausgelöst, sobald sich im Kalender-Provider etwas ändert. */
    fun watchCalendarChanges(context: Context) {
        if (!CalendarSource(context).hasPermission()) return
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(10, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ON_CHANGE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
    }
}
