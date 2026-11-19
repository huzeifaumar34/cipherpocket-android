cmake_minimum_required(VERSION 3.22)
project(cipherpocket_native LANGUAGES CXX)

include(CTest)
include(FetchContent)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.17.0
    GIT_SHALLOW TRUE
)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

find_package(OpenSSL 3.0 REQUIRED COMPONENTS Crypto)

add_subdirectory(../core/src/main/cpp/engine engine)
target_link_libraries(cp_engine PUBLIC OpenSSL::Crypto)

add_executable(cipherpocket-cli src/main.cpp)
target_link_libraries(cipherpocket-cli PRIVATE cp_engine)

add_executable(cipherpocket-engine-tests tests/base64_tests.cpp)
target_link_libraries(cipherpocket-engine-tests PRIVATE cp_engine GTest::gtest_main)

include(GoogleTest)
gtest_discover_tests(cipherpocket-engine-tests)
