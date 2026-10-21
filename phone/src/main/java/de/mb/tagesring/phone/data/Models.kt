package de.mb.tagesring.phone.data

/** Ein Kalender auf dem Handy. */
data class PhoneCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

/** Ein Termin (ganztägige Termine haben lokale Tagesgrenzen als Anfang/Ende). */
data class PhoneEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String?,
    val calendarName: String,
    val color: Int,
    val allDay: Boolean,
)
