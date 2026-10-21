// Reines WFF-Paket (kein Code). Wird von der Uhr-App per Watch Face Push installiert.
// Paketname muss "<Paket der Uhr-App>.watchfacepush.<name>" lauten.
plugins {
    id("com.android.application")
}

android {
    namespace = "de.mb.tagesring.watchfacepush.tagesring"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.mb.tagesring.watchfacepush.tagesring"
        minSdk = 36
        targetSdk = 36
        // Jeder Build bekommt eine höhere Version (Minuten seit 1970), damit Watch Face Push
        // ihn als Update akzeptiert.
        versionCode = (System.currentTimeMillis() / 60_000L).toInt()
        versionName = "2.0"
    }

    signingConfigs {
        // Watch Face Push verlangt für Watchfaces einen anderen Schlüssel als für die App.
        create("watchface") {
            storeFile = rootProject.file("keystores/watchface.jks")
            storePassword = "tagesring-wf"
            keyAlias = "watchface"
            keyPassword = "tagesring-wf"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("watchface")
            // R8 entfernt die leere Dex-Datei; Ressourcen bleiben (kein shrinkResources!)
            isMinifyEnabled = true
            isShrinkResources = false
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}
