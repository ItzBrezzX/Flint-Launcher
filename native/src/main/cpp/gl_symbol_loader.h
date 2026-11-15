// gl_symbol_loader.h
// Flint GL symbol loader / renderer backend selector.
//
// Two backends:
//   1. GL4ES_PLUGIN: dlopen("libGL.so") from the plugin dir (MIT, optional).
//      Desktop GL function pointers come from the plugin; it translates to
//      GLES internally. Requires a current EGL context at init time.
//   2. SYSTEM_GLES: direct dispatch to libGLESv2.so / eglGetProcAddress.
//      Only GL ES 2.0+ symbols are available; desktop GL entry points that
//      have no ES equivalent return NULL (caller must handle / compat-test).
//
// Renderer selection: try requested backend; if it fails to load/init, fall
// back through the chain (GL4ES → SYSTEM_GLES → none). Context recovery
// re-creates the EGL context and re-initializes the backend.
//
// All code original to Flint. GL4ES and OpenAL are third-party plugins loaded
// at runtime; see docs/progress-shard-native.md for licence attribution.
#pragma once

#include <cstdint>

namespace flint {

enum class RendererBackend {
    None = 0,
    SystemGles = 1,  // direct GLESv2 dispatch
    Gl4esPlugin = 2, // dlopen libGL.so (GL4ES, MIT)
};

struct GlCompatReport {
    RendererBackend backend = RendererBackend::None;
    char renderer[256] = {0};
    char version[256] = {0};
    char glslVersion[256] = {0};
    char vendor[256] = {0};
    char extensions[2048] = {0};
    int maxTextureSize = 0;
    int maxVertexAttribs = 0;
    int maxTextureImageUnits = 0;
    int redBits = 8, greenBits = 8, blueBits = 8, alphaBits = 8;
    int depthBits = 16, stencilBits = 0;
    int samples = 0;
    bool srgbCapable = false;
    bool initialized = false;
    char errorMessage[256] = {0};
};

// Initialize the GL backend. Call with a current EGL context on the render thread.
// - requested: preferred backend
// - pluginPath: absolute path to libGL.so (GL4ES) or libopenal.so; may be null for SystemGles.
// Returns the backend actually selected (None if everything failed).
RendererBackend glInit(RendererBackend requested, const char* pluginPath);

// Shutdown / unload plugin. Call with current context.
void glShutdown();

// Get a GL function pointer by name (desktop GL name, e.g. "glCreateShader").
// Dispatch order: GL4ES plugin dlsym → eglGetProcAddress → libGLESv2 dlsym → NULL.
void* glGetProcAddress(const char* name);

// Query compatibility / capabilities. Requires current context. Fills report.
void glQueryCompat(GlCompatReport& report);

// Attempt context recovery: tear down and re-create EGL context, re-init backend.
// Returns true on success. Call on render thread after a context loss.
bool glRecoverContext();

// Current backend.
RendererBackend glCurrentBackend();

// Last error string (empty if none).
const char* glLastError();

// Set the plugin search directory (used for dlopen fallback paths).
void glSetPluginDir(const char* dir);

// Add a directory to the native library search path (LD_LIBRARY_PATH equivalent
// for our own dlopen calls). Returns true if set.
bool addNativeLibraryPath(const char* dir);

} // namespace flint
