package de.mb.tagesring.sync

/**
 * Farben des Watchfaces (ARGB). Standardfarben nach dem Design von nexart.de (dunkles Marineblau-Grau,
 * Akzentblau #3FA9F5, kühle Grautöne). Die Handy-App legt sie fest, die Uhr liefert sie dem Watchface über
 * Komplikations-Daten (mehr als 7 Farben pro Komplikation sind nicht möglich, deshalb die Gruppen).
 *
 *  Gruppe "main" (Hauptebene):  time, text, marks, track, progress, badge, date
 *  Gruppe "title" (Zeile 1+2):  label, title
 *
 * Die Zeilen 3 und 4 (Uhrzeit, Kalender · Ort) nutzen die Farben "date" und "marks" der Hauptebene.
 */
data class Theme(
    /** Uhrzeit */
    val time: Int = 0xFFEEF3F8.toInt(),
    /** Wetter, Akku und Zahlen */
    val text: Int = 0xFFDFE6EE.toInt(),
    /** Stundenmarken, Beschriftung (6, 18, 0) und die Zeile „Kalender · Ort“ */
    val marks: Int = 0xFF71869A.toInt(),
    /** Ring-Spur */
    val track: Int = 0xFF203240.toInt(),
    /** Bereits vergangener Teil des Tages */
    val progress: Int = 0xFF3FA9F5.toInt(),
    /** Hintergrund der Zähler-Marken */
    val badge: Int = 0xFF203240.toInt(),
    /** Wochentag und Datum unter der Uhrzeit sowie die Uhrzeit des nächsten Termins */
    val date: Int = 0xFFA9B7C6.toInt(),
    /** "NÄCHSTER · in 39 Min" */
    val label: Int = 0xFF3FA9F5.toInt(),
    /** Titel des Termins */
    val title: Int = 0xFFEEF3F8.toInt(),
) {
    fun main() = intArrayOf(time, text, marks, track, progress, badge, date)
    fun titleGroup() = intArrayOf(label, title)

    fun toList(): List<Int> = listOf(time, text, marks, track, progress, badge, date, label, title)

    companion object {
        val DEFAULT = Theme()

        /** Standardfarben bis Version 3.0 – gespeicherte Werte, die noch so aussehen, werden auf die neuen umgestellt. */
        val LEGACY_DEFAULT = Theme(
            time = 0xFFFFFFFF.toInt(), text = 0xFFFFFFFF.toInt(), marks = 0xFF8B939B.toInt(), track = 0xFF2B3238.toInt(),
            progress = 0xFF4FC3F7.toInt(), badge = 0xFF33383D.toInt(), date = 0xFFFFFFFF.toInt(),
            label = 0xFFFFFFFF.toInt(), title = 0xFFFFFFFF.toInt(),
        )

        fun fromList(l: List<Int>): Theme {
            if (l.size < 9) return DEFAULT      // ältere Speicherstände hatten 11 Werte: die ersten 9 gelten weiter
            return Theme(l[0], l[1], l[2], l[3], l[4], l[5], l[6], l[7], l[8])
        }

        /** Namen für die Handy-App, in der Reihenfolge von [toList]. */
        val ROLE_NAMES = listOf(
            "Uhrzeit", "Wetter, Akku, Zahlen", "Stundenmarken, Kalender · Ort", "Ring-Spur", "Vergangene Zeit",
            "Zähler-Hintergrund", "Datum, Uhrzeit des Termins", "Zeile „Nächster“", "Titel des Termins",
        )
    }
}
