import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A lean app of Pulse (-PleanApp=<id>, see lean/apps.txt): the app's own code from Pulse's source tree on a small Compose base (src/main/java/com/umutk/pulse/lean holds the
// stand-ins of Pulse's look kit: colours, text, icons, chips and fields), so the APK is a few MB instead of the 40 MB of the whole of Pulse.
val leanApp = (project.findProperty("leanApp") as String?)?.trim().orEmpty().ifEmpty { "calculator" }
val appRow = rootProject.file("lean/apps.txt").readLines().filter { !it.startsWith("#") }.map { it.split("|") }.first { it.size >= 3 && it[0] == leanApp }
val appPerms = appRow.getOrElse(3) { "" }.split(",").map { it.trim() }.filter { it.isNotEmpty() }
val appLibs = appRow.getOrElse(4) { "" }.split(",").map { it.trim() }.filter { it.isNotEmpty() }
val appVersion = Regex("versionName = \"([^\"]+)\"").find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]
val appCode = Regex("versionCode = (\\d+)").find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1].toInt()

// the manifest of this app: its name, its permissions and its one activity (written here so that each app asks only for its own)
val manifestFile = layout.buildDirectory.file("leanManifest/AndroidManifest.xml").get().asFile
manifestFile.parentFile.mkdirs()
manifestFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
${appPerms.joinToString("\n") { "    <uses-permission android:name=\"android.permission.$it\" />" }}
    <application
        android:label="${appRow[1]}"
        android:icon="@drawable/ic_launcher"
        android:roundIcon="@drawable/ic_launcher"
        android:allowBackup="true"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name="com.umutk.pulse.lean.LeanActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode|screenLayout|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")

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
    sourceSets.getByName("main").manifest.srcFile(manifestFile)
    // the app's own code, from Pulse's source tree (Kotlin does not need the folders to follow the packages), and the pure Kotlin that Pulse and the lean apps share
    sourceSets.getByName("main") {
        for (d in appRow[2].split(",")) java.srcDir(rootProject.file("app/src/main/java/com/umutk/pulse/$d"))
        java.srcDir(rootProject.file("app/src/main/java/com/umutk/pulse/leanshared"))
        java.srcDir(file("src/apps/$leanApp"))
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
    for (l in appLibs) implementation(l)
}
