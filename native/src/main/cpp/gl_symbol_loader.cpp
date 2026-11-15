// gl_symbol_loader.cpp
// Flint GL symbol loader / renderer backend selector.
//
// Backends:
//   Gl4esPlugin : dlopen("libGL.so") — GL4ES (MIT), desktop GL → GLES translation.
//                 Requires a current EGL context at init (initialize_gl4es()).
//   SystemGles  : direct dispatch to libGLESv2.so / eglGetProcAddress. Only
//                 GL ES symbols are available; desktop-only entry points return NULL.
//
// Fallback chain: requested → GL4ES (if plugin present & inits) → SystemGles → None.
// All glue code original to Flint. GL4ES is a third-party runtime plugin.

#include "flint_common.h"
#include "gl_symbol_loader.h"
#include "audio_openal.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <mutex>
#include <string>
#include <vector>
#include <unordered_map>

namespace flint {

static std::mutex g_mutex;
static RendererBackend g_backend = RendererBackend::None;
static void* g_gl4esHandle = nullptr;
static void* g_gles2Handle = nullptr;
static std::string g_pluginDir;
static std::vector<std::string> g_libraryPaths;
static std::string g_lastError;

// Desktop GL → GLES function name aliases. GLES uses the "f" suffix for
// fixed-point/float variants of some functions; LWJGL asks for the desktop name.
struct NameAlias { const char* desktop; const char* gles; };
static const NameAlias kNameAliases[] = {
    { "glDepthRange", "glDepthRangef" },
    { "glClearDepth", "glClearDepthf" },
    { "glFrustum", nullptr },       // removed in GLES2/3 core; GL4ES provides it
    { "glOrtho", nullptr },
    { "glPushMatrix", nullptr },
    { "glPopMatrix", nullptr },
    { "glMatrixMode", nullptr },
    { "glLoadIdentity", nullptr },
    { "glTranslatef", nullptr },
    { "glRotatef", nullptr },
    { "glScalef", nullptr },
    { "glMultMatrixf", nullptr },
    { "glLoadMatrixf", nullptr },
    { "glVertexPointer", nullptr },
    { "glColorPointer", nullptr },
    { "glTexCoordPointer", nullptr },
    { "glNormalPointer", nullptr },
    { "glEnableClientState", nullptr },
    { "glDisableClientState", nullptr },
    { "glDrawArrays", "glDrawArrays" },
    { "glDrawElements", "glDrawElements" },
    { "glFogi", nullptr },
    { "glFogf", nullptr },
    { "glFogfv", nullptr },
    { "glLightfv", nullptr },
    { "glLightModelfv", nullptr },
    { "glMaterialfv", nullptr },
    { "glColor4f", nullptr },
    { "glBegin", nullptr },
    { "glEnd", nullptr },
    { "glVertex2f", nullptr },
    { "glVertex3f", nullptr },
    { "glTexEnvf", nullptr },
    { "glTexEnvi", nullptr },
    { "glAlphaFunc", nullptr },
    { "glBlendFunc", "glBlendFunc" },
    { "glShadeModel", nullptr },
    { "glPolygonMode", nullptr },
    { "glLineWidth", "glLineWidth" },
    { "glPointSize", "glPointSize" },
    { "glHint", "glHint" },
    { "glClipPlanef", nullptr },
    { "glGetString", "glGetString" },
    { "glGetError", "glGetError" },
    { nullptr, nullptr }
};

static const char* aliasGlesName(const char* desktop) {
    for (int i = 0; kNameAliases[i].desktop; ++i) {
        if (strcmp(kNameAliases[i].desktop, desktop) == 0) {
            return kNameAliases[i].gles; // may be null (no GLES equivalent)
        }
    }
    return desktop; // same name in both APIs
}

static void* dlsymIn(void* handle, const char* name) {
    if (!handle || !name) return nullptr;
    return dlsym(handle, name);
}

void glSetPluginDir(const char* dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dir) g_pluginDir = dir;
}

bool addNativeLibraryPath(const char* dir) {
    if (!dir || !*dir) return false;
    std::lock_guard<std::mutex> lock(g_mutex);
    for (const auto& p : g_libraryPaths) {
        if (p == dir) return true;
    }
    g_libraryPaths.emplace_back(dir);
    // Best-effort: update LD_LIBRARY_PATH env for child dlopen lookups.
    // Android's linker ignores LD_LIBRARY_PATH for targetSdk>=24, but our own
    // dlopen calls use absolute paths constructed from these dirs.
    const char* existing = getenv("LD_LIBRARY_PATH");
    std::string updated = dir;
    if (existing && *existing) {
        updated += ":";
        updated += existing;
    }
    setenv("LD_LIBRARY_PATH", updated.c_str(), 1);
    return true;
}

