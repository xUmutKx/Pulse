plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Pulse AI: local chat (llama.cpp) and speech to text (whisper.cpp), an extra app of Pulse with its own APK.
// The llama.cpp and whisper.cpp sources are fetched by the ai.yml workflow into localai/vendor (pinned commits), never committed.
val vendor = rootProject.file("localai/vendor").absolutePath

android {
    namespace = "com.umutk.pulse.ai"
    compileSdk = 36
    ndkVersion = "29.0.13113456"
    defaultConfig {
        applicationId = "com.umutk.pulse.extra.ai"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLLAMA_SRC=$vendor/llama.cpp",
                    "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=ON", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_COMMON=ON", "-DLLAMA_OPENSSL=OFF", "-DLLAMA_CURL=OFF",
                    "-DGGML_NATIVE=OFF", "-DGGML_BACKEND_DL=ON", "-DGGML_CPU_ALL_VARIANTS=ON", "-DGGML_LLAMAFILE=OFF",
                )
            }
        }
    }
    externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    signingConfigs {
        create("pulse") {
            storeFile = file(System.getenv("PULSE_KEYSTORE") ?: "pulse-missing.keystore")
            storePassword = System.getenv("PULSE_STORE_PASSWORD"); keyAlias = "pulse"; keyPassword = System.getenv("PULSE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("pulse")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    // the CPU back ends of ggml are loaded from the library folder at run time: the libraries must be extracted
    packaging { jniLibs { useLegacyPackaging = true }; resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":aistt"))
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
