# Flint GL4ES Android backend

Flint fetches upstream [GL4ES](https://github.com/ptitSeb/gl4es) at the pinned
revision `8b1a3da5d2331d0f88b70be25963182d46eed857` and builds it as
`libGL.so` for each ABI in the Android native module.

GL4ES translates desktop OpenGL 1.x/2.x calls to the device's OpenGL ES 2.0
driver. It does not replace Android EGL or the GPU driver. The Android surface
creates the EGL/GLES2 context; GL4ES is loaded with `RTLD_GLOBAL` and
initialized only after that context is current.

## Integration surface

`GL4ESSurfaceView`:
- creates a GLES2 EGL context on its dedicated render thread;
- loads the packaged `libGL.so` globally;
- calls `initialize_gl4es()` after EGL context creation;
- verifies GL4ES `glGetString`, `glClearColor`, `glClear`, and `glGetError`.

This provides a real GL4ES-backed Android render surface. It is not yet a
complete Minecraft window backend: LWJGL 2/3 still need their native window,
context-current, buffer-swap, and input lifecycle bound to this surface before
a Minecraft session can render its own frames.

## Build

A clean native build fetches the exact GL4ES Git revision above, so GitHub
network access is required on the first build. Gradle/CMake dependency caches
can be reused on subsequent builds.

Upstream license: MIT; see `GL4ES-LICENSE`.
