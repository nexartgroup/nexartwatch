pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android (Wireless-ADB in der Handy-App) gibt es nur über JitPack
        maven("https://jitpack.io") {
            content { includeGroup("com.github.MuntashirAkon") }
        }
    }
}

rootProject.name = "neXartWatch"
include(":watchface", ":watch", ":phone")
