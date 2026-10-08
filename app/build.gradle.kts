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
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "0.10"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
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
}

composeCompiler { includeComposeMappingFile.set(false) }
