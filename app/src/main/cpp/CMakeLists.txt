cmake_minimum_required(VERSION 3.22)
project(tesla_android_bridge LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
include(FetchContent)
# A real Tesla protocol engine: pairing, authenticated sessions, encrypted state reads.
# Pinned upstream source snapshot (2026-10-12). Not device-validated.
FetchContent_Declare(tesla_ble
    GIT_REPOSITORY https://github.com/yoziru/tesla-ble.git
    GIT_TAG 42b899b7816cc3700b7361e65b526fdc04d19a51
    GIT_SHALLOW FALSE)
FetchContent_MakeAvailable(tesla_ble)
add_library(tesla-android SHARED native_bridge.cpp)
target_link_libraries(tesla-android PRIVATE TeslaBLE android log)
target_include_directories(tesla-android PRIVATE ${tesla_ble_SOURCE_DIR}/include ${tesla_ble_SOURCE_DIR}/generated/include)
