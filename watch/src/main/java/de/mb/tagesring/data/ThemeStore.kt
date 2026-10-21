package de.mb.tagesring.data

import android.content.Context
import de.mb.tagesring.sync.Theme

/** Farben des Watchfaces, wie die Handy-App sie zuletzt geschickt hat. */
object ThemeStore {
    @Volatile
    private var cached: Theme? = null

    fun theme(context: Context): Theme {
        cached?.let { return it }
        val csv = context.getSharedPreferences("theme", Context.MODE_PRIVATE).getString("colors", null)
        val theme = csv?.split(',')?.mapNotNull { it.trim().toLongOrNull()?.toInt() }?.let { Theme.fromList(it) } ?: Theme.DEFAULT
        cached = theme
        return theme
    }

    /** @return true, wenn sich etwas geändert hat. */
    fun save(context: Context, theme: Theme): Boolean {
        if (theme == theme(context)) return false
        cached = theme
        context.getSharedPreferences("theme", Context.MODE_PRIVATE).edit()
            .putString("colors", theme.toList().joinToString(",")).apply()
        return true
    }
}
