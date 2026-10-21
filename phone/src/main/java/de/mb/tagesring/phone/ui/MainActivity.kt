package de.mb.tagesring.phone.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import de.mb.tagesring.phone.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.Intent
import de.mb.tagesring.phone.data.Counters
import de.mb.tagesring.phone.data.Palette
import de.mb.tagesring.phone.data.PhoneCalendar
import de.mb.tagesring.phone.data.Settings as AppSettings
import de.mb.tagesring.sync.InfoType
import de.mb.tagesring.sync.Theme
import de.mb.tagesring.phone.data.PhoneEvent
import de.mb.tagesring.phone.data.WatchLink
import de.mb.tagesring.phone.data.WatchState
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/** Farben von nexart.de: dunkles Marineblau-Grau, Akzentblau, kühle Grautöne. */
private val NEXART_SCHEME = darkColorScheme(
    primary = Color(0xFF3FA9F5), onPrimary = Color(0xFF06121C),
    primaryContainer = Color(0xFF123A57), onPrimaryContainer = Color(0xFFDFE6EE),
    secondary = Color(0xFF5FD0C0), onSecondary = Color(0xFF06121C),
    secondaryContainer = Color(0xFF203240), onSecondaryContainer = Color(0xFFDFE6EE),
    tertiary = Color(0xFFB3A6EC), onTertiary = Color(0xFF06121C),
    background = Color(0xFF0E1720), onBackground = Color(0xFFDFE6EE),
    surface = Color(0xFF0E1720), onSurface = Color(0xFFDFE6EE),
    surfaceVariant = Color(0xFF172733), onSurfaceVariant = Color(0xFFA9B7C6),
    surfaceContainerLowest = Color(0xFF0C141C), surfaceContainerLow = Color(0xFF101C26),
    surfaceContainer = Color(0xFF122029), surfaceContainerHigh = Color(0xFF172733), surfaceContainerHighest = Color(0xFF172733),
    outline = Color(0xFF71869A), outlineVariant = Color(0xFF203240),
    error = Color(0xFFEF8A7A), onError = Color(0xFF06121C),
)

