package de.mb.tagesring.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.mb.tagesring.data.EventStore
import de.mb.tagesring.data.InfoStore
import de.mb.tagesring.health.HealthSync
import de.mb.tagesring.data.NotificationStore
import de.mb.tagesring.provider.Refresh
import de.mb.tagesring.push.WatchFaceInstaller
import de.mb.tagesring.sync.PhoneLink
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Einrichtung auf der Uhr: Watchface installieren/aktivieren, Berechtigungen, Status. */
class SetupActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var installButton: Button
    private lateinit var activateButton: Button
    private lateinit var installer: WatchFaceInstaller

    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val healthPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { enableHealth() }

    private val activePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) activate() else say("Ohne die Berechtigung kann das Watchface nicht automatisch aktiviert werden. Bitte in der Zifferblatt-Auswahl wählen.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installer = WatchFaceInstaller(this)
        val pad = (resources.displayMetrics.density * 18).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad * 2)
            setBackgroundColor(Color.BLACK)
        }
        column.addView(label("neXart Watch", 16f, bold = true))
        status = label("", 13f)
        column.addView(status)
        message = label("", 12f, color = Color.parseColor("#4FC3F7"))
        column.addView(message)

        installButton = button("Watchface installieren") { install() }
        activateButton = button("Watchface aktivieren") { activate() }
        column.addView(installButton)
        column.addView(activateButton)
        column.addView(button("Kalender der Uhr erlauben") {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        })
        column.addView(button("Schritte und Puls erlauben") {
            if (HealthSync.hasPermissions(this)) enableHealth() else healthPermissions.launch(HealthSync.PERMISSIONS)
        })
        column.addView(button("Termine vom Handy holen") {
            lifecycleScope.launch {
                val n = PhoneLink.requestSync(this@SetupActivity)
                say(if (n > 0) "Handy angefragt" else "Kein Handy erreichbar")
            }
        })
        column.addView(label(
            "Handy-App „neXart Watch“: Kalender auswählen, Ganztägig an/aus.",
            12f, color = Color.GRAY,
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
        Refresh.requestAll(applicationContext)
    }

    private fun refresh() {
        lifecycleScope.launch {
            val s = installer.status()
            val calendar = ContextCompat.checkSelfPermission(this@SetupActivity, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
            val snap = NotificationStore.snapshot(this@SetupActivity)
            val phone = EventStore.load(this@SetupActivity)
            status.text = buildString {
                appendLine("Watchface: " + when {
                    !s.supported -> "✗ nicht unterstützt"
                    !s.installed -> "nicht installiert"
                    s.active && s.upToDate -> "✓ aktiv"
                    s.active -> "aktiv, Update verfügbar"
                    s.upToDate -> "installiert"
                    else -> "Update verfügbar"
                })
                appendLine("Termine: " + if (phone != null) {
                    "✓ vom Handy (${phone.events.size}, " +
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(phone.receivedAt)) + ")"
                } else if (calendar) "von der Uhr (Handy-App nicht verbunden)" else "✗ keine Quelle")
                append("Benachrichtigungen: " + if (snap.connected) {
                    "✓ vom Handy"
                } else {
                    "✗ Handy-App: Benachrichtigungszugriff erlauben"
                })
                append("\nSchritte/Puls: " + when {
                    !HealthSync.hasPermissions(this@SetupActivity) -> "Berechtigung fehlt"
                    InfoStore.healthStatus(this@SetupActivity).isEmpty() -> "nicht angemeldet"
                    else -> InfoStore.healthStatus(this@SetupActivity)
                })
                s.error?.let { append("\n$it") }
                if (snap.connected && snap.packages.isNotEmpty()) {
                    append("\n\nAktive Apps (Handy):\n")
                    append(snap.packages.entries.joinToString("\n") { "${it.key}: ${it.value}" })
                }
            }
            installButton.text = if (s.installed) "Watchface aktualisieren" else "Watchface installieren"
            installButton.isEnabled = s.supported
            activateButton.isEnabled = s.supported && s.installed && !s.active
        }
    }

    private fun enableHealth() {
        lifecycleScope.launch {
            say(if (HealthSync.hasPermissions(this@SetupActivity)) HealthSync.enable(this@SetupActivity) else "Schritte/Puls: Berechtigung fehlt")
            Refresh.requestInfo(applicationContext)
            refresh()
        }
    }

    private fun install() {
        say("Installiere …")
        lifecycleScope.launch {
            say(installer.installOrUpdate())
            refresh()
        }
    }

    private fun activate() {
        if (!installer.canActivate()) {
            activePermission.launch(WatchFaceInstaller.PERMISSION_ACTIVE)
            return
        }
        lifecycleScope.launch {
            say(installer.setActive())
            refresh()
        }
    }

    private fun say(text: String) {
        message.text = text
    }

    private fun label(text: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_HORIZONTAL
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }
}
