import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.umutk.pulse.watch"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.umutk.pulse.watch"
        // Wear OS 3 and newer
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // the release is signed with Pulse's own key (the workflow writes keystore.properties from the secrets); without it, the debug key
    val keys = rootProject.file("keystore.properties")
    val release = if (keys.isFile) {
        val p = Properties().apply { FileInputStream(keys).use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else signingConfigs.getByName("debug")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = release
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    // Wear OS: the scaling list with the crown, curved text, and the navigation where a swipe to the right goes back
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("androidx.wear.compose:compose-material:1.7.1") // curvedText
    implementation("androidx.wear.compose:compose-navigation:1.7.1")
    // the watch's own text input (keyboard, voice, handwriting) for the notes
    implementation("androidx.wear:wear-input:1.2.0")
}
