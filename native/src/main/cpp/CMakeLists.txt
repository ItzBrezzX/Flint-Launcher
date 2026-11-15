cmake_minimum_required(VERSION 3.22.1)
project(flint_native LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# Flint native bridge (clean-room, written from scratch).
#
# Design decision (project owner): the GLFW stub, JNI bridge, EGL/Surface
# wrapper and input bridge are Flint's own code (permissive licence).
# Third-party renderers are OPTIONAL backends, loaded as separate plugins:
#   - GL4ES (MIT)  : optional, built from source when FLINT_BUILD_GL4ES=ON,
#                    or shipped as a download-on-demand libGL.so plugin.
#   - OpenAL Soft (LGPL-2.1+) : dynamic-linked only, never statically linked
#                    into flint_native; loaded at runtime via dlopen.
# Copyleft libraries are never statically linked into flint_native.
# See docs/RUNTIME_ARTIFACTS.md for URLs, SHA-256 and licence attribution.
# ---------------------------------------------------------------------------
option(FLINT_BUILD_GL4ES "Build GL4ES (MIT) from source as the optional libGL.so backend" OFF)
option(FLINT_GL4ES_FETCH "Fetch GL4ES source via FetchContent (requires network at build time)" ON)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv2)
find_library(dl-lib dl)
find_library(m-lib m)

set(FLINT_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

if(FLINT_BUILD_GL4ES)
    if(FLINT_GL4ES_FETCH)
        include(FetchContent)
        set(ANDROID ON CACHE BOOL "Build GL4ES for Android" FORCE)
        set(NOX11 ON CACHE BOOL "No X11 window system on Android" FORCE)
        set(NO_GBM ON CACHE BOOL "No GBM on Android" FORCE)
        set(NO_INIT_CONSTRUCTOR ON CACHE BOOL "Initialize GL4ES with a current EGL context" FORCE)
        set(USE_ANDROID_LOG ON CACHE BOOL "Use Android logcat" FORCE)
        set(DEFAULT_ES 2 CACHE STRING "Use GLES2 backend" FORCE)
        set(STATICLIB OFF CACHE BOOL "Build loadable libGL.so" FORCE)
        set(EGL_WRAPPER OFF CACHE BOOL "Use Android EGL directly" FORCE)
        FetchContent_Declare(
            gl4es
            GIT_REPOSITORY https://github.com/ptitSeb/gl4es.git
            GIT_TAG 8b1a3da5d2331d0f88b70be25963182d46eed857
            GIT_SHALLOW FALSE
        )
        FetchContent_MakeAvailable(gl4es)
        if(NOT TARGET GL)
            message(FATAL_ERROR "Pinned GL4ES source did not define the GL target")
        endif()
        # LWJGL resolves desktop OpenGL symbols dynamically. Package GL4ES as
        # libGL.so with default visibility, output to the ABI directory.
        set_target_properties(GL PROPERTIES
            OUTPUT_NAME "GL"
            PREFIX "lib"
            SUFFIX ".so"
            LIBRARY_OUTPUT_DIRECTORY "${FLINT_LIBRARY_OUTPUT_DIRECTORY}"
            C_VISIBILITY_PRESET default
        )
        target_compile_options(GL PRIVATE -fvisibility=default)
        target_link_libraries(GL ${log-lib} ${android-lib} ${egl-lib} ${gles-lib} ${dl-lib} ${m-lib})
        message(STATUS "Flint: GL4ES (MIT) backend will be built from source.")
    else()
        message(STATUS "Flint: FLINT_BUILD_GL4ES=ON but fetch disabled; expecting prebuilt libGL.so plugin.")
    endif()
else()
    message(STATUS "Flint: GL4ES backend disabled at build time; libGL.so expected as runtime plugin.")
endif()

# ---------------------------------------------------------------------------
# flint_native: Flint's own clean-room bridge. Links only against system
# libraries (EGL, GLESv2, android, log, dl, m). Third-party renderers are
# resolved at runtime via dlopen (see flint_jni.cpp nativeLoadGL4ES).
# ---------------------------------------------------------------------------
add_library(flint_native SHARED
    flint_jni.cpp
    egl_smoke.cpp
    glfw_stub.cpp
    gl_symbol_loader.cpp
    input_bridge.cpp
)

target_include_directories(flint_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(flint_native
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    ${dl-lib}
    ${m-lib}
)

target_compile_options(flint_native PRIVATE -Wall -Wextra -fvisibility=hidden)
