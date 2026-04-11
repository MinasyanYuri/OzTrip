pluginManagement {
    repositories {

        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // ДОБАВЬ ВОТ ЭТУ СТРОКУ:
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases") }

    }
}
rootProject.name = "OzTrip"
include(":app")
 