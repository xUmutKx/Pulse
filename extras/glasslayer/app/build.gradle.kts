import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.drdisagree.glasslayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // the One UI edition of Pulse: its own package, so it installs beside the original Glass Layer and never replaces it
        applicationId = "com.umutk.pulse.glasslayer"
        minSdk = 36
        targetSdk = 37
        versionCode = 4
        versionName = "1.1.0-oneui.1"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    var releaseSigning = signingConfigs.getByName("debug")

    try {
        val keystoreProperties = Properties()
        FileInputStream(keystorePropertiesFile).use { inputStream ->
            keystoreProperties.load(inputStream)
        }

        releaseSigning = signingConfigs.create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    } catch (_: Exception) {
    }

    buildTypes {
        debug {
            signingConfig = releaseSigning
        }
        release {
            optimization {
                enable = true
            }
            signingConfig = releaseSigning
        }
        create("dev") {
            initWith(getByName("release"))
            optimization {
                enable = true
            }
            signingConfig = releaseSigning
            matchingFallbacks += listOf("release")
        }
        create("review") {
            initWith(getByName("release"))
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName = "GlassLayer-OneUI-${android.defaultConfig.versionName}.apk"
            }
        }
    }
}

tasks.register("printVersionName") {
    val versionName = android.defaultConfig.versionName
    doLast {
        println(versionName)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
