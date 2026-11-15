# Android/Bionic builds of the LWJGL 3.3.3 native core and OpenGL binding.
# The upstream generated JNI sources are compiled for the Android NDK and use
# the platform's Bionic libc, libdl and JNI headers; no Linux .so is reused.

include(FetchContent)
include(ExternalProject)

set(FLINT_LWJGL_VERSION "3.3.3" CACHE STRING "LWJGL native/JAR compatibility version")
# Keep the auxiliary libffi cross-compiler aligned with native/build.gradle.kts.
set(FLINT_LWJGL_ANDROID_API "26" CACHE STRING "Android API level used for LWJGL/libffi builds")

FetchContent_Declare(
    flint_lwjgl_source
    GIT_REPOSITORY https://github.com/LWJGL/lwjgl3.git
    GIT_TAG ${FLINT_LWJGL_VERSION}
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(flint_lwjgl_source)
if(NOT flint_lwjgl_source_POPULATED)
    FetchContent_Populate(flint_lwjgl_source)
endif()

FetchContent_Declare(
    flint_libffi_source
    URL https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz
)
FetchContent_GetProperties(flint_libffi_source)
if(NOT flint_libffi_source_POPULATED)
    FetchContent_Populate(flint_libffi_source)
endif()

if(NOT ANDROID OR NOT CMAKE_ANDROID_NDK)
    message(FATAL_ERROR "Flint LWJGL Android natives require the Android NDK CMake toolchain")
endif()

set(_flint_lwjgl_core "${flint_lwjgl_source_SOURCE_DIR}/modules/lwjgl/core")
set(_flint_lwjgl_opengl "${flint_lwjgl_source_SOURCE_DIR}/modules/lwjgl/opengl")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(_flint_ffi_host "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(_flint_ffi_host "armv7a-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86")
    set(_flint_ffi_host "i686-linux-android")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(_flint_ffi_host "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported Android ABI for LWJGL: ${ANDROID_ABI}")
endif()

string(TOLOWER "${CMAKE_HOST_SYSTEM_NAME}" _flint_host_os)
if(CMAKE_HOST_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|AMD64)$")
    set(_flint_host_arch "x86_64")
elseif(CMAKE_HOST_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)$")
    set(_flint_host_arch "aarch64")
else()
    message(FATAL_ERROR "Unsupported host architecture for libffi cross build: ${CMAKE_HOST_SYSTEM_PROCESSOR}")
endif()

set(_flint_llvm_bin "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/${_flint_host_os}-${_flint_host_arch}/bin")
set(_flint_ffi_cc "${_flint_llvm_bin}/${_flint_ffi_host}${FLINT_LWJGL_ANDROID_API}-clang")
set(_flint_ffi_ar "${_flint_llvm_bin}/llvm-ar")
set(_flint_ffi_ranlib "${_flint_llvm_bin}/llvm-ranlib")
if(NOT EXISTS "${_flint_ffi_cc}")
    message(FATAL_ERROR "Android NDK compiler was not found: ${_flint_ffi_cc}")
endif()

find_program(FLINT_HOST_MAKE make REQUIRED)
set(_flint_ffi_build "${CMAKE_CURRENT_BINARY_DIR}/flint-libffi-build")
set(_flint_ffi_archive "${_flint_ffi_build}/.libs/libffi.a")
ExternalProject_Add(flint_lwjgl_libffi
    PREFIX "${CMAKE_CURRENT_BINARY_DIR}/flint-libffi-prefix"
    SOURCE_DIR "${flint_libffi_source_SOURCE_DIR}"
    BINARY_DIR "${_flint_ffi_build}"
    CONFIGURE_COMMAND
        <SOURCE_DIR>/configure
        --host=${_flint_ffi_host}
        --disable-shared
        --enable-static
        --with-sysroot=${CMAKE_SYSROOT}
        CC=${_flint_ffi_cc}
        AR=${_flint_ffi_ar}
        RANLIB=${_flint_ffi_ranlib}
        CFLAGS=-fPIC
    BUILD_COMMAND "${FLINT_HOST_MAKE}" -j2
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${_flint_ffi_archive}"
    LOG_CONFIGURE ON
    LOG_BUILD ON
)

# LWJGL 3.3.3 contains constants for non-Android libffi calling conventions.
# Keep the generated JNI methods available but return 0 when a convention is
# not defined by the Android target ABI. FFI_DEFAULT_ABI remains supplied by
# libffi and is not altered by this patch.
set(_flint_lwjgl_patched_dir "${CMAKE_CURRENT_BINARY_DIR}/flint-lwjgl-patched")
file(MAKE_DIRECTORY "${_flint_lwjgl_patched_dir}")
set(_flint_lwjgl_libffi_generated "${_flint_lwjgl_core}/src/generated/c/org_lwjgl_system_libffi_LibFFI.c")
file(READ "${_flint_lwjgl_libffi_generated}" _flint_lwjgl_libffi_source)
foreach(_flint_ffi_constant
    FFI_WIN64 FFI_GNUW64 FFI_UNIX64 FFI_EFI64 FFI_SYSV
    FFI_STDCALL FFI_THISCALL FFI_FASTCALL FFI_MS_CDECL
    FFI_PASCAL FFI_REGISTER FFI_VFP
)
    string(REPLACE
        "return (jint)${_flint_ffi_constant};"
        "#ifdef ${_flint_ffi_constant}\n    return (jint)${_flint_ffi_constant};\n#else\n    return 0;\n#endif"
        _flint_lwjgl_libffi_source
        "${_flint_lwjgl_libffi_source}"
    )
endforeach()
set(_flint_lwjgl_libffi_patched "${_flint_lwjgl_patched_dir}/org_lwjgl_system_libffi_LibFFI.c")
file(WRITE "${_flint_lwjgl_libffi_patched}" "${_flint_lwjgl_libffi_source}")

file(GLOB_RECURSE _flint_lwjgl_core_generated
    "${_flint_lwjgl_core}/src/generated/c/*.c"
)
list(FILTER _flint_lwjgl_core_generated EXCLUDE REGEX ".*/(windows|macos|freebsd)/.*")
list(FILTER _flint_lwjgl_core_generated EXCLUDE REGEX ".*/liburing/.*")
list(FILTER _flint_lwjgl_core_generated EXCLUDE REGEX ".*UIO.*")
list(FILTER _flint_lwjgl_core_generated EXCLUDE REGEX ".*/org_lwjgl_system_libffi_LibFFI.c$")
list(APPEND _flint_lwjgl_core_generated "${_flint_lwjgl_libffi_patched}")

file(GLOB_RECURSE _flint_lwjgl_core_main
    "${_flint_lwjgl_core}/src/main/c/*.c"
)
list(FILTER _flint_lwjgl_core_main EXCLUDE REGEX ".*/liburing/.*")

add_library(flint_lwjgl SHARED
    ${_flint_lwjgl_core_generated}
    ${_flint_lwjgl_core_main}
)
set_target_properties(flint_lwjgl PROPERTIES
    OUTPUT_NAME "lwjgl"
    PREFIX "lib"
    SUFFIX ".so"
    LIBRARY_OUTPUT_DIRECTORY "${FLINT_LIBRARY_OUTPUT_DIRECTORY}"
)
target_include_directories(flint_lwjgl PRIVATE
    "${_flint_lwjgl_core}/src/main/c"
    "${_flint_lwjgl_core}/src/main/c/linux"
    "${_flint_lwjgl_core}/src/main/c/libffi"
    "${_flint_lwjgl_core}/src/generated/c/linux"
    "${_flint_ffi_build}"
    "${_flint_ffi_build}/include"
    "${flint_libffi_source_SOURCE_DIR}/include"
)
target_compile_definitions(flint_lwjgl PRIVATE LWJGL_LINUX)
target_link_libraries(flint_lwjgl PRIVATE
    "${_flint_ffi_archive}"
    ${log-lib}
    ${dl-lib}
)
add_dependencies(flint_lwjgl flint_lwjgl_libffi)

file(GLOB _flint_lwjgl_opengl_generated
    "${_flint_lwjgl_opengl}/src/generated/c/*.c"
)
list(FILTER _flint_lwjgl_opengl_generated EXCLUDE REGEX ".*(WGL|GLX|CGL).*")

add_library(flint_lwjgl_opengl SHARED ${_flint_lwjgl_opengl_generated})
set_target_properties(flint_lwjgl_opengl PROPERTIES
    OUTPUT_NAME "lwjgl_opengl"
    PREFIX "lib"
    SUFFIX ".so"
    LIBRARY_OUTPUT_DIRECTORY "${FLINT_LIBRARY_OUTPUT_DIRECTORY}"
)
target_include_directories(flint_lwjgl_opengl PRIVATE
    "${_flint_lwjgl_core}/src/main/c"
    "${_flint_lwjgl_core}/src/main/c/linux"
    "${_flint_lwjgl_opengl}/src/main/c"
)
target_compile_definitions(flint_lwjgl_opengl PRIVATE LWJGL_LINUX)
target_link_libraries(flint_lwjgl_opengl PRIVATE
    flint_lwjgl
    ${log-lib}
    ${dl-lib}
)

message(STATUS "Flint Android LWJGL ${FLINT_LWJGL_VERSION}: ${ANDROID_ABI}")
