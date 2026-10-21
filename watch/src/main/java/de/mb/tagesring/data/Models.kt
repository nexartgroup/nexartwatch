package de.mb.tagesring.data

/** Ein Kalendertermin. Ganztägige Termine haben lokale Tagesgrenzen als Anfang/Ende. */
data class CalendarEvent(
    /** Event-ID im Kalender-Provider (für "Termin öffnen"), 0 = unbekannt. */
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String?,
    val calendarName: String?,
    val color: Int,
    val allDay: Boolean = false,
)

/** Zähler der aktiven Benachrichtigungen auf der Uhr (vom Telefon gespiegelt). */
data class NotificationSnapshot(
    /** false = Benachrichtigungszugriff nicht aktiv, Zähler sind unbekannt. */
    val connected: Boolean = false,
    val phone: Int = 0,
    val whatsapp: Int = 0,
    val mail: Int = 0,
    val other: Int = 0,
    /** Paketname -> Anzahl, nur zur Diagnose in der Setup-App. */
    val packages: Map<String, Int> = emptyMap(),
)
