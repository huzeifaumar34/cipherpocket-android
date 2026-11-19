cmake_minimum_required(VERSION 3.22.1)
project(cipherpocket_android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory(engine)

add_library(cipherpocket_jni SHARED
    jni/native_bridge.cpp
)

target_compile_options(cipherpocket_jni PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -fvisibility=hidden
)

target_link_options(cipherpocket_jni PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--exclude-libs,ALL"
)

target_link_libraries(cipherpocket_jni PRIVATE cp_engine)
