plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.umutk.pulse"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.umutk.pulse"
        minSdk = (project.findProperty("pulseMinSdk") as String?)?.toIntOrNull() ?: 21 // -PpulseMinSdk=31 builds the Android 12+ flavour of the same code
        targetSdk = 34
        versionCode = 780
        versionName = "1.0.379"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        resourceConfigurations += listOf("en", "tr")
    }
    // release key: private, kept in GitHub Actions secrets (see ci/README.md); the workflow writes it to a file and passes its path
    signingConfigs {
        create("pulse") {
            storeFile = file(System.getenv("PULSE_KEYSTORE") ?: "pulse-missing.keystore")
            storePassword = System.getenv("PULSE_STORE_PASSWORD"); keyAlias = "pulse"; keyPassword = System.getenv("PULSE_KEY_PASSWORD")
        }
    }
    // foss: the full app, no ads and no Google SDK; play: the same app with the AdMob ads (src/play)
    flavorDimensions += "store"
    productFlavors {
        // APK_INSTALL: the app may install APK files itself (REQUEST_INSTALL_PACKAGES). Off in the play build: Play declares that permission separately
        create("foss") { dimension = "store"; buildConfigField("boolean", "APK_INSTALL", "true") }
        create("play") { dimension = "store"; targetSdk = 36; buildConfigField("boolean", "APK_INSTALL", "false") } // Google Play requires API 36 for new uploads; the foss flavor keeps 34
    }
    buildTypes {
        release {
            ndk { debugSymbolLevel = "SYMBOL_TABLE" } // native symbols in the bundle: no "missing symbols" warning in Play
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("pulse")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    lint { checkOnly += setOf("NewApi"); abortOnError = false; textReport = true; textOutput = file("lint-newapi.txt") }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    // Pulse AI: ggml loads its CPU back ends from the library folder at run time, so the native libraries are extracted on install
    packaging { jniLibs { useLegacyPackaging = true } }
    packaging { resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/versions/9/module-info.class") } // the last two: BouncyCastle ships them in several jars
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4") // java.time, Iterable.forEach ... on Android 5 to 7
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") { // reads the text of a PDF for the book reader (text PDFs are reflowed)
        exclude(group = "org.bouncycastle") // the app already ships bcprov 1.79 (bcpkix below); the 1.72 copy clashes with it
    }
    implementation(project(":localai")) // Pulse AI chat: llama.cpp (arm64)
    implementation(project(":aistt")) // Pulse AI speech to text: whisper.cpp (arm64)
    implementation("com.google.zxing:core:3.5.3") // reads the QR code of an authenticator from a picture
    add("playImplementation", "com.android.billingclient:billing:8.0.0") // Google Play one-time purchase (Premium), the play build only
    add("playImplementation", "com.google.android.gms:play-services-ads:23.6.0") // AdMob: banner and rewarded ads, the play build only
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.autofill:autofill:1.1.0") // the password manager's suggestions inside the keyboard bar (inline autofill, Android 11+)
    implementation("androidx.webkit:webkit:1.11.0") // Browser (extras/browser): force-dark on the phone's own WebView, no bundled engine
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
    implementation("net.lingala.zip4j:zip4j:2.11.5") // password-protected zip (AES), written and read in the file manager (features/ExplorerZip.kt, ExplorerArchive.kt)
    implementation("com.github.junrar:junrar:7.5.5") // rar in the file manager
    implementation("org.slf4j:slf4j-api:1.7.36") // junrar logs through it
    implementation("com.android.tools.build:apksig:8.5.2") // signs the font pack APK on the phone (features/FontChanger.kt)
    implementation("androidx.media3:media3-exoplayer:1.4.1") // Apache-2.0; music player (extras/music), 1.4.1 is the last with minCompileSdk 34
    implementation("androidx.media3:media3-session:1.4.1") // MediaSessionService: background playback and media notification
    implementation("androidx.media3:media3-transformer:1.4.1") // Media tools: shrinking and converting video and music
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1") // PlayerView in extras/video
    compileOnly(files("libs/xposed-stub.jar")) // BlackOut (extras/blackout): the Xposed API classes come from LSPosed / Vector at run time
    implementation("androidx.datastore:datastore-preferences:1.1.1") // Lucid clock (extras/lucid): its settings
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3") // Lucid clock: viewModel() in Compose
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") // Relay (extras/relay, Apache-2.0): its protocol messages
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79") // Relay: the self-signed certificate of this device (mutual TLS)
    implementation("androidx.palette:palette-ktx:1.0.0") // Palette (extras/palette): the colours of a wallpaper
    implementation("androidx.work:work-runtime-ktx:2.9.1") // ServiceWatchdog: periodic CheckWorker (extras/watchdog); 2.10 needs compileSdk 35
    // Apache-2.0; builds the MMS PDU for attachments in Messages (Messages.kt). okhttp 2.x is left out: the system MMS stack sends it, and okio 3 is already in use
    implementation("com.klinkerapps:android-smsmms:5.2.6") { exclude(group = "com.squareup.okhttp") }
    testImplementation("junit:junit:4.13.2") // JVM tests for extras/music (M3uParserTest)
}

composeCompiler { includeComposeMappingFile.set(false) }
