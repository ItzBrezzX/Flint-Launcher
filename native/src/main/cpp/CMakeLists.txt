cmake_minimum_required(VERSION 3.22.1)
project(flint_native LANGUAGES C CXX)

# Preserve the Android Gradle Plugin's per-ABI output directory before the
# upstream GL4ES CMake project changes its own output defaults.
set(FLINT_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

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

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv2)
find_library(dl-lib dl)
find_library(m-lib m)

# Build the Android/Bionic LWJGL natives against the exact pinned Java ABI.
include(${CMAKE_CURRENT_LIST_DIR}/flint_lwjgl_android.cmake)

# LWJGL resolves desktop OpenGL symbols dynamically. Package GL4ES as libGL.so
# and expose its entry points process-wide after the Android EGL context exists.
set_target_properties(GL PROPERTIES
    OUTPUT_NAME "GL"
    PREFIX "lib"
    SUFFIX ".so"
    LIBRARY_OUTPUT_DIRECTORY "${FLINT_LIBRARY_OUTPUT_DIRECTORY}"
    C_VISIBILITY_PRESET default
)
target_compile_options(GL PRIVATE -fvisibility=default)
# Upstream GL4ES uses the plain target_link_libraries signature for GL;
# keep this call plain as well (CMake forbids mixing plain/keyword signatures).
target_link_libraries(GL ${log-lib} ${android-lib} ${egl-lib} ${gles-lib} ${dl-lib} ${m-lib})

# Flint owns this Android GLFW-compatible C ABI. It is intentionally a
# separate libglfw.so so an Android-built LWJGL JNI adapter can bind to it.
add_library(flint_glfw SHARED
    flint_glfw_backend.cpp
    flint_input_queue.cpp
)
set_target_properties(flint_glfw PROPERTIES
    OUTPUT_NAME "glfw"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)
target_compile_options(flint_glfw PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_libraries(flint_glfw
    ${egl-lib}
    ${dl-lib}
    ${log-lib}
)

add_library(flint_native SHARED
    flint_jni.cpp
    egl_smoke.cpp
    flint_host.cpp
)
target_link_libraries(flint_native
    GL
    flint_glfw
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    ${dl-lib}
)
target_compile_options(flint_native PRIVATE -Wall -Wextra -fvisibility=hidden)
add_dependencies(flint_native flint_lwjgl_opengl)
