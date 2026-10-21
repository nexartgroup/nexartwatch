package de.mb.tagesring.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.mb.tagesring.R
import de.mb.tagesring.data.InfoStore
import de.mb.tagesring.health.HealthSync
import de.mb.tagesring.sync.InfoType
import de.mb.tagesring.ui.SetupActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

private fun plain(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

private val HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * Eine der beiden Zusatzinfos neben dem nächsten Termin. Welche Art angezeigt wird, wählt die Handy-App
 * ([InfoStore]); auf der Uhr lässt sich das Feld über „Anpassen“ auch auf jede andere Komplikation umstellen.
 */
abstract class InfoService(private val index: Int) : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return build(InfoType.STEPS, "4.231", "Schritte")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val context = applicationContext
        val now = System.currentTimeMillis()
        return when (InfoStore.slot(context, index)) {
            InfoType.NONE -> NoDataComplicationData()
            InfoType.STEPS -> {
                val r = InfoStore.steps(context)
                build(InfoType.STEPS, r?.let { String.format(Locale.GERMANY, "%,d", it.value) } ?: "–", "Schritte", needsPermission = true)
            }
            InfoType.HEART_RATE -> {
                val r = InfoStore.heartRate(context)?.takeIf { now - it.at < 60 * 60_000L }
                build(InfoType.HEART_RATE, r?.value?.toString() ?: "–", "Puls", needsPermission = true)
            }
            InfoType.BATTERY_PHONE -> {
                val b = InfoStore.phoneBattery(context)
                build(InfoType.BATTERY_PHONE, if (b < 0) "–" else "$b%", "Akku Handy")
            }
            InfoType.ALARM -> {
                val at = InfoStore.alarmAt(context)
                build(InfoType.ALARM, if (at <= now) "–" else alarmText(at, now), "Wecker")
            }
            InfoType.WEEK -> build(InfoType.WEEK, "KW " + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), "Kalenderwoche")
        }
    }

    private fun alarmText(at: Long, now: Long): String {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val time = HOUR_MIN.format(t)
        return if (at - now < 20 * 3_600_000L) time else "${WEEKDAYS[t.dayOfWeek.value - 1]} $time"
    }

    private fun build(type: InfoType, text: String, description: String, needsPermission: Boolean = false): ComplicationData {
        val icon = when (type) {
            InfoType.STEPS -> R.drawable.ic_info_steps
            InfoType.HEART_RATE -> R.drawable.ic_info_heart
            InfoType.BATTERY_PHONE -> R.drawable.ic_info_battery
            InfoType.ALARM -> R.drawable.ic_info_alarm
            else -> R.drawable.ic_info_week
        }
        val builder = ShortTextComplicationData.Builder(plain(text), plain(description))
            .setTitle(plain(description))
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithResource(applicationContext, icon)).build())
        if (needsPermission && !HealthSync.hasPermissions(applicationContext)) {
            // Ohne Berechtigung führt ein Tipp zur Einrichtungsseite der Uhr-App
            builder.setTapAction(
                PendingIntent.getActivity(
                    applicationContext, 40 + index, Intent(applicationContext, SetupActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }
}

class Info1Service : InfoService(1)
class Info2Service : InfoService(2)
