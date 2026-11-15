package org.lwjgl.openal

import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Flint LWJGL3-compat shim for org.lwjgl.openal.AL (OpenAL function provider).
 *
 * OpenAL Soft (LGPL-2.1+) is loaded dynamically via dlopen — never statically
 * linked into flint_native. If libopenal.so is absent, nativeAudioGetProcAddress
 * returns no-op trampolines so Minecraft's audio init succeeds silently.
 *
 * Status: function-provider wiring implemented; per-function AL10/AL11 binding
 * classes come from the upstream LWJGL openal jar (pure Java). Audio output is
 * only produced when a real libopenal.so plugin is installed.
 */
object AL {
    @JvmStatic
    fun create(): Boolean =
        NativeBridge.isNativeLoaded && runCatching {
            NativeBridge.nativeAudioInit(null)
        }.getOrDefault(false)

    @JvmStatic
    fun isCreated(): Boolean =
        NativeBridge.isNativeLoaded && NativeBridge.nativeAudioIsAvailable()

    @JvmStatic
    fun getFunctionProvider(): (String) -> Long = { name ->
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeAudioGetProcAddress(name) else 0L
    }

    @JvmStatic
    fun destroy() { /* no-op; native keeps library loaded */ }
}
