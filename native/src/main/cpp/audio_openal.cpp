// audio_openal.cpp
// Flint audio contract: OpenAL Soft (LGPL-2.1+) is loaded dynamically via
// dlopen — never statically linked into flint_native. If libopenal.so is
// absent, all calls route to a silent no-op stub so Minecraft's audio init
// succeeds (with no sound). All glue code original to Flint.

#include "flint_common.h"
#include "audio_openal.h"

#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <mutex>
#include <string>
#include <unordered_map>

namespace flint {

static std::mutex g_mutex;
static void* g_openalHandle = nullptr;
static bool g_loaded = false;
static std::string g_pluginDir;
static std::string g_libraryPath;
static std::string g_error;

// No-op trampolines (stub backend). Return "success" values.
static void* stub_alcCreateContext(void*, const int*) { return (void*)0x1; }
static int   stub_alcMakeContextCurrent(void*) { return 1; }
static void  stub_alcDestroyContext(void*) {}
static void* stub_alcOpenDevice(const char*) { return (void*)0x1; }
static int   stub_alcCloseDevice(void*) { return 1; }
static const char* stub_alcGetString(void*, int) { return "Flint stub audio device"; }
static void  stub_alGenSources(int, unsigned int*) {}
static void  stub_alDeleteSources(int, const unsigned int*) {}
static void  stub_alSourcei(unsigned int, int, int) {}
static void  stub_alSourcef(unsigned int, int, float) {}
static void  stub_alSource3f(unsigned int, int, float, float, float) {}
static void  stub_alSourcePlay(unsigned int) {}
static void  stub_alSourceStop(unsigned int) {}
static void  stub_alSourcePause(unsigned int) {}
static void  stub_alGenBuffers(int, unsigned int*) {}
static void  stub_alDeleteBuffers(int, const unsigned int*) {}
static void  stub_alBufferData(unsigned int, int, const void*, int, int) {}
static int   stub_alGetError() { return 0; } // AL_NO_ERROR
static void  stub_alListener3f(int, float, float, float) {}
static void  stub_alListenerf(int, float) {}
static void  stub_alGetSourcei(unsigned int, int, int* value) { if (value) *value = 0; }

struct StubEntry { const char* name; void* fn; };
static const StubEntry kStubTable[] = {
    { "alcCreateContext", (void*)stub_alcCreateContext },
    { "alcMakeContextCurrent", (void*)stub_alcMakeContextCurrent },
    { "alcDestroyContext", (void*)stub_alcDestroyContext },
    { "alcOpenDevice", (void*)stub_alcOpenDevice },
    { "alcCloseDevice", (void*)stub_alcCloseDevice },
    { "alcGetString", (void*)stub_alcGetString },
    { "alGenSources", (void*)stub_alGenSources },
    { "alDeleteSources", (void*)stub_alDeleteSources },
    { "alSourcei", (void*)stub_alSourcei },
    { "alSourcef", (void*)stub_alSourcef },
    { "alSource3f", (void*)stub_alSource3f },
    { "alSourcePlay", (void*)stub_alSourcePlay },
    { "alSourceStop", (void*)stub_alSourceStop },
    { "alSourcePause", (void*)stub_alSourcePause },
    { "alGenBuffers", (void*)stub_alGenBuffers },
    { "alDeleteBuffers", (void*)stub_alDeleteBuffers },
    { "alBufferData", (void*)stub_alBufferData },
    { "alGetError", (void*)stub_alGetError },
    { "alListener3f", (void*)stub_alListener3f },
    { "alListenerf", (void*)stub_alListenerf },
    { "alGetSourcei", (void*)stub_alGetSourcei },
    { nullptr, nullptr }
};

void audioSetPluginDir(const char* dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dir) g_pluginDir = dir;
}

