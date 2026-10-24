cmake_minimum_required(VERSION 3.22.1)
project(nexus_local_ai LANGUAGES C CXX)

# A universal APK must still install on 32-bit Android. The compact local model
# is supported only in a 64-bit process; no placeholder runtime is built here.
# UI, speech and the optional custom HTTP provider remain usable on 32-bit ABIs.
if(ANDROID AND (ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "x86"))
    message(STATUS "NEXUS: local LLM disabled for 32-bit ABI ${ANDROID_ABI}; no JNI runtime target")
    return()
endif()

# Downloaded and SHA256-verified by scripts/fetch_llama_cpp.py; never fetched
# by CMake while building. A local path can be passed for an offline build.
set(NEXUS_LLAMA_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/vendor/llama.cpp" CACHE PATH "Pinned llama.cpp sources")
if(NOT EXISTS "${NEXUS_LLAMA_SOURCE_DIR}/include/llama.h")
    message(FATAL_ERROR "llama.cpp absent. Run the repository's source-fetch script before compiling.")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# A debuggable APK still needs optimized tensor kernels on modest phones.
# Keep symbols while overriding any inherited -O0 for all upstream C/C++ code.
foreach(language C CXX)
    string(REGEX REPLACE "(^| )-O[^ ]+" "" nexus_debug_flags "${CMAKE_${language}_FLAGS_DEBUG}")
    set(CMAKE_${language}_FLAGS_DEBUG "${nexus_debug_flags} -O3" CACHE STRING "Optimized native debug inference" FORCE)
endforeach()
# Android's toolchain can shadow the cache flags with a normal variable. A
# directory compile option propagates to every upstream subdirectory regardless
# of CMP0126 or a shadowed CMAKE_*_FLAGS_DEBUG cache entry.
add_compile_options("$<$<CONFIG:Debug>:-O3>")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Link llama.cpp into one JNI library" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Do not use build-host instruction sets" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
# Snapdragon 845 / Exynos 9810 must not require ARM dotprod, i8mm or SVE.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "S9+ baseline" FORCE)
endif()
foreach(feature SSE42 AVX AVX2 AVX_VNNI AVX512 AVX512_VBMI AVX512_VNNI AVX512_BF16 BMI2 FMA F16C)
    set(GGML_${feature} OFF CACHE BOOL "Baseline emulator compatibility" FORCE)
endforeach()

add_subdirectory("${NEXUS_LLAMA_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama")
add_library(nexus_llama SHARED nexus_jni.cpp nexus_runtime.cpp)
target_include_directories(nexus_llama PRIVATE "${NEXUS_LLAMA_SOURCE_DIR}/vendor")
if(NOT ANDROID)
    find_package(JNI REQUIRED)
    target_include_directories(nexus_llama PRIVATE ${JNI_INCLUDE_DIRS})
endif()
target_link_libraries(nexus_llama PRIVATE llama)
target_compile_options(nexus_llama PRIVATE -Wall -Wextra -Wpedantic)
set_target_properties(nexus_llama PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
