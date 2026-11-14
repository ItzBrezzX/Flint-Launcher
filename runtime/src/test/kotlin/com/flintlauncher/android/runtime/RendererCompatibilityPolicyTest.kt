package com.flintlauncher.android.runtime

import com.flintlauncher.android.minecraft.version.Library
import com.flintlauncher.android.minecraft.version.VersionJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RendererCompatibilityPolicyTest {

    @Test
    fun minecraft125UsesLegacyLwjgl2AndGl4esPlan() {
        val plan = RendererCompatibilityPolicy.planFor("1.2.5")

        assertNotNull(plan)
        assertEquals(LwjglCompatibilityLine.LWJGL2, plan!!.lwjglLine)
        assertEquals(RendererKind.GL4ES, plan.renderer)
        assertEquals("gl4es", plan.rendererId)
    }

    @Test
    fun releasesBefore113UseLwjgl2() {
        assertEquals(
            LwjglCompatibilityLine.LWJGL2,
            RendererCompatibilityPolicy.planFor("1.12.2")!!.lwjglLine,
        )
    }

    @Test
    fun releasesFrom113UseLwjgl3() {
        assertEquals(
            LwjglCompatibilityLine.LWJGL3,
            RendererCompatibilityPolicy.planFor("1.13")!!.lwjglLine,
        )
        assertEquals(
            LwjglCompatibilityLine.LWJGL3,
            RendererCompatibilityPolicy.planFor("1.21.1")!!.lwjglLine,
        )
        assertEquals(
            LwjglCompatibilityLine.LWJGL3,
            RendererCompatibilityPolicy.planFor("26.1")!!.lwjglLine,
        )
    }

    @Test
    fun alphaAndBetaUseResolvedLegacyLwjglMetadata() {
        val alpha = VersionJson(
            id = "a1.2.6",
            libraries = listOf(Library(name = "org.lwjgl.lwjgl:lwjgl:2.9.0")),
        )
        val beta = VersionJson(
            id = "b1.7.3",
            libraries = listOf(Library(name = "org.lwjgl.lwjgl:lwjgl:2.9.1")),
        )

        assertEquals(LwjglCompatibilityLine.LWJGL2, RendererCompatibilityPolicy.planFor(alpha)!!.lwjglLine)
        assertEquals(LwjglCompatibilityLine.LWJGL2, RendererCompatibilityPolicy.planFor(beta)!!.lwjglLine)
    }

    @Test
    fun snapshotUsesResolvedModernLwjglMetadata() {
        val snapshot = VersionJson(
            id = "23w14a",
            libraries = listOf(Library(name = "org.lwjgl:lwjgl:3.3.1")),
        )

        assertEquals(LwjglCompatibilityLine.LWJGL3, RendererCompatibilityPolicy.planFor(snapshot)!!.lwjglLine)
    }

    @Test
    fun moddedProfileUsesItsResolvedLwjglLibraries() {
        val legacyLoader = VersionJson(
            id = "fabric-loader-legacy",
            libraries = listOf(
                Library(name = "net.fabricmc:fabric-loader:0.14.0"),
                Library(name = "org.lwjgl.lwjgl:lwjgl:2.9.4"),
            ),
        )
        val modernLoader = VersionJson(
            id = "fabric-loader-modern",
            libraries = listOf(
                Library(name = "net.fabricmc:fabric-loader:0.16.0"),
                Library(name = "org.lwjgl:lwjgl:3.3.3"),
            ),
        )

        assertEquals(LwjglCompatibilityLine.LWJGL2, RendererCompatibilityPolicy.planFor(legacyLoader)!!.lwjglLine)
        assertEquals(LwjglCompatibilityLine.LWJGL3, RendererCompatibilityPolicy.planFor(modernLoader)!!.lwjglLine)
    }

    @Test
    fun ambiguousOrUnknownIdentifiersAreNotGuessed() {
        assertNull(RendererCompatibilityPolicy.planFor("23w14a"))
        assertNull(RendererCompatibilityPolicy.planFor("b1.7.3"))
        assertNull(RendererCompatibilityPolicy.planFor("not-a-version"))
    }
}
