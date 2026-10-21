package de.mb.tagesring.sync

/**
 * Zusatzinfos, die das Watchface rechts neben dem nächsten Termin zeigen kann (zwei Felder).
 * Die Handy-App wählt je Feld eine Art; auf der Uhr lässt sich jedes Feld zusätzlich über „Anpassen“
 * auf eine beliebige andere Komplikation (Puls von Samsung Health, Timer, ...) umstellen.
 */
enum class InfoType(val id: String, val label: String, val hint: String) {
    NONE("none", "Leer", "Feld bleibt leer – auf der Uhr über „Anpassen“ belegen"),
    STEPS("steps", "Schritte", "Schritte heute (Health Services der Uhr)"),
    HEART_RATE("hr", "Puls", "Zuletzt gemessener Puls (Health Services der Uhr)"),
    BATTERY_PHONE("phone_battery", "Akku Handy", "Ladestand des Handys"),
    ALARM("alarm", "Wecker", "Nächster Wecker des Handys"),
    WEEK("week", "Kalenderwoche", "Aktuelle Kalenderwoche"),
    ;

    companion object {
        fun fromId(id: String?, default: InfoType = NONE) = entries.firstOrNull { it.id == id } ?: default
    }
}
