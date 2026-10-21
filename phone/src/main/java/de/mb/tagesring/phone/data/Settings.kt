package de.mb.tagesring.phone.data

import android.content.Context
import de.mb.tagesring.sync.InfoType
import de.mb.tagesring.sync.Theme

/** Einstellungen der Handy-App (Kalenderauswahl, Ganztägig). */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** null = alle Kalender. */
    var selectedCalendars: Set<Long>?
        get() = if (prefs.contains(KEY_SELECTED)) {
            prefs.getStringSet(KEY_SELECTED, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        } else {
            null
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SELECTED) else putStringSet(KEY_SELECTED, value.map { it.toString() }.toSet())
            }.apply()
        }

    var showAllDay: Boolean
        get() = prefs.getBoolean(KEY_ALL_DAY, false)
        set(value) = prefs.edit().putBoolean(KEY_ALL_DAY, value).apply()

    /** Vom Nutzer gewählte Farben je Kalender-ID (überschreiben die Kalender- und Einzelfarben). */
    var calendarColors: Map<Long, Int>
        get() = prefs.getStringSet(KEY_COLORS, emptySet())!!.mapNotNull {
            val (id, color) = it.split(':').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (color.toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
        set(value) = prefs.edit().putStringSet(KEY_COLORS, value.map { "${it.key}:${it.value}" }.toSet()).apply()

    /** [color] = null setzt auf die Originalfarbe des Kalenders zurück. */
    fun setCalendarColor(calendarId: Long, color: Int?) {
        calendarColors = calendarColors.toMutableMap().apply {
            if (color == null) remove(calendarId) else put(calendarId, color or 0xFF000000.toInt())
        }
    }

    /**
     * Farben des Watchfaces (alle 11 Rollen). Werte, die noch den alten Standardfarben (bis 3.0) entsprechen,
     * werden einmalig auf die neuen nexart-Standardfarben umgestellt.
     */
    var theme: Theme
        get() {
            val saved = prefs.getString(KEY_THEME, null)?.split(',')?.mapNotNull { it.toLongOrNull()?.toInt() }
                ?.let { Theme.fromList(it) } ?: return Theme.DEFAULT
            if (prefs.getInt(KEY_THEME_VERSION, 1) >= THEME_VERSION) return saved
            val old = Theme.LEGACY_DEFAULT.toList()
            val new = Theme.DEFAULT.toList()
            val migrated = Theme.fromList(saved.toList().mapIndexed { i, v -> if (v == old[i]) new[i] else v })
            prefs.edit().putString(KEY_THEME, migrated.toList().joinToString(",")).putInt(KEY_THEME_VERSION, THEME_VERSION).apply()
            return migrated
        }
        set(value) = prefs.edit().putString(KEY_THEME, value.toList().joinToString(","))
            .putInt(KEY_THEME_VERSION, THEME_VERSION).apply()

    /** 0 = Kalenderfarben, 1 = Palette (harmonische Farbfolge, die sich wiederholt). */
    var colorMode: Int
        get() = prefs.getInt(KEY_MODE, MODE_CALENDAR)
        set(value) = prefs.edit().putInt(KEY_MODE, value).apply()

    /** Mit welcher Farbe der Palette der erste Termin beginnt (0 bis [de.mb.tagesring.phone.data.Palette.COLORS].size - 1). */
    var paletteStart: Int
        get() = prefs.getInt(KEY_PALETTE_START, 0).coerceIn(0, Palette.COLORS.size - 1)
        set(value) = prefs.edit().putInt(KEY_PALETTE_START, value).apply()

    /** Zusatzinfos rechts neben dem Termin; [index] 1 = oben, 2 = unten. */
    fun infoSlot(index: Int): InfoType =
        InfoType.fromId(prefs.getString("info_slot$index", null), if (index == 1) InfoType.STEPS else InfoType.HEART_RATE)

    fun setInfoSlot(index: Int, type: InfoType) = prefs.edit().putString("info_slot$index", type.id).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var lastSyncCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_COUNT, value).apply()

    companion object {
        const val MODE_CALENDAR = 0
        const val MODE_PALETTE = 1
        const val THEME_VERSION = 2

        const val KEY_SELECTED = "selected_calendars"
        const val KEY_ALL_DAY = "show_all_day"
        const val KEY_COLORS = "calendar_colors"
        const val KEY_THEME = "theme"
        const val KEY_MODE = "color_mode"
        const val KEY_PALETTE_START = "palette_start"
        const val KEY_THEME_VERSION = "theme_version"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_COUNT = "last_sync_count"
    }
}
