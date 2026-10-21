package de.mb.tagesring.phone.data

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Werte des Handys für die Zusatzinfos: Ladestand und nächster Wecker. */
data class PhoneInfo(val battery: Int, val charging: Boolean, val alarmAt: Long) {
    companion object {
        fun read(context: Context): PhoneInfo {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val alarm = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime ?: 0L
            return PhoneInfo(if (level < 0) -1 else level * 100 / scale, charging, alarm)
        }
    }
}