bool audioInit(const char* libraryPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_loaded) return true;
    g_error.clear();
    void* handle = nullptr;
    if (libraryPath && *libraryPath) {
        handle = dlopen(libraryPath, RTLD_NOW | RTLD_GLOBAL);
        if (handle) g_libraryPath = libraryPath;
    }
    if (!handle && !g_pluginDir.empty()) {
        std::string p = g_pluginDir + "/libopenal.so";
        handle = dlopen(p.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (handle) g_libraryPath = p;
    }
    if (!handle) {
        handle = dlopen("libopenal.so", RTLD_NOW | RTLD_GLOBAL);
        if (handle) g_libraryPath = "libopenal.so (system)";
    }
    if (!handle) {
        g_error = std::string("libopenal.so not found; using silent stub backend: ") +
                  (dlerror() ? dlerror() : "");
        FLINT_LOGW("%s", g_error.c_str());
        g_loaded = false; // stub mode
        return false;
    }
    // Verify core symbols.
    const char* required[] = { "alcOpenDevice", "alcCreateContext", "alcMakeContextCurrent",
                               "alGenSources", "alSourcePlay", "alGetError", nullptr };
    for (int i = 0; required[i]; ++i) {
        if (!dlsym(handle, required[i])) {
            g_error = std::string("libopenal.so missing symbol: ") + required[i];
            FLINT_LOGE("%s", g_error.c_str());
            dlclose(handle);
            return false;
        }
    }
    g_openalHandle = handle;
    g_loaded = true;
    FLINT_LOGI("OpenAL Soft loaded from %s (dynamic, LGPL — not statically linked)", g_libraryPath.c_str());
    return true;
}

bool audioIsAvailable() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_loaded;
}

void audioShutdown() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_openalHandle) {
        // Keep loaded: LWJGL may resolve later. dlclose omitted intentionally.
        g_openalHandle = nullptr;
    }
    g_loaded = false;
}

void audioQuery(AudioReport& report) {
    std::lock_guard<std::mutex> lock(g_mutex);
    report.openalLoaded = g_loaded;
    snprintf(report.libraryPath, sizeof(report.libraryPath), "%s", g_libraryPath.c_str());
    snprintf(report.errorMessage, sizeof(report.errorMessage), "%s", g_error.c_str());
    if (g_loaded && g_openalHandle) {
        auto getStr = reinterpret_cast<const char* (*)(void*, int)>(dlsym(g_openalHandle, "alcGetString"));
        if (getStr) {
            const char* dev = getStr(nullptr, 0x1005); // ALC_DEFAULT_DEVICE_SPECIFIER
            snprintf(report.defaultDevice, sizeof(report.defaultDevice), "%s", dev ? dev : "unknown");
        }
    } else {
        snprintf(report.defaultDevice, sizeof(report.defaultDevice), "%s", "silent-stub");
    }
}

void* audioGetProcAddress(const char* name) {
    if (!name) return nullptr;
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_openalHandle) {
        void* p = dlsym(g_openalHandle, name);
        if (p) return p;
        p = dlsym(RTLD_DEFAULT, name);
        if (p) return p;
    }
    // Fall back to stub trampoline so callers never see NULL.
    for (int i = 0; kStubTable[i].name; ++i) {
        if (strcmp(kStubTable[i].name, name) == 0) return kStubTable[i].fn;
    }
    // Unknown AL function: return generic no-op.
    return (void*)stub_alSourcePlay;
}

} // namespace flint

// ---------------------------------------------------------------------------
// JNI entry points (Kotlin: NativeBridge.nativeAudio*).
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeAudioInit(
    JNIEnv* env, jclass, jstring libraryPath) {
    flint::ScopedUtfChars s(env, libraryPath);
    return flint::audioInit(s.get()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeAudioIsAvailable(JNIEnv*, jclass) {
    return flint::audioIsAvailable() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeAudioQuery(JNIEnv* env, jclass) {
    flint::AudioReport report;
    flint::audioQuery(report);
    char buf[1024];
    snprintf(buf, sizeof(buf), "loaded=%d|path=%s|device=%s|error=%s",
             report.openalLoaded ? 1 : 0, report.libraryPath,
             report.defaultDevice, report.errorMessage);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flintlauncher_android_nativebridge_NativeBridge_nativeAudioGetProcAddress(
    JNIEnv* env, jclass, jstring name) {
    flint::ScopedUtfChars s(env, name);
    return reinterpret_cast<jlong>(flint::audioGetProcAddress(s.get()));
}
