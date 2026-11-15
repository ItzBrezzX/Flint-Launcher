cmake_minimum_required(VERSION 3.22.1)

set(FLINT_LWJGL_VERSION "3.3.3" CACHE INTERNAL "Pinned LWJGL native ABI")
set(FLINT_LWJGL_ROOT "${CMAKE_CURRENT_LIST_DIR}/_work/lwjgl3-${FLINT_LWJGL_VERSION}")
set(FLINT_GLFW_SOURCE_ROOT "${CMAKE_CURRENT_LIST_DIR}/_work/glfw-3.3.8")
execute_process(
    COMMAND bash "${CMAKE_CURRENT_LIST_DIR}/prepare.sh"
    WORKING_DIRECTORY "${CMAKE_CURRENT_LIST_DIR}"
    RESULT_VARIABLE LWJGL_PREPARE_RESULT
)
if(NOT LWJGL_PREPARE_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not prepare LWJGL ${FLINT_LWJGL_VERSION} JNI sources")
endif()

if(CMAKE_ANDROID_NDK)
    set(FLINT_NDK "${CMAKE_ANDROID_NDK}")
elseif(ANDROID_NDK)
    set(FLINT_NDK "${ANDROID_NDK}")
elseif(DEFINED ENV{ANDROID_NDK_HOME})
    set(FLINT_NDK "$ENV{ANDROID_NDK_HOME}")
else()
    message(FATAL_ERROR "Android NDK path is unavailable for LWJGL native build")
endif()

if(ANDROID_ABI)
    set(FLINT_ABI "${ANDROID_ABI}")
else()
    set(FLINT_ABI "${CMAKE_ANDROID_ARCH_ABI}")
endif()
if(NOT FLINT_ABI)
    message(FATAL_ERROR "Android ABI is unavailable for LWJGL native build")
endif()

set(FLINT_ANDROID_API 26)
set(FLINT_LIBFFI_OUT "${CMAKE_CURRENT_BINARY_DIR}/libffi-${FLINT_ABI}")
execute_process(
    COMMAND bash "${CMAKE_CURRENT_LIST_DIR}/build_libffi.sh"
        "${FLINT_NDK}" "${FLINT_ABI}" "${FLINT_ANDROID_API}" "${FLINT_LIBFFI_OUT}"
    WORKING_DIRECTORY "${CMAKE_CURRENT_LIST_DIR}"
    RESULT_VARIABLE LIBFFI_BUILD_RESULT
)
if(NOT LIBFFI_BUILD_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not build Android libffi for ${FLINT_ABI}")
endif()
set(FLINT_LIBFFI "${FLINT_LIBFFI_OUT}/install/lib/libffi.a")
if(NOT EXISTS "${FLINT_LIBFFI}")
    message(FATAL_ERROR "Android libffi archive missing: ${FLINT_LIBFFI}")
endif()

set(LWJGL_CORE "${FLINT_LWJGL_ROOT}/modules/lwjgl/core")
set(LWJGL_GLFW "${FLINT_LWJGL_ROOT}/modules/lwjgl/glfw")
set(LWJGL_OPENGL "${FLINT_LWJGL_ROOT}/modules/lwjgl/opengl")

if(FLINT_ABI STREQUAL "arm64-v8a")
    set(FLINT_LWJGL_ARCH "arm64")
elseif(FLINT_ABI STREQUAL "armeabi-v7a")
    set(FLINT_LWJGL_ARCH "arm32")
elseif(FLINT_ABI STREQUAL "x86")
    set(FLINT_LWJGL_ARCH "x86")
elseif(FLINT_ABI STREQUAL "x86_64")
    set(FLINT_LWJGL_ARCH "x64")
else()
    message(FATAL_ERROR "No LWJGL architecture definition for ${FLINT_ABI}")
endif()

file(GLOB CORE_GENERATED CONFIGURE_DEPENDS
    "${LWJGL_CORE}/src/generated/c/*.c"
    "${LWJGL_CORE}/src/generated/c/linux/*.c"
)
list(FILTER CORE_GENERATED EXCLUDE REGEX "liburing")
file(GLOB CORE_MAIN CONFIGURE_DEPENDS
    "${LWJGL_CORE}/src/main/c/*.c"
)

file(GLOB GLFW_GENERATED CONFIGURE_DEPENDS
    "${LWJGL_GLFW}/src/generated/c/*.c"
)
list(FILTER GLFW_GENERATED EXCLUDE REGEX "GLFWNative")

file(GLOB OPENGL_GENERATED CONFIGURE_DEPENDS
    "${LWJGL_OPENGL}/src/generated/c/*.c"
)
list(FILTER OPENGL_GENERATED EXCLUDE REGEX "(GLX|WGL|CGL|macos|windows)")

if(NOT CORE_GENERATED OR NOT GLFW_GENERATED OR NOT OPENGL_GENERATED)
    message(FATAL_ERROR "LWJGL JNI generation did not produce core, GLFW and OpenGL C sources")
endif()

find_library(FLINT_DL_LIBRARY dl)
find_library(FLINT_LOG_LIBRARY log)
find_library(FLINT_ANDROID_LIBRARY android)
find_library(FLINT_EGL_LIBRARY EGL)
find_library(FLINT_GLES_LIBRARY GLESv2)

add_library(lwjgl SHARED ${CORE_GENERATED} ${CORE_MAIN})
set_target_properties(lwjgl PROPERTIES OUTPUT_NAME lwjgl)
target_include_directories(lwjgl PRIVATE
    "${LWJGL_CORE}/src/main/c"
    "${LWJGL_CORE}/src/main/c/libffi"
    "${LWJGL_CORE}/src/generated/c"
    "${LWJGL_CORE}/src/generated/c/linux"
    "${FLINT_LIBFFI_OUT}/install/include"
)
target_compile_definitions(lwjgl PRIVATE LWJGL_LINUX "LWJGL_${FLINT_LWJGL_ARCH}")
target_compile_options(lwjgl PRIVATE -O3 -fPIC -pthread -DNDEBUG -fvisibility=hidden -Wno-unused-parameter -Wno-deprecated-declarations)
target_link_options(lwjgl PRIVATE -pthread)
target_link_libraries(lwjgl PRIVATE
    "${FLINT_LIBFFI}"
    "${FLINT_DL_LIBRARY}"
    "${FLINT_LOG_LIBRARY}"
)

add_library(lwjgl_glfw SHARED ${GLFW_GENERATED})
set_target_properties(lwjgl_glfw PROPERTIES OUTPUT_NAME lwjgl_glfw)
target_include_directories(lwjgl_glfw PRIVATE
    "${LWJGL_CORE}/src/main/c"
    "${LWJGL_CORE}/src/generated/c"
    "${LWJGL_GLFW}/src/main/c"
    "${LWJGL_GLFW}/src/generated/c"
    "${FLINT_GLFW_SOURCE_ROOT}/include"
)
target_compile_definitions(lwjgl_glfw PRIVATE LWJGL_LINUX "LWJGL_${FLINT_LWJGL_ARCH}")
target_compile_options(lwjgl_glfw PRIVATE -O3 -fPIC -pthread -DNDEBUG -fvisibility=hidden -Wno-unused-parameter -Wno-deprecated-declarations)
target_link_options(lwjgl_glfw PRIVATE -pthread)
target_link_libraries(lwjgl_glfw PRIVATE
    lwjgl
    flint_glfw
    "${FLINT_DL_LIBRARY}"
    "${FLINT_LOG_LIBRARY}"
    "${FLINT_ANDROID_LIBRARY}"
)

add_library(lwjgl_opengl SHARED ${OPENGL_GENERATED})
set_target_properties(lwjgl_opengl PROPERTIES OUTPUT_NAME lwjgl_opengl)
target_include_directories(lwjgl_opengl PRIVATE
    "${LWJGL_CORE}/src/main/c"
    "${LWJGL_CORE}/src/generated/c"
    "${LWJGL_OPENGL}/src/main/c"
    "${LWJGL_OPENGL}/src/generated/c"
)
target_compile_definitions(lwjgl_opengl PRIVATE LWJGL_LINUX "LWJGL_${FLINT_LWJGL_ARCH}")
target_compile_options(lwjgl_opengl PRIVATE -O3 -fPIC -pthread -DNDEBUG -fvisibility=hidden -Wno-unused-parameter -Wno-deprecated-declarations)
target_link_options(lwjgl_opengl PRIVATE -pthread)
target_link_libraries(lwjgl_opengl PRIVATE
    lwjgl
    flint_glfw
    "${FLINT_DL_LIBRARY}"
    "${FLINT_LOG_LIBRARY}"
    "${FLINT_EGL_LIBRARY}"
    "${FLINT_GLES_LIBRARY}"
)
