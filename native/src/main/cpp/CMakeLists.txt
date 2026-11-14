cmake_minimum_required(VERSION 3.22.1)
project(flint_native LANGUAGES C CXX)

include(FetchContent)

# Android GLFW backend used by LWJGL2/LWJGL3. Keep upstream revisions pinned;
# GLFW's Android platform uses MojoExec for Android linker/EGL integration.
set(GLFW_BUILD_ANDROID ON CACHE BOOL "Build Android GLFW backend" FORCE)
set(GLFW_BUILD_X11 OFF CACHE BOOL "Disable desktop X11 backend" FORCE)
set(GLFW_BUILD_WAYLAND OFF CACHE BOOL "Disable desktop Wayland backend" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "Disable GLFW examples" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "Disable GLFW tests" FORCE)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "Disable GLFW documentation" FORCE)
set(GLFW_INSTALL OFF CACHE BOOL "Disable GLFW install rules" FORCE)
set(GLFW_LIBRARY_TYPE SHARED CACHE STRING "Build GLFW as a shared library for LWJGL" FORCE)

FetchContent_Declare(
    mojoexec
    GIT_REPOSITORY https://github.com/MojoLauncher/mojoexec.git
    GIT_TAG 9ec4b836e93900135bad823eaac16ebc81cf6b98
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(mojoexec)

FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/MojoLauncher/glfw.git
    GIT_TAG 7a6c6ceb51f20cd0db84dec2914ac1a0237051f6
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(glfw)

if(NOT TARGET glfw OR NOT TARGET mojoexec)
    message(FATAL_ERROR "Pinned Android GLFW/MojoExec targets were not created")
endif()

set_target_properties(glfw PROPERTIES
    OUTPUT_NAME "glfw"
    SUFFIX ".so"
    VERSION ""
    SOVERSION ""
)

add_library(flint_native SHARED
    flint_jni.cpp
    egl_smoke.cpp
    egl_window.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv2)

target_link_libraries(flint_native PRIVATE
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
set(NOEGL ON CACHE BOOL "GL4ES: Flint owns EGL context and window lifecycle" FORCE)
set(NO_LOADER ON CACHE BOOL "GL4ES: resolve GLES through Flint EGL context" FORCE)
set(NO_INIT_CONSTRUCTOR ON CACHE BOOL "GL4ES: initialize only after Flint makes EGL current" FORCE)
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

target_compile_definitions(GL PRIVATE GL4ES_COMPILE_FOR_USE_IN_SHARED_LIB)

# Flint owns the EGL display/context and supplies the current-context GLES resolver.
target_include_directories(flint_native PRIVATE "${gl4es_SOURCE_DIR}/include")

target_link_libraries(flint_native PRIVATE GL glfw)

target_link_libraries(GL
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
    dl
    m
)
