plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.umutk.pulse"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.umutk.pulse"
        minSdk = 31
        targetSdk = 34
        versionCode = 168
        versionName = "0.10.158"
        ndk { abiFilters += "arm64-v8a" }
    }
    // one fixed key for every build (CI and Termux), so each new APK installs over the last one; the key is public on purpose (see ci/README.md)
    signingConfigs {
        create("pulse") {
            storeFile = rootProject.file("ci/pulse.keystore")
            storePassword = "pulsepulse"; keyAlias = "pulse"; keyPassword = "pulsepulse"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; aidl = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3") // reads the QR code of an authenticator from a picture
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment:1.5.4") // 1.3.0+ needed by ActivityResult APIs (lint InvalidFragmentVersionForActivityResult)
    implementation("androidx.biometric:biometric:1.1.0") // App Lock: fingerprint prompt (applock/LockService.kt)
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
    implementation("com.squareup.okio:okio:3.9.0")
    implementation("org.apache.commons:commons-compress:1.21") // 7z and tar.bz2 / tar.xz in the file manager (features/ExplorerArchive.kt)
    implementation("org.tukaani:xz:1.9") // LZMA / XZ for commons-compress
    implementation("com.github.junrar:junrar:7.5.5") // rar in the file manager
    implementation("org.slf4j:slf4j-api:1.7.36") // junrar logs through it
    implementation("com.android.tools.build:apksig:8.5.2") // signs the font pack APK on the phone (features/FontChanger.kt)
    implementation("androidx.media3:media3-exoplayer:1.4.1") // Apache-2.0; music player (extras/music), 1.4.1 is the last with minCompileSdk 34
    implementation("androidx.media3:media3-session:1.4.1") // MediaSessionService: background playback and media notification
    implementation("androidx.media3:media3-ui:1.4.1") // PlayerView in extras/video
    implementation("androidx.work:work-runtime-ktx:2.9.1") // ServiceWatchdog: periodic CheckWorker (extras/watchdog); 2.10 needs compileSdk 35
    // Apache-2.0; builds the MMS PDU for attachments in Messages (Messages.kt). okhttp 2.x is left out: the system MMS stack sends it, and okio 3 is already in use
    implementation("com.klinkerapps:android-smsmms:5.2.6") { exclude(group = "com.squareup.okhttp") }
    testImplementation("junit:junit:4.13.2") // JVM tests for extras/music (M3uParserTest)
}

composeCompiler { includeComposeMappingFile.set(false) }
