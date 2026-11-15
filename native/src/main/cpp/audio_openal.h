// audio_openal.h
// Flint audio contract: OpenAL Soft is loaded dynamically via dlopen
// (LGPL-2.1+ — dynamic linking only, never statically linked into flint_native).
// If libopenal.so is absent, all calls route to a silent stub backend that
// reports success with no-op behaviour, so Minecraft's audio init doesn't crash.
//
// Only the high-traffic subset of the AL/ALC API Minecraft uses is resolved.
// All glue code original to Flint.
#pragma once

#include <cstdint>

namespace flint {

struct AudioReport {
    bool openalLoaded = false;
    char libraryPath[512] = {0};
    char defaultDevice[256] = {0};
    char alVersion[64] = {0};
    char errorMessage[256] = {0};
};

// Try to dlopen libopenal.so from the given path (or plugin dir if null).
// Returns true if loaded and core symbols resolved. Safe to call multiple times.
bool audioInit(const char* libraryPath);

// True if OpenAL Soft is loaded and functional.
bool audioIsAvailable();

// Close device + unload.
void audioShutdown();

// Fill a report.
void audioQuery(AudioReport& report);

// Set plugin search dir.
void audioSetPluginDir(const char* dir);

// --- Resolved AL/ALC function pointer types (subset). Minecraft/LWJGL calls
// these through our shim; if OpenAL is absent they no-op successfully. ---
typedef void* (*ALCCreateContext)(void* device, const int* attrlist);
typedef int   (*ALCMakeContextCurrent)(void* context);
typedef void  (*ALCDestroyContext)(void* context);
typedef void* (*ALCOpenDevice)(const char* devicename);
typedef int   (*ALCCloseDevice)(void* device);
typedef const char* (*ALCGetString)(void* device, int param);
typedef void  (*ALGenSources)(int n, unsigned int* sources);
typedef void  (*ALDeleteSources)(int n, const unsigned int* sources);
typedef void  (*ALSourcei)(unsigned int sid, int param, int value);
typedef void  (*ALSourcef)(unsigned int sid, int param, float value);
typedef void  (*ALSource3f)(unsigned int sid, int param, float v1, float v2, float v3);
typedef void  (*ALSourcePlay)(unsigned int sid);
typedef void  (*ALSourceStop)(unsigned int sid);
typedef void  (*ALSourcePause)(unsigned int sid);
typedef void  (*ALGenBuffers)(int n, unsigned int* buffers);
typedef void  (*ALDeleteBuffers)(int n, const unsigned int* buffers);
typedef void  (*ALBufferData)(unsigned int bid, int format, const void* data, int size, int freq);
typedef int   (*ALGetError)(void);
typedef void  (*ALListener3f)(int param, float v1, float v2, float v3);
typedef void  (*ALListenerf)(int param, float value);
typedef void  (*ALGetSourcei)(unsigned int sid, int param, int* value);

// Access resolved pointers (nullptr if not loaded). The stub backend returns
// non-null no-op trampolines so callers never see NULL.
void* audioGetProcAddress(const char* name);

} // namespace flint
