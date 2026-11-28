import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A lean app of Pulse (-PleanApp=<id>, see lean/apps.txt): the app's own code from Pulse's source tree on a small Compose base (src/main/java/com/umutk/pulse/lean holds the
// stand-ins of Pulse's look kit: colours, text, icons, chips and fields), so the APK is a few MB instead of the 40 MB of the whole of Pulse.
val leanApp = (project.findProperty("leanApp") as String?)?.trim().orEmpty().ifEmpty { "calculator" }
val appRow = rootProject.file("lean/apps.txt").readLines().map { it.split("|") }.first { it.size >= 3 && it[0] == leanApp }
val appVersion = Regex("versionName = \"([^\"]+)\"").find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]
val appCode = Regex("versionCode = (\\d+)").find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1].toInt()

android {
    namespace = "com.umutk.pulse.lean"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.umutk.pulse.$leanApp"
        minSdk = 21
        targetSdk = 34
        versionCode = appCode
        versionName = appVersion
        resourceConfigurations += listOf("en", "tr")
        manifestPlaceholders["appName"] = appRow[1]
        buildConfigField("String", "APP", "\"$leanApp\"")
    }
    signingConfigs {
        create("pulse") {
            storeFile = file(System.getenv("PULSE_KEYSTORE") ?: "pulse-missing.keystore")
            storePassword = System.getenv("PULSE_STORE_PASSWORD"); keyAlias = "pulse"; keyPassword = System.getenv("PULSE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("pulse")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
    // the app's own code, from Pulse's source tree (Kotlin does not need the folders to follow the packages), and the pure Kotlin that Pulse and the lean apps share
    sourceSets.getByName("main") {
        for (d in appRow[2].split(",")) java.srcDir(rootProject.file("app/src/main/java/com/umutk/pulse/$d"))
        java.srcDir(rootProject.file("app/src/main/java/com/umutk/pulse/leanshared"))
    }
    packaging { resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "META-INF/*.version") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
