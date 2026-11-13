cmake_minimum_required(VERSION 3.22.1)
project(flint_native)

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
)

target_compile_options(flint_native PRIVATE -Wall -Wextra -fvisibility=hidden)
