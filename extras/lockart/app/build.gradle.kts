import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La clave de firma y sus contraseñas van en keystore.properties (no se sube al repositorio):
//   storeFile=lockart.keystore, storePassword=..., keyAlias=..., keyPassword=...
// Sin ese archivo, la versión release se firma con la clave de depuración.
val keystore = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

android {
    namespace = "com.elcesarmaat.lockart"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.elcesarmaat.lockart"
        minSdk = 31
        targetSdk = 37
        versionCode = 19
        versionName = "4.8"
        // Solo el Pixel (arm64): DexKit trae una librería nativa por ABI.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (keystore.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    // Solo inglés (por defecto) y español: quita también las traducciones que traen las librerías.
    androidResources {
        localeFilters += listOf("en", "es")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // Material 3 Expressive (el estilo de Android 16/17) solo es público desde la 1.5; en la 1.4 estable es interno.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")

    // API moderna de LSPosed: la pone el framework en tiempo de ejecución. El servicio es para la app (ajustes remotos).
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    // Para encontrar en tiempo de ejecución las clases ofuscadas de Spotify (OkHttp Headers).
    implementation("org.luckypray:dexkit:2.3.0")
}
