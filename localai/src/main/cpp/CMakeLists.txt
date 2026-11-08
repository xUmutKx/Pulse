# Pulse AI: llama.cpp (fetched into localai/vendor/llama.cpp by the workflow) and the JNI of llama.cpp's Android example (ai_chat.cpp, MIT).
cmake_minimum_required(VERSION 3.22)
project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_CPU_KLEIDIAI ON)
    set(GGML_OPENMP ON)
endif()

# small libraries: no debug information, unused code dropped, symbols stripped at link time
add_compile_options(-g0 -ffunction-sections -fdata-sections)
add_link_options(-Wl,--gc-sections -Wl,--strip-all)
if(NOT DEFINED LLAMA_SRC)
    message(FATAL_ERROR "LLAMA_SRC is not set (the workflow fetches llama.cpp into localai/vendor)")
endif()
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED ai_chat.cpp)
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC} ${LLAMA_SRC}/common ${LLAMA_SRC}/include ${LLAMA_SRC}/ggml/include ${LLAMA_SRC}/ggml/src)
target_link_libraries(${CMAKE_PROJECT_NAME} llama llama-common android log)
