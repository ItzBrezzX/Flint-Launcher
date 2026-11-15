package com.flintlauncher.android.minecraft.version

import com.flintlauncher.android.minecraft.manifest.VersionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionEraTest {
    @Test
    fun classifiesReleaseErasByMinorVersion() {
        assertEquals(VersionEra.RELEASE_1_0_TO_1_12, VersionEra.classify("1.12.2"))
        assertEquals(VersionEra.RELEASE_1_0_TO_1_12, VersionEra.classify("1.7.10"))
        assertEquals(VersionEra.RELEASE_1_0_TO_1_12, VersionEra.classify("1.0"))
        assertEquals(VersionEra.RELEASE_1_13_TO_1_16, VersionEra.classify("1.16.5"))
        assertEquals(VersionEra.RELEASE_1_13_TO_1_16, VersionEra.classify("1.13"))
        assertEquals(VersionEra.RELEASE_1_17_TO_1_20, VersionEra.classify("1.20.4"))
        assertEquals(VersionEra.RELEASE_1_17_TO_1_20, VersionEra.classify("1.17.1"))
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify("1.21.1"))
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify("1.21"))
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify("1.20.6"))
    }

    @Test
    fun classifiesLegacyErasByIdAndType() {
        assertEquals(VersionEra.ALPHA, VersionEra.classify("a1.2.6", VersionType.OLD_ALPHA))
        assertEquals(VersionEra.ALPHA, VersionEra.classify("anything", VersionType.OLD_ALPHA))
        assertEquals(VersionEra.BETA, VersionEra.classify("b1.7.3", VersionType.OLD_BETA))
        assertEquals(VersionEra.BETA, VersionEra.classify("anything", VersionType.OLD_BETA))
        assertEquals(VersionEra.CLASSIC, VersionEra.classify("rd-132211"))
        assertEquals(VersionEra.INDEV, VersionEra.classify("in-20100223"))
        assertEquals(VersionEra.INFDEV, VersionEra.classify("inf-20100618"))
    }

    @Test
    fun classifiesSnapshotsAndExperimental() {
        assertEquals(VersionEra.SNAPSHOT, VersionEra.classify("24w14a"))
        assertEquals(VersionEra.SNAPSHOT, VersionEra.classify("13w39b"))
        assertEquals(VersionEra.SNAPSHOT, VersionEra.classify("1.21-pre1"))
        assertEquals(VersionEra.SNAPSHOT, VersionEra.classify("1.20.5-rc3"))
        assertEquals(VersionEra.SNAPSHOT, VersionEra.classify("anything", VersionType.SNAPSHOT))
        assertEquals(VersionEra.EXPERIMENTAL, VersionEra.classify("1.21_experimental-snapshot-1"))
        assertEquals(VersionEra.EXPERIMENTAL, VersionEra.classify("update_1_21-experimental"))
    }

    @Test
    fun extractsEmbeddedVanillaVersionFromLoaderIds() {
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify("fabric-loader-0.15.7-1.21"))
        assertEquals(VersionEra.RELEASE_1_0_TO_1_12, VersionEra.classify("1.12.2-forge-14.23.5.2860"))
        assertEquals(VersionEra.RELEASE_1_17_TO_1_20, VersionEra.classify("quilt-loader-0.23.0-1.19.2"))
    }

    @Test
    fun eraTraitsReflectKnownCutovers() {
        // LWJGL 3 cutover at 1.13
        assertEquals(2, VersionEra.RELEASE_1_0_TO_1_12.lwjglMajor)
        assertEquals(3, VersionEra.RELEASE_1_13_TO_1_16.lwjglMajor)
        assertTrue(VersionEra.RELEASE_1_0_TO_1_12.usesSeparateNativeJars)
        assertFalse(VersionEra.RELEASE_1_13_TO_1_16.usesSeparateNativeJars)

        // Arguments format cutover at 1.13
        assertEquals(ArgsFormat.LEGACY_STRING, VersionEra.RELEASE_1_0_TO_1_12.argsFormat)
        assertEquals(ArgsFormat.MODERN_OBJECT, VersionEra.RELEASE_1_13_TO_1_16.argsFormat)

        // Java fallback expectations
        assertEquals(8, VersionEra.RELEASE_1_13_TO_1_16.fallbackJavaMajor)
        assertEquals(17, VersionEra.RELEASE_1_17_TO_1_20.fallbackJavaMajor)
        assertEquals(21, VersionEra.RELEASE_1_21.fallbackJavaMajor)

        // Classic/Indev/Infdev are not in the modern Mojang manifest
        assertFalse(VersionEra.CLASSIC.inMojangManifest)
        assertFalse(VersionEra.INDEV.inMojangManifest)
        assertFalse(VersionEra.INFDEV.inMojangManifest)
        assertTrue(VersionEra.RELEASE_1_21.inMojangManifest)

        // Assets layout: legacy eras use resources/ or virtual; modern uses content-addressed
        assertEquals(AssetsLayout.RESOURCES_DIR, VersionEra.BETA.assetsLayout)
        assertEquals(AssetsLayout.CONTENT_ADDRESSED, VersionEra.RELEASE_1_0_TO_1_12.assetsLayout)
    }

    @Test
    fun unknownIdsNeverThrowAndFallBackToNewestEra() {
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify("some-future-2.0"))
        assertEquals(VersionEra.RELEASE_1_21, VersionEra.classify(""))
    }
}
