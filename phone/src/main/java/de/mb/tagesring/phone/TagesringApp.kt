package de.mb.tagesring.phone

import android.app.Application
import de.mb.tagesring.phone.data.SyncScheduler

class TagesringApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedule(this)
    }
}
