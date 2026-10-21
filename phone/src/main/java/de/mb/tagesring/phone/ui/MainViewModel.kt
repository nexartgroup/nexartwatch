package de.mb.tagesring.phone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import de.mb.tagesring.phone.data.CalendarSource
import de.mb.tagesring.phone.data.CounterSync
import de.mb.tagesring.phone.data.PhoneNotificationService
import de.mb.tagesring.sync.Theme
import de.mb.tagesring.phone.data.PhoneCalendar
import de.mb.tagesring.phone.data.PhoneEvent
import de.mb.tagesring.phone.data.Settings
import de.mb.tagesring.phone.data.SyncScheduler
import de.mb.tagesring.phone.data.WatchLink
import de.mb.tagesring.phone.data.WatchState
import de.mb.tagesring.phone.data.WatchSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class UiState(
    val hasPermission: Boolean = false,
    val calendars: List<PhoneCalendar> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val showAllDay: Boolean = false,
    /** Termine der nächsten drei Tage, noch ohne Regenbogenfarben. */
    val raw: List<PhoneEvent> = emptyList(),
    val connection: WatchLink.Connection? = null,
    val lastSyncAt: Long = 0,
    val lastSyncCount: Int = 0,
    val busy: Boolean = false,
    val note: String = "",
    val listenerEnabled: Boolean = false,
    /** Vom Nutzer gewählte Farben je Kalender-ID. */
    val colors: Map<Long, Int> = emptyMap(),
    val theme: Theme = Theme.DEFAULT,
    val colorMode: Int = 0,
    val paletteStart: Int = 0,
    val batteryUnrestricted: Boolean = false,
) {
    /** Termine mit den Farben, die auch die Uhr bekommt. */
    val events: List<PhoneEvent> get() = WatchSync.colorize(raw, colorMode, paletteStart)

    /** Heutige Termine (ganztägige zuerst). */
    val preview: List<PhoneEvent>
        get() {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return events.filter { it.end > from && it.begin < to }.sortedWith(compareBy({ !it.allDay }, { it.begin }))
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val settings = Settings(app)
    private val source = CalendarSource(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val watchStatus = WatchState.status
    val counters = CounterSync.current
    val installMessage = WatchState.installMessage

    fun refresh() {
        viewModelScope.launch {
            val permission = source.hasPermission()
            val (calendars, raw) = withContext(Dispatchers.IO) { source.calendars() to WatchSync.collectRaw(context) }
            val selected = settings.selectedCalendars ?: calendars.map { it.id }.toSet()
            _state.value = _state.value.copy(
                hasPermission = permission,
                calendars = calendars,
                selected = selected,
                showAllDay = settings.showAllDay,
                raw = raw,
                colors = settings.calendarColors,
                theme = settings.theme,
                colorMode = settings.colorMode,
                paletteStart = settings.paletteStart,
                batteryUnrestricted = (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName),
                lastSyncAt = settings.lastSyncAt,
                lastSyncCount = settings.lastSyncCount,
            )
            if (permission) SyncScheduler.schedule(context)
            // Beim Öffnen der App das Farbschema abgleichen (z. B. nach einem Update mit neuen Standardfarben)
            try {
                WatchSync.pushTheme(context)
            } catch (_: Exception) {
            }
            val listener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            _state.value = _state.value.copy(listenerEnabled = listener)
            if (listener && !CounterSync.current.value.connected) {
                try {
                    NotificationListenerService.requestRebind(ComponentName(context, PhoneNotificationService::class.java))
                } catch (_: Exception) {
                }
            }
            refreshWatch()
        }
    }

    fun refreshWatch() {
        viewModelScope.launch {
            _state.value = _state.value.copy(connection = WatchLink.connection(context))
            try {
                WatchLink.requestStatus(context)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleCalendar(id: Long) {
        val current = _state.value
        val next = if (id in current.selected) current.selected - id else current.selected + id
        settings.selectedCalendars = next
        applyAndSync()
    }

    fun selectAll(all: Boolean) {
        settings.selectedCalendars = if (all) null else emptySet()
        applyAndSync()
    }

    /** [color] = null: zurück zur Originalfarbe des Kalenders. */
    fun setCalendarColor(id: Long, color: Int?) {
        settings.setCalendarColor(id, color)
        applyAndSync()
    }

    /** Farbe einer Rolle des Watchfaces ändern; [index] wie [Theme.toList]. */
    fun setThemeColor(index: Int, color: Int?) {
        val list = settings.theme.toList().toMutableList()
        list[index] = color ?: Theme.DEFAULT.toList()[index]
        settings.theme = Theme.fromList(list)
        applyAndSync()
    }

    fun resetTheme() {
        settings.theme = Theme.DEFAULT
        applyAndSync()
    }

    fun setColorMode(mode: Int) {
        settings.colorMode = mode
        applyAndSync()
    }

    fun setPaletteStart(index: Int) {
        settings.paletteStart = index
        _state.value = _state.value.copy(paletteStart = index)
        applyAndSync()
    }

    fun setAllDay(value: Boolean) {
        settings.showAllDay = value
        applyAndSync()
    }

    fun syncNow() = applyAndSync(showNote = true)

    fun installWatchFace() {
        viewModelScope.launch {
            WatchState.installMessage.value = "Wird an die Uhr gesendet …"
            val n = try {
                WatchLink.requestInstall(context)
            } catch (_: Exception) {
                0
            }
            if (n == 0) {
                WatchState.installMessage.value = "Keine Uhr mit installierter neXart-Watch-App erreichbar."
            }
        }
    }

    private fun applyAndSync(showNote: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val (cals, raw) = withContext(Dispatchers.IO) { source.calendars() to WatchSync.collectRaw(context) }
            val sent = try {
                WatchSync.pushTheme(context)
                WatchSync.push(context)
            } catch (_: Exception) {
                -2
            }
            _state.value = _state.value.copy(
                busy = false,
                calendars = cals,
                selected = settings.selectedCalendars ?: cals.map { it.id }.toSet(),
                showAllDay = settings.showAllDay,
                raw = raw,
                colors = settings.calendarColors,
                theme = settings.theme,
                colorMode = settings.colorMode,
                paletteStart = settings.paletteStart,
                lastSyncAt = settings.lastSyncAt,
                lastSyncCount = settings.lastSyncCount,
                note = if (!showNote) "" else when {
                    sent >= 0 -> "$sent Termine an die Uhr gesendet"
                    sent == -1 -> "Kalender-Zugriff fehlt"
                    else -> "Senden fehlgeschlagen (Uhr verbunden?)"
                },
            )
        }
    }
}
