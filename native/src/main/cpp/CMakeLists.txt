cmake_minimum_required(VERSION 3.22.1)
project(flint_native LANGUAGES C CXX)

include(FetchContent)

set(FLINT_NATIVE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")

# Flint owns the small public Android platform-loading boundary used by GLFW.
# It loads only public system EGL/Vulkan libraries and never bypasses linker namespaces.
add_library(flint_platform STATIC flint_platform.c)
target_include_directories(flint_platform PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_options(flint_platform PRIVATE -Wall -Wextra)
target_link_libraries(flint_platform PUBLIC dl)
set_target_properties(flint_platform PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Android GLFW backend used by LWJGL2/LWJGL3. Keep upstream revisions pinned.
set(GLFW_BUILD_ANDROID ON CACHE BOOL "Build Android GLFW backend" FORCE)
set(GLFW_BUILD_X11 OFF CACHE BOOL "Disable desktop X11 backend" FORCE)
set(GLFW_BUILD_WAYLAND OFF CACHE BOOL "Disable desktop Wayland backend" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "Disable GLFW examples" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "Disable GLFW tests" FORCE)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "Disable GLFW documentation" FORCE)
set(GLFW_INSTALL OFF CACHE BOOL "Disable GLFW install rules" FORCE)
set(GLFW_LIBRARY_TYPE SHARED CACHE STRING "Build GLFW as a shared library for LWJGL" FORCE)

FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/MojoLauncher/glfw.git
    GIT_TAG 7a6c6ceb51f20cd0db84dec2914ac1a0237051f6
    GIT_PROGRESS TRUE
)
FetchContent_GetProperties(glfw)
if(NOT glfw_POPULATED)
    FetchContent_Populate(glfw)
endif()

# Apply a narrow, checked adaptation to the pinned GLFW Android fork.
# The GLFW platform backend now calls Flint's independently implemented
# public-library loader and display metadata API instead of MojoExec.
function(flint_replace_glfw_source relative_path old_text new_text)
    set(source_file "${glfw_SOURCE_DIR}/${relative_path}")
    file(READ "${source_file}" source_text)
    string(FIND "${source_text}" "${old_text}" match_position)
    if(match_position EQUAL -1)
        message(FATAL_ERROR "Flint GLFW patch no longer matches ${relative_path}: ${old_text}")
    endif()
    string(REPLACE "${old_text}" "${new_text}" source_text "${source_text}")
    file(WRITE "${source_file}" "${source_text}")
endfunction()

flint_replace_glfw_source("src/android_init.c"
    "#include <mojoexec.h>" "#include <flint_platform.h>")
flint_replace_glfw_source("src/android_init.c"
    "mojoexec_acq_vulkan_handle" "flint_acquire_vulkan_handle")
flint_replace_glfw_source("src/android_init.c"
    "mojoexec_acq_egl_handle" "flint_acquire_egl_handle")

flint_replace_glfw_source("src/android_window.c"
    "#include <mojoexec.h>" "#include <flint_platform.h>")
flint_replace_glfw_source("src/android_window.c"
    "mojoexec_renderspec" "flint_render_config")
flint_replace_glfw_source("src/android_window.c"
    "force_gles_context" "request_es_context")
flint_replace_glfw_source("src/android_window.c"
    "override_major_version" "requested_es_major")

flint_replace_glfw_source("src/android_monitor.c"
    "#include <mojoexec.h>" "#include <flint_platform.h>")
flint_replace_glfw_source("src/android_monitor.c"
    "mojoexec_renderspec" "flint_render_config")
flint_replace_glfw_source("src/android_monitor.c"
    "disp_width" "display_width_px")
flint_replace_glfw_source("src/android_monitor.c"
    "disp_height" "display_height_px")
flint_replace_glfw_source("src/android_monitor.c"
    "disp_hz" "display_refresh_hz")

flint_replace_glfw_source("src/egl_context.c"
    "#include \"mojoexec.h\"" "#include <flint_platform.h>")
flint_replace_glfw_source("src/egl_context.c"
    "mojoexec_acq_egl_handle" "flint_acquire_egl_handle")
flint_replace_glfw_source("src/egl_context.c"
    "acquired client handle through mojoexec" "acquired EGL client handle through Flint")
flint_replace_glfw_source("src/egl_context.c"
    "#if defined(_GLFW_ANDROID)\n        mojoexec_make_bigcore_affine();\n#endif"
    "")

flint_replace_glfw_source("src/CMakeLists.txt"
    [=[
    if (NOT TARGET mojoexec)
        message(FATAL_ERROR "MojoExec is a required dependency! Make sure you have added it beforeincluding this")
    endif ()
    target_link_libraries(glfw PRIVATE android log mojoexec)
]=]
    [=[
    if (NOT TARGET flint_platform)
        message(FATAL_ERROR "Flint platform loader target is missing")
    endif ()
    target_include_directories(glfw PRIVATE "${FLINT_NATIVE_SOURCE_DIR}")
    target_link_libraries(glfw PRIVATE android log flint_platform)
]=])

add_subdirectory("${glfw_SOURCE_DIR}" "${glfw_BINARY_DIR}")

if(NOT TARGET glfw OR NOT TARGET flint_platform)
    message(FATAL_ERROR "Pinned Android GLFW/Flint platform targets were not created")
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
