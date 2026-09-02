cmake_minimum_required(VERSION 3.22.1)

project("myai_native")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

if (NOT EXISTS ${THIRD_PARTY_DIR}/llama.cpp/CMakeLists.txt)
    message(FATAL_ERROR "Missing llama.cpp dependency at ${THIRD_PARTY_DIR}/llama.cpp")
endif()

if (NOT EXISTS ${THIRD_PARTY_DIR}/whisper.cpp/CMakeLists.txt)
    message(FATAL_ERROR "Missing whisper.cpp dependency at ${THIRD_PARTY_DIR}/whisper.cpp")
endif()

# Build third-party engines as static libraries and link into libmyai_native.so.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static third-party libraries" FORCE)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)

set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

if (ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+fp16+dotprod)
endif()

add_subdirectory(${THIRD_PARTY_DIR}/llama.cpp EXCLUDE_FROM_ALL)
add_subdirectory(${THIRD_PARTY_DIR}/whisper.cpp EXCLUDE_FROM_ALL)

# Compile native JNI bindings
add_library(
    myai_native
    SHARED
    llama-jni.cpp
    whisper-jni.cpp
)

# Include directories
target_include_directories(
    myai_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${THIRD_PARTY_DIR}/llama.cpp/include
    ${THIRD_PARTY_DIR}/whisper.cpp/include
)

find_library(
    log-lib
    log
)

target_link_libraries(
    myai_native
    llama
    ggml
    whisper
    ${log-lib}
    android
)
