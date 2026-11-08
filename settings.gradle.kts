pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Pulse"
include(":app")

// Pulse AI's engines (llama.cpp and whisper.cpp, arm64): the workflow fetches their sources into localai/vendor first
include(":localai")
include(":aistt")
