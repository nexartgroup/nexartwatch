package de.mb.tagesring.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.coroutineScope

/**
 * Installiert das Watchface mit der offiziellen Watch-Face-Push-API (ab Wear OS 6): Das als Asset
 * eingebettete, reine WFF-Paket wird zusammen mit seinem Prüf-Token an das System übergeben –
 * ohne adb und ohne Play Store.
 */
class WatchFaceInstaller(base: Context) {

    // Receiver-Kontexte dürfen keine Dienste binden – deshalb der Anwendungskontext
    private val context: Context = base.applicationContext

    data class Status(
        val supported: Boolean,
        val installed: Boolean,
        val slotId: String?,
        val installedVersion: Long,
        val bundledVersion: Long,
        val active: Boolean,
        val canActivate: Boolean,
        val error: String? = null,
    ) {
        val upToDate get() = installed && installedVersion >= bundledVersion
    }

    private val manager: WatchFacePushManager by lazy {
        WatchFacePushManagerFactory.createWatchFacePushManager(context)
    }

    fun canActivate(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION_ACTIVE) == PackageManager.PERMISSION_GRANTED

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val bundled = bundledVersion()
        if (!WatchFacePushManagerFactory.isSupported()) {
            return@withContext Status(false, false, null, 0, bundled, false, false, "Watch Face Push wird nicht unterstützt")
        }
        try {
            val face = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FACE_PACKAGE }
            val active = face != null && try {
                manager.isWatchFaceActive(FACE_PACKAGE)
            } catch (_: Exception) {
                false
            }
            Status(true, face != null, face?.slotId, face?.versionCode ?: 0, bundled, active, canActivate())
        } catch (e: Exception) {
            Log.w(TAG, "status", e)
            Status(true, false, null, 0, bundled, false, canActivate(), describe(e))
        }
    }

    /** Installiert das Watchface bzw. aktualisiert es, wenn die eingebettete Version neuer ist. */
    suspend fun installOrUpdate(): String {
        val token = try {
            context.assets.open(ASSET_TOKEN).use { String(it.readBytes(), Charsets.UTF_8).trim() }
        } catch (_: IOException) {
            return "Prüf-Token fehlt in der App"
        }
        return installOrUpdate(token) { context.assets.open(ASSET_APK) }
    }

    /** Wie [installOrUpdate], aber mit beliebiger APK-Quelle (Tests). */
    suspend fun installOrUpdate(token: String, apk: () -> java.io.InputStream): String = withContext(Dispatchers.IO) {
        if (!WatchFacePushManagerFactory.isSupported()) return@withContext "Watch Face Push wird nicht unterstützt"
        try {
            val existing = manager.listWatchFaces().installedWatchFaceDetails
                .firstOrNull { it.packageName == FACE_PACKAGE }
            coroutineScope {
                val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
                launch(Dispatchers.IO) { pump(writeFd, apk) }
                try {
                    if (existing == null) {
                        manager.addWatchFace(readFd, token)
                    } else {
                        manager.updateWatchFace(existing.slotId, readFd, token)
                    }
                } finally {
                    readFd.close()
                    writeFd.close()
                }
            }
            if (existing == null) "Watchface installiert" else "Watchface aktualisiert"
        } catch (e: Exception) {
            Log.e(TAG, "installOrUpdate", e)
            "Installation fehlgeschlagen: ${describe(e)}"
        }
    }

    suspend fun setActive(): String = withContext(Dispatchers.IO) {
        try {
            val face = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FACE_PACKAGE }
                ?: return@withContext "Watchface ist noch nicht installiert"
            if (manager.isWatchFaceActive(FACE_PACKAGE)) return@withContext "Watchface ist bereits aktiv"
            manager.setWatchFaceAsActive(face.slotId)
            "Watchface ist jetzt aktiv"
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            Log.e(TAG, "setActive", e)
            when (e.errorCode) {
                WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MAXIMUM_ATTEMPTS_REACHED ->
                    "Automatisches Aktivieren ist nicht mehr möglich (Limit des Systems). Bitte Display lange drücken und „neXart Watch“ wählen."
                WatchFacePushManager.SetWatchFaceAsActiveException.ERROR_MISSING_PERMISSION ->
                    "Berechtigung zum Aktivieren fehlt – in der Uhr-App „Watchface aktivieren“ tippen."
                else -> "Aktivieren fehlgeschlagen: ${describe(e)}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "setActive", e)
            "Aktivieren fehlgeschlagen: ${describe(e)}"
        }
    }

    suspend fun remove(): String = withContext(Dispatchers.IO) {
        try {
            val face = manager.listWatchFaces().installedWatchFaceDetails.firstOrNull { it.packageName == FACE_PACKAGE }
                ?: return@withContext "Watchface ist nicht installiert"
            manager.removeWatchFace(face.slotId)
            "Watchface entfernt"
        } catch (e: Exception) {
            "Entfernen fehlgeschlagen: ${describe(e)}"
        }
    }

    private fun pump(writeFd: ParcelFileDescriptor, apk: () -> java.io.InputStream) {
        try {
            writeFd.use {
                apk().use { input ->
                    FileOutputStream(writeFd.fileDescriptor).use { out -> input.transferTo(out) }
                }
            }
        } catch (_: IOException) {
            // Das System schließt die Pipe vorzeitig, wenn es das Paket ablehnt.
        }
    }

    /** Versionscode des eingebetteten Watchface-Pakets. */
    private fun bundledVersion(): Long {
        val tmp = File.createTempFile("wface", ".apk", context.cacheDir)
        return try {
            context.assets.open(ASSET_APK).use { input -> FileOutputStream(tmp).use { input.copyTo(it) } }
            context.packageManager.getPackageArchiveInfo(tmp.absolutePath, PackageManager.PackageInfoFlags.of(0))
                ?.longVersionCode ?: 0
        } catch (_: Exception) {
            0
        } finally {
            tmp.delete()
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is WatchFacePushManager.AddWatchFaceException -> "${e.message} (Code ${e.errorCode})"
        is WatchFacePushManager.UpdateWatchFaceException -> "${e.message} (Code ${e.errorCode})"
        is WatchFacePushManager.SetWatchFaceAsActiveException -> "${e.message} (Code ${e.errorCode})"
        is WatchFacePushManager.ListWatchFacesException -> "${e.message} (Code ${e.errorCode})"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val TAG = "WatchFaceInstaller"
        const val FACE_PACKAGE = "de.mb.tagesring.watchfacepush.tagesring"
        const val ASSET_APK = "tagesring_watchface.apk"
        const val ASSET_TOKEN = "tagesring_watchface_token.txt"
        const val PERMISSION_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"
    }
}
