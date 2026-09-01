cmake_minimum_required(VERSION 3.22.1)

project("myai_native")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
)

find_library(
    log-lib
    log
)

target_link_libraries(
    myai_native
    ${log-lib}
    android
)
