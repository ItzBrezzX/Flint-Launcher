package org.lwjgl.opengl

import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Flint LWJGL3-compat shim for org.lwjgl.opengl.GL (function provider).
 *
 * LWJGL3's GL.createCapabilities() resolves GL function pointers through a
 * FunctionProvider. Our provider delegates to Flint's native GL symbol loader
 * (nativeGlGetProcAddress), which dispatches to the GL4ES plugin or system
 * GLESv2. The per-function GL11/GL12/.../GL30 binding classes are NOT shimmed
 * in Spike 0 — they are expected to come from the upstream LWJGL opengl jar
 * (which is pure Java + generated JNI stubs that call through the function
 * provider). Only the provider entry point is replaced here.
 *
 * Status: API-only wiring; full capability creation is delegated to upstream
 * LWJGL classes. Documented in docs/progress-shard-native.md.
 */
object GL {
    /** Function provider entry point used by LWJGL's GL.createCapabilities. */
    @JvmStatic
    fun getFunctionProvider(): (String) -> Long = { name ->
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlGetProcAddress(name) else 0L
    }

    /** Convenience: resolve one GL function pointer. */
    @JvmStatic
    fun getProcAddress(name: String): Long =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlGetProcAddress(name) else 0L

    @JvmStatic
    fun createCapabilities(): Boolean =
        NativeBridge.isNativeLoaded && NativeBridge.nativeGlCurrentBackend() != 0

    @JvmStatic
    fun getCapabilities(): Any? = null // delegated to upstream LWJGL
}
