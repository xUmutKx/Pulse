pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Pulse"
include(":app")

// Pulse AI (its own APK, an extra app): built only by .github/workflows/ai.yml with -PwithAi, which first fetches llama.cpp and whisper.cpp into localai/vendor
if (providers.gradleProperty("withAi").isPresent) {
    include(":localai")
    include(":aistt")
}