static void* dlopenPlugin(const char* name) {
    // Try absolute plugin dir first, then registered library paths, then bare name.
    if (!g_pluginDir.empty()) {
        std::string path = g_pluginDir + "/" + name;
        void* h = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (h) { FLINT_LOGI("dlopen %s ok from plugin dir", name); return h; }
    }
    for (const auto& dir : g_libraryPaths) {
        std::string path = dir + "/" + name;
        void* h = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (h) { FLINT_LOGI("dlopen %s ok from %s", name, dir.c_str()); return h; }
    }
    void* h = dlopen(name, RTLD_NOW | RTLD_GLOBAL);
    if (h) FLINT_LOGI("dlopen %s ok from system search", name);
    return h;
}

static bool initGl4es(const char* pluginPath) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        g_lastError = "GL4ES requires a current EGL context at init";
        FLINT_LOGE("%s", g_lastError.c_str());
        return false;
    }
    if (!g_gl4esHandle) {
        if (pluginPath && *pluginPath) {
            g_gl4esHandle = dlopen(pluginPath, RTLD_NOW | RTLD_GLOBAL);
            if (!g_gl4esHandle) {
                g_lastError = std::string("dlopen libGL.so failed: ") + dlerror();
                FLINT_LOGE("%s", g_lastError.c_str());
                return false;
            }
        } else {
            g_gl4esHandle = dlopenPlugin("libGL.so");
            if (!g_gl4esHandle) {
                g_lastError = std::string("libGL.so plugin not found: ") + (dlerror() ? dlerror() : "");
                return false;
            }
        }
    }
    auto initialize = reinterpret_cast<void (*)(void)>(dlsym(g_gl4esHandle, "initialize_gl4es"));
    if (!initialize) {
        g_lastError = "libGL.so does not export initialize_gl4es (not GL4ES?)";
        return false;
    }
    initialize();
    auto getString = reinterpret_cast<const GLubyte* (*)(GLenum)>(dlsym(g_gl4esHandle, "glGetString"));
    if (!getString) {
        g_lastError = "libGL.so missing glGetString";
        return false;
    }
    const GLubyte* ver = getString(GL_VERSION);
    FLINT_LOGI("GL4ES initialized: %s", ver ? (const char*)ver : "unknown");
    return true;
}

static bool initSystemGles() {
    // libGLESv2 is linked into flint_native; ensure handle is non-null for dlsym.
    if (!g_gles2Handle) {
        g_gles2Handle = dlopen("libGLESv2.so", RTLD_NOW | RTLD_GLOBAL);
        if (!g_gles2Handle) {
            // Already linked; use RTLD_DEFAULT as fallback.
            g_gles2Handle = RTLD_DEFAULT;
        }
    }
    return eglGetCurrentContext() != EGL_NO_CONTEXT || true; // symbols resolvable without context
}

RendererBackend glInit(RendererBackend requested, const char* pluginPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_lastError.clear();
    if (requested == RendererBackend::Gl4esPlugin || requested == RendererBackend::None) {
        if (initGl4es(pluginPath)) {
            g_backend = RendererBackend::Gl4esPlugin;
            return g_backend;
        }
        FLINT_LOGW("GL4ES init failed, falling back to SystemGLES: %s", g_lastError.c_str());
    }
    if (initSystemGles()) {
        g_backend = RendererBackend::SystemGles;
        return g_backend;
    }
    g_backend = RendererBackend::None;
    g_lastError = "No GL backend available";
    return g_backend;
}

void glShutdown() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_gl4esHandle) {
        // Keep handle loaded: LWJGL may dlsym(RTLD_DEFAULT, ...) later.
        // dlclose(g_gl4esHandle);
        g_gl4esHandle = nullptr;
    }
    g_backend = RendererBackend::None;
}

void* glGetProcAddress(const char* name) {
    if (!name) return nullptr;
    std::lock_guard<std::mutex> lock(g_mutex);
    // 1. GL4ES plugin (desktop GL names).
    if (g_backend == RendererBackend::Gl4esPlugin && g_gl4esHandle) {
        void* p = dlsym(g_gl4esHandle, name);
        if (p) return p;
        // Also try global (GL4ES exports with default visibility).
        p = dlsym(RTLD_DEFAULT, name);
        if (p) return p;
    }
    // 2. eglGetProcAddress (GLES extension / core functions).
    const char* glesName = aliasGlesName(name);
    if (glesName) {
        void* p = eglGetProcAddress(glesName);
        if (p) return p;
        // 3. Direct dlsym in libGLESv2 (some core functions not returned by eglGetProcAddress).
        if (g_gles2Handle) {
            p = dlsym(g_gles2Handle, glesName);
            if (p) return p;
        }
        p = dlsym(RTLD_DEFAULT, glesName);
        if (p) return p;
    }
    // 4. If desktop name differs and has no GLES equivalent, still try global.
    if (glesName != name) {
        void* p = dlsym(RTLD_DEFAULT, name);
        if (p) return p;
    }
    return nullptr;
}

