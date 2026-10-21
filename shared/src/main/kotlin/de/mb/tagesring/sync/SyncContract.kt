package de.mb.tagesring.sync

/**
 * Vereinbarung zwischen Handy-App und Uhr-App (Wearable Data Layer).
 * Diese Datei gehört zu beiden Modulen (siehe `shared/`).
 */
object SyncContract {
    // ---- Handy -> Uhr: Termine (DataItem)
    const val PATH_EVENTS = "/tagesring/events"
    const val KEY_EVENTS = "events"
    const val KEY_SYNCED_AT = "syncedAt"

    const val E_ID = "id"
    const val E_TITLE = "title"
    const val E_BEGIN = "begin"
    const val E_END = "end"
    const val E_LOCATION = "location"
    const val E_CALENDAR = "calendar"
    const val E_COLOR = "color"
    const val E_ALL_DAY = "allDay"

    // ---- Handy -> Uhr: Farben und Darstellung (DataItem)
    const val PATH_THEME = "/tagesring/theme"
    const val T_COLORS = "colors"          // IntArray, Reihenfolge wie Theme.toList()
    const val T_UPDATED_AT = "updatedAt"

    // ---- Handy -> Uhr: Benachrichtigungszähler (DataItem)
    const val PATH_COUNTERS = "/tagesring/counters"
    const val C_CONNECTED = "connected"
    const val C_PHONE = "phone"
    const val C_WHATSAPP = "whatsapp"
    const val C_MAIL = "mail"
    const val C_OTHER = "other"
    const val C_PACKAGES = "packages" // "paket=anzahl,paket=anzahl" (Diagnose)
    const val C_UPDATED_AT = "updatedAt"

    // ---- Uhr -> Handy: "bitte Termine und Zähler neu senden" (Nachricht)
    const val PATH_REQUEST = "/tagesring/request"

    // ---- Handy -> Uhr: Watchface per Watch Face Push installieren / aktualisieren (Nachricht)
    const val PATH_INSTALL = "/tagesring/install"

    // ---- Uhr -> Handy: Ergebnis der Installation, Text für den Nutzer (Nachricht)
    const val PATH_INSTALL_RESULT = "/tagesring/install/result"

    // ---- Handy -> Uhr: Status abfragen (Nachricht); Antwort Uhr -> Handy als JSON
    const val PATH_STATUS_REQUEST = "/tagesring/status/request"
    const val PATH_STATUS = "/tagesring/status"
}
