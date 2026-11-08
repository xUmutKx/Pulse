plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Pulse AI's chat engine: llama.cpp (fetched by the workflow into localai/vendor, pinned commit) and the JNI of llama.cpp's Android example (MIT).
// Built for arm64 only; Pulse loads it on Android 9 and newer (the app's manifest overrides this library's minSdk).
val vendor = rootProject.file("localai/vendor").absolutePath

android {
    namespace = "com.arm.aichat"
    compileSdk = 36
    ndkVersion = "29.0.13113456"
    defaultConfig {
        minSdk = 28
        ndk { abiFilters += listOf("arm64-v8a") }
        consumerProguardFiles("proguard-rules.pro")
        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a")
                arguments += listOf(
                    "-DLLAMA_SRC=$vendor/llama.cpp",
                    "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=ON", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_COMMON=ON", "-DLLAMA_OPENSSL=OFF", "-DLLAMA_CURL=OFF",
                    "-DGGML_NATIVE=OFF", "-DGGML_BACKEND_DL=ON", "-DGGML_CPU_ALL_VARIANTS=ON", "-DGGML_LLAMAFILE=OFF",
                )
            }
        }
    }
    externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