static void copyStr(char* dst, size_t cap, const char* src) {
    if (!src) src = "unknown";
    snprintf(dst, cap, "%s", src);
    for (char* p = dst; *p; ++p) {
        if (*p == '|' || *p == '\n') *p = ' ';
    }
}

void glQueryCompat(GlCompatReport& report) {
    std::lock_guard<std::mutex> lock(g_mutex);
    memset(&report, 0, sizeof(report));
    report.backend = g_backend;
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        snprintf(report.errorMessage, sizeof(report.errorMessage), "no current EGL context");
        return;
    }
    report.initialized = true;
    copyStr(report.vendor, sizeof(report.vendor), (const char*)glGetString(GL_VENDOR));
    copyStr(report.renderer, sizeof(report.renderer), (const char*)glGetString(GL_RENDERER));
    copyStr(report.version, sizeof(report.version), (const char*)glGetString(GL_VERSION));
    copyStr(report.extensions, sizeof(report.extensions), (const char*)glGetString(GL_EXTENSIONS));
    // GLSL version string is ES-specific; query if available.
    const char* glsl = (const char*)glGetString(GL_SHADING_LANGUAGE_VERSION);
    copyStr(report.glslVersion, sizeof(report.glslVersion), glsl);
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &report.maxTextureSize);
    glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &report.maxVertexAttribs);
    glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, &report.maxTextureImageUnits);
}

bool glRecoverContext() {
    std::lock_guard<std::mutex> lock(g_mutex);
    // EGL context ownership lives in glfw_stub (the window). The loader's part
    // of recovery is re-initializing the GL4ES state machine after the window
    // layer re-creates the EGL context. If GL4ES is active, re-run its init.
    if (g_backend == RendererBackend::Gl4esPlugin && g_gl4esHandle) {
        auto initialize = reinterpret_cast<void (*)(void)>(dlsym(g_gl4esHandle, "initialize_gl4es"));
        if (initialize) {
            if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
                g_lastError = "recover: no current context; window layer must re-attach EGL first";
                return false;
            }
            initialize();
            return true;
        }
    }
    // SystemGLES: nothing to reload; context recovery is EGL-only (window layer).
    return true;
}

RendererBackend glCurrentBackend() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_backend;
}

const char* glLastError() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_lastError.c_str();
}

} // namespace flint

// ---------------------------------------------------------------------------
// JNI entry points (Kotlin: NativeBridge.nativeGl*).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlInit(
    JNIEnv* env, jclass, jint requested, jstring pluginPath) {
    flint::ScopedUtfChars path(env, pluginPath);
    flint::RendererBackend req = static_cast<flint::RendererBackend>(requested);
    flint::RendererBackend got = flint::glInit(req, path.get());
    return static_cast<jint>(got);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlGetProcAddress(
    JNIEnv* env, jclass, jstring name) {
    flint::ScopedUtfChars s(env, name);
    void* p = flint::glGetProcAddress(s.get());
    return reinterpret_cast<jlong>(p);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlQueryCompat(JNIEnv* env, jclass) {
    flint::GlCompatReport report;
    flint::glQueryCompat(report);
    char buf[4096];
    snprintf(buf, sizeof(buf),
        "backend=%d|renderer=%s|version=%s|glsl=%s|vendor=%s|maxTex=%d|maxAttribs=%d|maxTexUnits=%d|error=%s",
        (int)report.backend, report.renderer, report.version, report.glslVersion,
        report.vendor, report.maxTextureSize, report.maxVertexAttribs,
        report.maxTextureImageUnits, report.errorMessage);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlRecoverContext(JNIEnv*, jclass) {
    return flint::glRecoverContext() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlCurrentBackend(JNIEnv*, jclass) {
    return static_cast<jint>(flint::glCurrentBackend());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeGlLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(flint::glLastError());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeAddNativeLibraryPath(
    JNIEnv* env, jclass, jstring path) {
    flint::ScopedUtfChars s(env, path);
    return flint::addNativeLibraryPath(s.get()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeSetPluginDir(
    JNIEnv* env, jclass, jstring path) {
    flint::ScopedUtfChars s(env, path);
    flint::glSetPluginDir(s.get());
    flint::audioSetPluginDir(s.get());
}
