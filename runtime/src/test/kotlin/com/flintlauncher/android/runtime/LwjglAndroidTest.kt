package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LwjglAndroidTest {
    private fun lib(path: String) = File("/data/games/libraries/$path")

    private val lwjgl3 = listOf(
        lib("org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar"),
        lib("org/lwjgl/lwjgl-glfw/3.3.1/lwjgl-glfw-3.3.1.jar"),
        lib("org/lwjgl/lwjgl-opengl/3.3.1/lwjgl-opengl-3.3.1-natives-linux-arm64.jar"),
    )
    private val other = listOf(
        lib("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar"),
        File("/data/games/versions/1.19/1.19.jar"),
    )
    private val android = listOf(
        File("/files/lwjgl3/lwjgl-opengl.jar"),
        File("/files/lwjgl3/lwjgl.jar"),
        File("/files/lwjgl3/lwjglx-debug.jar"),
    )

    @Test
    fun recognisesLwjgl3AndLwjgl2Entries() {
        assertTrue(lwjgl3.all(LwjglAndroid::isLwjgl3))
        assertFalse(other.any(LwjglAndroid::isLwjgl3))
        assertTrue(LwjglAndroid.isLwjgl2(lib("org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar")))
        assertTrue(LwjglAndroid.isLwjgl2(lib("org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar")))
        assertTrue(LwjglAndroid.isLwjgl2(lib("net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar")))
        assertFalse(LwjglAndroid.isLwjgl2(lwjgl3.first()))
    }

    @Test
    fun replacesDesktopLwjgl3WithAndroidJarsFirstAndKeepsEverythingElse() {
        val result = LwjglAndroid.substitute(lwjgl3 + other, android) as LwjglAndroid.Substitution.Ready
        assertEquals(
            listOf(File("/files/lwjgl3/lwjgl-opengl.jar"), File("/files/lwjgl3/lwjgl.jar")).sortedBy { it.name } + other,
            result.classpath,
        )
        assertFalse(result.classpath.any { it.name.startsWith("lwjglx") }) // compat layer only for LWJGL 2
    }

    @Test
    fun withoutAndroidComponentsAnLwjglVersionIsRefusedWithAnActionableMessage() {
        val result = LwjglAndroid.substitute(lwjgl3 + other, emptyList())
        assertTrue(result is LwjglAndroid.Substitution.Unsupported)
        assertTrue((result as LwjglAndroid.Substitution.Unsupported).reason.contains("LWJGL"))
    }

    @Test
    fun lwjgl2NeedsTheCompatLayerOtherwiseItIsRefusedClearly() {
        val old = listOf(lib("org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar")) + other
        val noCompat = LwjglAndroid.substitute(old, android.filterNot { it.name.startsWith("lwjglx") })
        assertTrue(noCompat is LwjglAndroid.Substitution.Unsupported)

        val withCompat = LwjglAndroid.substitute(old, android) as LwjglAndroid.Substitution.Ready
        assertTrue(withCompat.classpath.any { it.name == "lwjglx-debug.jar" })
        assertFalse(withCompat.classpath.any(LwjglAndroid::isLwjgl2))
    }

    @Test
    fun aClasspathWithoutLwjglIsLeftAlone() {
        val result = LwjglAndroid.substitute(other, android) as LwjglAndroid.Substitution.Ready
        assertEquals(other, result.classpath)
    }

    @Test
    fun jvmArgumentsPointLwjglAtFlintsLibraries() {
        val args = LwjglAndroid.jvmArguments(File("/data/app/lib/arm64"))
        assertTrue("-Dorg.lwjgl.glfw.libname=/data/app/lib/arm64/libglfw.so" in args)
        assertTrue("-Dorg.lwjgl.opengl.libname=/data/app/lib/arm64/libGL.so" in args)
        assertTrue("-Dorg.lwjgl.librarypath=/data/app/lib/arm64" in args)
    }
}
