import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.mb.tagesring"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.mb.tagesring"
        // Watch Face Push gibt es ab Wear OS 6 (API 36)
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Zum Sideloaden reicht der Debug-Schlüssel (derselbe wie in der Handy-App, nötig
            // für die Verbindung zwischen Handy- und Uhr-App).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }

    // Gemeinsame Vereinbarung mit der Handy-App
    sourceSets["main"].java.srcDir("../shared/src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("androidx.wear.watchfacepush:watchfacepush:1.0.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}

// ---------------------------------------------------------------------------------------------
// Das Watchface (Modul :watchface) wird mit seinem Prüf-Token als Asset in die Uhr-App
// eingebettet. Das Token erzeugt Googles Validator (validator-push-cli) beim Bauen.
// ---------------------------------------------------------------------------------------------

val validatorCli: Configuration by configurations.creating

dependencies {
    validatorCli("com.google.android.wearable.watchface.validator:validator-push-cli:1.1.0-alpha01")
}

abstract class WatchFaceAssetsTask : DefaultTask() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:InputFiles
    abstract val validator: ConfigurableFileCollection

    @get:Input
    abstract val appPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        val target = File(out, "tagesring_watchface.apk")
        apk.get().asFile.copyTo(target, overwrite = true)

        val stdout = ByteArrayOutputStream()
        exec.exec {
            commandLine(
                File(System.getProperty("java.home"), "bin/java").absolutePath,
                "-jar", validator.singleFile.absolutePath,
                "--apk_path=${target.absolutePath}",
                "--package_name=${appPackage.get()}",
            )
            standardOutput = stdout
            isIgnoreExitValue = true
        }
        val text = stdout.toString()
        val token = Regex("generated token: (\\S+)").find(text)?.groupValues?.get(1)
            ?: throw GradleException("Watchface-Validierung fehlgeschlagen:\n$text")
        File(out, "tagesring_watchface_token.txt").writeText(token)
    }
}

val watchFaceAssets = tasks.register<WatchFaceAssetsTask>("generateWatchFaceAssets") {
    dependsOn(":watchface:assembleRelease")
    apk.set(project(":watchface").layout.buildDirectory.file("outputs/apk/release/watchface-release.apk"))
    validator.from(validatorCli)
    appPackage.set("de.mb.tagesring")
    outputDir.set(layout.buildDirectory.dir("generated/watchfaceAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(watchFaceAssets, WatchFaceAssetsTask::outputDir)
    }
}
