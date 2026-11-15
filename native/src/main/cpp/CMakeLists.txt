cmake_minimum_required(VERSION 3.22.1)
project(flint_native)

add_library(flint_native SHARED
    flint_jni.cpp
    egl_smoke.cpp
    window_surface.cpp
    glfw_event_queue.cpp
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
)

target_compile_options(flint_native PRIVATE -Wall -Wextra -fvisibility=hidden)


# LWJGL loads the GLFW platform library by its canonical soname (libglfw.so).
# Keep it separate from Flint's JNI bridge while sharing the EGL/event backend.
add_library(flint_glfw SHARED
    glfw_abi.cpp
)
set_target_properties(flint_glfw PROPERTIES OUTPUT_NAME glfw)
target_include_directories(flint_glfw PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(flint_glfw
    flint_native
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
)
target_compile_options(flint_glfw PRIVATE -Wall -Wextra -fvisibility=hidden)
