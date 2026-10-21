package de.mb.tagesring.provider

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import de.mb.tagesring.R
import de.mb.tagesring.data.InfoStore
import de.mb.tagesring.data.ThemeStore
import de.mb.tagesring.health.HealthSync
import de.mb.tagesring.ui.SetupActivity
import java.util.Locale

private fun plain(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

/**
 * Kennzeichen, an dem das Watchface unsere eigenen Zusatzinfos erkennt (Minimum des Bereichswerts):
 * nur dann liest es die Symbolfarbe aus dem Farbverlauf der Daten. Fremde Komplikationen behalten die Farben
 * ihres Symbols. (Muss mit tools/gen_watchface.py übereinstimmen.)
 */
const val INFO_MARK = -12345f

/**
 * Zusatzinfo rechts neben dem nächsten Termin. Die Komplikation lässt sich auf der Uhr über „Anpassen“ jederzeit
 * auf eine beliebige andere umstellen (Puls von Samsung Health, Timer, ...). Die Farbe des Symbols legt die
 * Handy-App fest ([de.mb.tagesring.sync.Theme.icon1] / `icon2`).
 */
abstract class InfoService(private val index: Int) : SuspendingComplicationDataSourceService() {

    protected abstract val icon: Int
    protected abstract val description: String
    protected abstract fun text(): String?

    private fun color(): Int {
        val theme = ThemeStore.theme(applicationContext)
        return if (index == 1) theme.icon1 else theme.icon2
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE || type == ComplicationType.SHORT_TEXT) {
            build(type, if (index == 1) "4.231" else "72")
        } else {
            null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, text() ?: "–")

    /**
     * Bereichswert mit Kennzeichen und Symbolfarbe (so zeigt das Watchface die gewählte Farbe). Ein Feld, das schon
     * früher als Kurztext angelegt wurde, bekommt weiterhin Kurztext (dann mit weißem Symbol).
     */
    private fun build(type: ComplicationType, text: String): ComplicationData {
        val image = MonochromaticImage.Builder(Icon.createWithResource(applicationContext, icon)).build()
        // Ohne Berechtigung führt ein Tipp zur Einrichtungsseite der Uhr-App
        val tap = if (HealthSync.hasPermissions(applicationContext)) null else PendingIntent.getActivity(
            applicationContext, 40 + index, Intent(applicationContext, SetupActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (type == ComplicationType.SHORT_TEXT) {
            return ShortTextComplicationData.Builder(plain(text), plain(description))
                .setTitle(plain(description)).setMonochromaticImage(image).setTapAction(tap).build()
        }
        val c = color()
        return RangedValueComplicationData.Builder(INFO_MARK, INFO_MARK, INFO_MARK + 1f, plain(description))
            .setText(plain(text)).setTitle(plain(description)).setMonochromaticImage(image)
            .setColorRamp(ColorRamp(intArrayOf(c, c), false)).setTapAction(tap).build()
    }
}

/** Oberes Feld: Schritte heute. */
class Info1Service : InfoService(1) {
    override val icon = R.drawable.ic_info_steps
    override val description = "Schritte"
    override fun text(): String? = InfoStore.steps(applicationContext)?.let { String.format(Locale.GERMANY, "%,d", it.value) }
}

/** Unteres Feld: zuletzt gemessener Puls (nicht älter als eine Stunde). */
class Info2Service : InfoService(2) {
    override val icon = R.drawable.ic_info_heart
    override val description = "Puls"
    override fun text(): String? =
        InfoStore.heartRate(applicationContext)?.takeIf { System.currentTimeMillis() - it.at < 60 * 60_000L }?.value?.toString()
}
