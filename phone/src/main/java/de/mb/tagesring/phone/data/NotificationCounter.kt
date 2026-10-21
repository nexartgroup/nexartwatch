package de.mb.tagesring.phone.data

import android.app.Notification
import android.service.notification.StatusBarNotification

/** Zähler der aktiven Benachrichtigungen auf dem Handy. */
data class Counters(
    /** false = Benachrichtigungszugriff nicht aktiv. */
    val connected: Boolean = false,
    val phone: Int = 0,
    val whatsapp: Int = 0,
    val mail: Int = 0,
    val other: Int = 0,
    /** Paketname -> Anzahl, nur zur Diagnose. */
    val packages: Map<String, Int> = emptyMap(),
)

/**
 * Ordnet aktive Benachrichtigungen den Kategorien Anrufe / WhatsApp / Mail / Sonstige zu
 * und zählt Nachrichten (nicht nur Benachrichtigungen) – z. B. eine WhatsApp-Konversation
 * mit 4 ungelesenen Nachrichten zählt als 4.
 */
object NotificationCounter {

    private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val MAIL = setOf(
        "com.google.android.gm", "com.google.android.gm.lite",
        "com.samsung.android.email.provider", "com.android.email",
        "com.microsoft.office.outlook", "com.fsck.k9", "net.thunderbird.android",
        "com.yahoo.mobile.client.android.mail", "de.gmx.mobile.android.mail",
        "de.web.mobile.android.mail", "ch.protonmail.android", "de.tutao.tutanota",
        "com.my.mail", "com.readdle.spark", "com.fastmail.mobile",
    )

    private val PHONE = setOf(
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer",
        "com.android.server.telecom", "com.android.phone", "com.samsung.android.incallui",
        "com.samsung.android.app.telephonyui",
    )

    private val IGNORED = setOf(
        "android", "com.android.systemui",
        // Kopplung/Brücke zur Uhr – das sind keine Nachrichten für den Nutzer
        "com.samsung.android.app.watchmanager", "com.samsung.android.waterplugin",
        "com.samsung.wearable.watch7plugin", "com.google.android.wearable.app",
    )

    private val NOT_MISSED_CALL = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_ALARM,
    )

    private const val CATEGORY_MISSED_CALL = "missed_call"
    private val LEADING_NUMBER = Regex("""^\s*(\d{1,3})\s+\p{L}""")

    /**
     * @param debuggable nur in Debug-Builds: Benachrichtigungen der Shell (adb) werden anhand des
     * Tags ("phone…", "wa…", "mail…") den Kategorien zugeordnet – zum Testen ohne echtes Telefon.
     */
    fun count(
        active: List<StatusBarNotification>,
        ownPackage: String,
        debuggable: Boolean = false,
    ): Counters {
        val byPackage = active
            .filter { counts(it, ownPackage) }
            .groupBy { packageOf(it, debuggable) }
            .mapValues { (pkg, list) -> countPackage(pkg, list) }
            .filterValues { it > 0 }

        var phone = 0
        var whatsapp = 0
        var mail = 0
        var other = 0
        byPackage.forEach { (pkg, n) ->
            when {
                pkg in WHATSAPP -> whatsapp += n
                pkg in PHONE -> phone += n
                pkg in MAIL -> mail += n
                else -> other += n
            }
        }
        return Counters(
            connected = true,
            phone = phone,
            whatsapp = whatsapp,
            mail = mail,
            other = other,
            packages = byPackage,
        )
    }

    private fun packageOf(sbn: StatusBarNotification, debuggable: Boolean): String {
        if (debuggable && sbn.packageName == "com.android.shell") {
            val tag = sbn.tag.orEmpty()
            return when {
                tag.startsWith("phone") -> "com.samsung.android.dialer"
                tag.startsWith("wa") -> "com.whatsapp"
                tag.startsWith("mail") -> "com.google.android.gm"
                else -> sbn.packageName
            }
        }
        return sbn.packageName
    }

    private fun counts(sbn: StatusBarNotification, ownPackage: String): Boolean {
        val pkg = sbn.packageName
        if (pkg == ownPackage || pkg in IGNORED) return false
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return false
        }
        if (pkg in PHONE) {
            val n = sbn.notification
            val channel = n.channelId.orEmpty()
            val isMissed = n.category == CATEGORY_MISSED_CALL || channel.contains("missed", ignoreCase = true)
            if (!isMissed && n.category in NOT_MISSED_CALL) return false
        }
        return true
    }

    private fun countPackage(pkg: String, list: List<StatusBarNotification>): Int {
        val (summaries, children) = list.partition {
            it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        }
        return if (children.isNotEmpty()) {
            children.sumOf { itemCount(it.notification, allowTextNumber = pkg in PHONE) }
        } else {
            summaries.sumOf { itemCount(it.notification, allowTextNumber = true) }
        }
    }

    @Suppress("DEPRECATION")
    private fun itemCount(n: Notification, allowTextNumber: Boolean): Int {
        val extras = n.extras
        val messages = extras?.getParcelableArray(Notification.EXTRA_MESSAGES)?.size ?: 0
        if (messages > 0) return messages
        if (n.number > 0) return n.number
        if (allowTextNumber && extras != null) {
            listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT).forEach { key ->
                val text = extras.getCharSequence(key)?.toString() ?: return@forEach
                LEADING_NUMBER.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            }
        }
        return 1
    }
}
