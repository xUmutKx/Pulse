// Pulse Premium: a separate app, sold once on Google Play. Pulse unlocks its paid features only while this app
// is installed and signed with the Play signing key (see ads/AdPrompt.kt, Premium.UNLOCKER_CERT_SHA256).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val premiumKeystore: String? = System.getenv("PREMIUM_KEYSTORE_FILE")

android {
    namespace = "com.umutk.pulse.premium"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.umutk.pulse.premium"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    // Only an upload key lives in the secrets; Google Play re-signs the app with its own signing key, and that
    // certificate is the one Pulse checks. Without the secrets the APK is built unsigned (never signed with a public key).
    signingConfigs {
        create("upload") {
            if (premiumKeystore != null) {
                storeFile = file(premiumKeystore)
                storePassword = System.getenv("PREMIUM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PREMIUM_KEY_ALIAS")
                keyPassword = System.getenv("PREMIUM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (premiumKeystore != null) signingConfigs.getByName("upload") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation("com.android.billingclient:billing:7.1.1")
}
