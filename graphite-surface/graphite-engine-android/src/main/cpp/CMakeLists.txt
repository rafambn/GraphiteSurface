cmake_minimum_required(VERSION 3.22.1)

project(graphite-engine-android LANGUAGES CXX)

if(NOT GRAPHITE_SKIA_ROOT)
    message(FATAL_ERROR "GRAPHITE_SKIA_ROOT must point to the engine-owned Skia archive")
endif()

set(SKIA_OUT "${GRAPHITE_SKIA_ROOT}/out/Debug-android-arm64")
set(SKIA_INCLUDE "${GRAPHITE_SKIA_ROOT}/include")

if(NOT EXISTS "${SKIA_OUT}/libskia_graphite_ext.a")
    message(FATAL_ERROR "Missing Android Graphite Skia archive at ${SKIA_OUT}")
endif()

add_library(graphite-engine-android SHARED GraphiteEngine.cpp)

target_compile_features(graphite-engine-android PRIVATE cxx_std_20)
target_compile_definitions(
    graphite-engine-android
    PRIVATE
        SK_BUILD_FOR_ANDROID=1
        SK_GANESH=1
        SK_GRAPHITE=1
        VK_USE_PLATFORM_ANDROID_KHR=1
)
target_include_directories(
    graphite-engine-android
    PRIVATE
        "${GRAPHITE_SKIA_ROOT}"
        "${SKIA_INCLUDE}"
)

file(GLOB SKIA_STATIC_LIBRARIES "${SKIA_OUT}/*.a")
# Skia ships allocator_shim for embedders that deliberately wrap process-wide malloc/free.
# The Android shared library must use the platform allocator, as does Skiko's Android target.
list(FILTER SKIA_STATIC_LIBRARIES EXCLUDE REGEX "/liballocator_shim\\.a$")
find_library(ANDROID_LIBRARY android)
find_library(LOG_LIBRARY log)
find_library(VULKAN_LIBRARY vulkan)
find_library(DL_LIBRARY dl)

target_link_options(
    graphite-engine-android
    PRIVATE
        "-Wl,--gc-sections"
        "-Wl,--exclude-libs,ALL"
)
target_link_libraries(
    graphite-engine-android
    PRIVATE
        "-Wl,--start-group"
        ${SKIA_STATIC_LIBRARIES}
        "-Wl,--end-group"
        ${ANDROID_LIBRARY}
        ${LOG_LIBRARY}
        ${VULKAN_LIBRARY}
        ${DL_LIBRARY}
        c++_static
)
