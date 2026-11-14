cmake_minimum_required(VERSION 3.22.1)
project(flint_native LANGUAGES C CXX)

include(FetchContent)

add_library(flint_native SHARED
    flint_jni.cpp
    egl_smoke.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv2)

target_link_libraries(flint_native
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
)

target_compile_options(flint_native PRIVATE -Wall -Wextra -fvisibility=hidden)

# GL4ES translates desktop OpenGL 1.5/2.1 calls to Android OpenGL ES.
# Pin the exact upstream revision so native builds are reproducible.
set(ANDROID ON CACHE BOOL "Build GL4ES for Android" FORCE)
set(NOX11 ON CACHE BOOL "GL4ES: no X11 on Android" FORCE)
set(GBM OFF CACHE BOOL "GL4ES: no GBM on Android" FORCE)
set(USE_ANDROID_LOG ON CACHE BOOL "GL4ES: use Android log" FORCE)
set(DEFAULT_ES 2 CACHE STRING "GL4ES GLES backend" FORCE)
set(STATICLIB OFF CACHE BOOL "Build GL4ES shared library" FORCE)
set(EGL_WRAPPER OFF CACHE BOOL "Use Flint-owned EGL lifecycle" FORCE)
set(GLX_STUBS OFF CACHE BOOL "No GLX stubs needed for Android" FORCE)

FetchContent_Declare(
    gl4es
    GIT_REPOSITORY https://github.com/ptitSeb/gl4es.git
    GIT_TAG 8b1a3da5d2331d0f88b70be25963182d46eed857
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(gl4es)

if(NOT TARGET GL)
    message(FATAL_ERROR "Pinned GL4ES revision did not define the expected GL target")
endif()

# Upstream names its shared target GL but gives it the Unix SONAME suffix
# .so.1. Android's dynamic loader and LWJGL expect the conventional libGL.so.
set_target_properties(GL PROPERTIES
    OUTPUT_NAME "GL"
    SUFFIX ".so"
    VERSION ""
    SOVERSION ""
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/gl4es/${ANDROID_ABI}"
)

target_link_libraries(GL
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
    m
)
