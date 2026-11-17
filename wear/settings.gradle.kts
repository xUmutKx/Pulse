pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
// Pulse Watch: the Wear OS app of Pulse, a project of its own (its own APK, its own toolchain); the phone app is not part of it
rootProject.name = "PulseWatch"
include(":app")
