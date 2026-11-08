plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Speech to text for Pulse AI: whisper.cpp (fetched by the workflow into localai/vendor/whisper.cpp), linked statically into one library with hidden symbols,
// so its own copy of ggml never meets the shared ggml of llama.cpp in the same app.
val vendor = rootProject.file("localai/vendor").absolutePath

android {
    namespace = "com.umutk.pulse.ai.stt"
    compileSdk = 36
    ndkVersion = "29.0.13113456"
    defaultConfig {
        minSdk = 33
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DWHISPER_SRC=$vendor/whisper.cpp", "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF", "-DWHISPER_BUILD_TESTS=OFF", "-DWHISPER_BUILD_SERVER=OFF", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF",
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16")
            }
        }
    }
    externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}
