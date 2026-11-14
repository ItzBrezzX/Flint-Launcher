package com.flintlauncher.android.runtime

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
    }

    @Test
    fun snapshotsAndUnknownVersionFormatsAreNotGuessed() {
        assertNull(RendererCompatibilityPolicy.planFor("23w14a"))
        assertNull(RendererCompatibilityPolicy.planFor("b1.7.3"))
        assertNull(RendererCompatibilityPolicy.planFor("not-a-version"))
    }
}