private val PLEX = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NEXART_SCHEME) {
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
                }
                Screen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val watch by vm.watchStatus.collectAsState()
    val installMessage by vm.installMessage.collectAsState()
    val counters by vm.counters.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<PhoneCalendar?>(null) }
    var editingRole by remember { mutableStateOf<Int?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo_n), contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("neXart Watch", fontFamily = PLEX, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        editing?.let { cal ->
            ColorDialog(
                title = "Farbe: ${cal.name}",
                hint = "Gilt für alle Termine dieses Kalenders – auf dem Handy und auf dem Watchface.",
                current = state.colors[cal.id],
                onPick = { color -> vm.setCalendarColor(cal.id, color); editing = null },
                onDismiss = { editing = null },
            )
        }
        editingRole?.let { role ->
            ColorDialog(
                title = Theme.ROLE_NAMES[role],
                hint = "Farbe auf dem Watchface. „Original“ stellt die Standardfarbe wieder her.",
                current = state.theme.toList()[role].takeIf { it != Theme.DEFAULT.toList()[role] },
                onPick = { color -> vm.setThemeColor(role, color); editingRole = null },
                onDismiss = { editingRole = null },
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
        // Vorschau bleibt oben stehen, die Karten darunter scrollen
        WatchFacePreview(state, counters, Modifier.padding(top = 4.dp, bottom = 6.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { WatchCard(state.connection, watch, installMessage, onInstall = vm::installWatchFace, onRefresh = vm::refreshWatch) }

            item {
                NotificationCard(counters, state.listenerEnabled, state.batteryUnrestricted,
                    onOpenSettings = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onBattery = {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")),
                            )
                        } catch (_: Exception) {
                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    })
            }

            item {
                SectionCard("Kalender auf der Uhr") {
                    if (!state.hasPermission) {
                        Text("Damit Termine auf dem Watchface erscheinen, braucht die App Zugriff auf deine Kalender.")
                        Button(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Kalender-Zugriff erlauben") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Ganztägige Termine anzeigen", style = MaterialTheme.typography.bodyLarge)
                                Text("Dünner farbiger Außenring; erscheint als Nächster, wenn heute nichts mehr ansteht.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = state.showAllDay, onCheckedChange = vm::setAllDay)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.selectAll(true) }) { Text("Alle") }
                            TextButton(onClick = { vm.selectAll(false) }) { Text("Keine") }
                        }
                    }
                }
            }

            if (state.hasPermission) {
                val grouped = state.calendars.groupBy { it.account }
                grouped.forEach { (account, calendars) ->
                    item(key = "acc_$account") {
                        Text(account.ifBlank { "Lokal" }, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    }
                    items(calendars, key = { "cal_${it.id}" }) { cal ->
                        CalendarRow(
                            cal = cal,
                            color = state.colors[cal.id] ?: cal.color,
                            checked = cal.id in state.selected,
                            onToggle = { vm.toggleCalendar(cal.id) },
                            onPickColor = { editing = cal },
                        )
                    }
                }

                item {
                    AppearanceCard(
                        state = state,
                        onMode = vm::setColorMode,
                        onPaletteStart = vm::setPaletteStart,
                        onRole = { editingRole = it },
                        onReset = vm::resetTheme,
                    )
                }

                item { InfoCard(state, onSlot = vm::setInfoSlot) }

                item {
                    SectionCard("Heute auf dem Ring") {
                        if (state.preview.isEmpty()) {
                            Text("Keine Termine in den gewählten Kalendern.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.preview.forEach { EventRow(it) }
                        }
                    }
                }

                item {
                    SectionCard("Abgleich") {
                        val last = if (state.lastSyncAt == 0L) "noch nie" else
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.lastSyncAt)) +
                                " (${state.lastSyncCount} Termine)"
                        Text("Zuletzt gesendet: $last")
                        if (state.note.isNotEmpty()) Text(state.note, color = MaterialTheme.colorScheme.primary)
                        Text("Änderungen werden automatisch gesendet, außerdem alle 15 Minuten.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = vm::syncNow, enabled = !state.busy) { Text("Jetzt an die Uhr senden") }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun WatchCard(
    connection: WatchLink.Connection?,
    watch: WatchState.Status?,
    installMessage: String,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard("Uhr") {
        when {
            connection == null -> Text("Suche Uhr …")
            !connection.hasWatch -> {
                Text("Keine Uhr verbunden.", fontWeight = FontWeight.Medium)
                Text("Galaxy Wearable muss mit der Uhr verbunden sein.", style = MaterialTheme.typography.bodySmall)
            }
            !connection.hasApp -> {
                Text("Uhr verbunden: ${connection.connected.joinToString { it.displayName }}", fontWeight = FontWeight.Medium)
                Text("Die neXart-Watch-App ist auf der Uhr noch nicht installiert. Sie muss einmalig über den Play Store oder " +
                    "per adb auf die Uhr kommen (siehe README); danach läuft alles von hier.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                Text("Uhr verbunden: ${connection.withApp.joinToString { it.displayName }}", fontWeight = FontWeight.Medium)
                if (watch == null) {
                    Text("Frage Status ab …", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        when {
                            !watch.faceSupported -> "Watchface-Installation wird von dieser Uhr nicht unterstützt (Wear OS 6 nötig)."
                            !watch.faceInstalled -> "Watchface: noch nicht installiert"
                            watch.faceActive && watch.faceUpToDate -> "Watchface: installiert und aktiv ✓"
                            watch.faceActive -> "Watchface: aktiv, Update verfügbar"
                            watch.faceUpToDate -> "Watchface: installiert, nicht aktiv"
                            else -> "Watchface: installiert, Update verfügbar"
                        },
                    )
                    Text(
                        "Uhr-App ${watch.version} · Termine auf der Uhr: ${watch.eventCount}" +
                            if (watch.eventsAt > 0) " (Stand " +
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(watch.eventsAt)) + ")" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (watch.error.isNotEmpty()) Text(watch.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (installMessage.isNotEmpty()) Text(installMessage, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onInstall, enabled = watch?.faceSupported != false) {
                        Text(if (watch?.faceInstalled == true) "Watchface aktualisieren" else "Watchface auf Uhr installieren")
                    }
                    TextButton(onClick = onRefresh) { Text("Status") }
                }
                if (watch != null && watch.faceInstalled && !watch.faceActive) {
                    Text("Zum Aktivieren auf der Uhr die App „neXart Watch“ öffnen und „Watchface aktivieren“ tippen " +
                        "(einmalige Berechtigung) – oder Display lange drücken und „neXart Watch“ wählen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(counters: Counters, enabled: Boolean, batteryUnrestricted: Boolean, onOpenSettings: () -> Unit, onBattery: () -> Unit) {
    SectionCard("Benachrichtigungen auf der Uhr") {
        if (!enabled || !counters.connected) {
            Text("Zeigt auf dem Watchface, wie viele Anrufe in Abwesenheit, WhatsApp-Nachrichten, Mails und andere " +
                "Benachrichtigungen auf dem Handy aktiv sind. Dafür braucht die App den „Benachrichtigungszugriff“.")
            Button(onClick = onOpenSettings) { Text("Benachrichtigungszugriff erlauben") }
            Text("Bei einer von Hand installierten App erscheint der Schalter erst, nachdem du in den App-Infos über " +
                "⋮ „Eingeschränkte Einstellungen zulassen“ gewählt hast.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Anrufe ${counters.phone} · WhatsApp ${counters.whatsapp} · Mail ${counters.mail} · Sonstige ${counters.other}",
                fontWeight = FontWeight.Medium)
            if (counters.packages.isNotEmpty()) {
                Text(counters.packages.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!batteryUnrestricted) {
            Text("Damit die Zahlen auch bei schlafendem Handy sofort auf der Uhr erscheinen, darf die App nicht vom " +
                "Akku-Sparmodus eingeschränkt werden.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onBattery) { Text("Akku-Einschränkung aufheben") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontFamily = PLEX, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
private fun CalendarRow(cal: PhoneCalendar, color: Int, checked: Boolean, onToggle: () -> Unit, onPickColor: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(cal.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Antippen öffnet die Farbauswahl
        Box(
            Modifier.padding(end = 8.dp).size(34.dp).clip(CircleShape)
                .clickable(onClick = onPickColor).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(color)))
        }
    }
}

private val PALETTE = listOf(
    "#3FA9F5", "#5FD0C0", "#6CC3A0", "#A9D36F", "#E3B25E",
    "#EF8A7A", "#B3A6EC", "#F08FC0", "#FFFFFF", "#EEF3F8",
    "#DFE6EE", "#A9B7C6", "#71869A", "#2D4252", "#203240",
).map { Color(android.graphics.Color.parseColor(it)) }

@Composable
private fun ColorDialog(title: String, hint: String, current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val start = Color(current ?: 0xFF4FC3F7.toInt())
    var hue by remember { mutableStateOf(hueOf(start)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PALETTE.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            val selected = current != null && c.toArgb() == (current or 0xFF000000.toInt())
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(c)
                                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable { onPick(c.toArgb()) },
                            )
                        }
                    }
                }
                Text("Oder Farbton wählen:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color.hsv(hue, 0.75f, 0.95f)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(Color.hsv(hue, 0.75f, 0.95f).toArgb()) }) { Text("Farbton übernehmen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("Original") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(state: UiState, onSlot: (Int, InfoType) -> Unit) {
    SectionCard("Zusatzinfos neben dem Termin") {
        Text("Zwei kleine Felder rechts neben dem nächsten Termin. Wähle hier, was sie zeigen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (slot in 1..2) {
            val current = if (slot == 1) state.infoSlot1 else state.infoSlot2
            Text(if (slot == 1) "Oberes Feld" else "Unteres Feld", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoType.entries.forEach { type ->
                    FilterChip(selected = type == current, onClick = { onSlot(slot, type) }, label = { Text(type.label) })
                }
            }
            Text(current.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Auf der Uhr lässt sich jedes Feld auch direkt ändern: Zifferblatt lange drücken, „Anpassen“, Feld antippen – " +
            "dort stehen alle Komplikationen der Uhr zur Wahl (z. B. Puls von Samsung Health, Timer, Stoppuhr).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Schritte und Puls liest die Uhr-App selbst (Health Services). Einmalig auf der Uhr in der App „neXart Watch“ " +
            "„Schritte und Puls erlauben“ antippen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceCard(
    state: UiState,
    onMode: (Int) -> Unit,
    onPaletteStart: (Int) -> Unit,
    onRole: (Int) -> Unit,
    onReset: () -> Unit,
) {
    SectionCard("Farben") {
        Text("Farben der Termine", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.colorMode == AppSettings.MODE_CALENDAR, onClick = { onMode(AppSettings.MODE_CALENDAR) },
                label = { Text("Kalenderfarben") })
            FilterChip(selected = state.colorMode == AppSettings.MODE_PALETTE, onClick = { onMode(AppSettings.MODE_PALETTE) },
                label = { Text("Palette") })
        }
        if (state.colorMode == AppSettings.MODE_PALETTE) {
            Text("Die Termine bekommen der Reihe nach diese zueinander passenden Farben, danach beginnt die Folge von vorn. " +
                "Aufeinanderfolgende Termine sind so immer klar zu unterscheiden. Tippe auf eine Farbe, um die Folge dort zu beginnen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Palette.COLORS.forEachIndexed { i, c ->
                    val first = i == state.paletteStart
                    Box(
                        Modifier.size(if (first) 38.dp else 32.dp).clip(CircleShape).background(Color(c))
                            .border(if (first) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { onPaletteStart(i) },
                    )
                }
            }
        }
        Text("Farben der Uhr", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Theme.ROLE_NAMES.forEachIndexed { i, name ->
            Row(
                Modifier.fillMaxWidth().clickable { onRole(i) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Box(Modifier.size(26.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .background(Color(state.theme.toList()[i])))
            }
        }
        Text("Im Ruhemodus (AOD) ist alles schwarz-weiß und wandert minütlich um wenige Pixel, damit nichts einbrennt.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReset) { Text("Alle Uhr-Farben zurücksetzen") }
    }
}

private fun hueOf(c: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return hsv[0]
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EventRow(e: PhoneEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(e.color)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(e.title, style = MaterialTheme.typography.bodyLarge)
            val zone = ZoneId.systemDefault()
            val range = if (e.allDay) "ganztägig" else
                TIME.format(Instant.ofEpochMilli(e.begin).atZone(zone)) + "–" + TIME.format(Instant.ofEpochMilli(e.end).atZone(zone))
            Text(listOfNotNull(range, e.calendarName.takeIf { it.isNotBlank() }, e.location).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
