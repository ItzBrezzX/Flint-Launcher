package com.flintlauncher.android.installer

import com.flintlauncher.android.minecraft.version.VersionJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class LegacyAssetMaterializerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun virtualIndexCopiesObjectsToLegacyVirtualTreeAndIsIdempotent() {
        val layout = GameLayout(tmp.root)
        val bytes = "legacy sound file".toByteArray()
        val hash = sha1(bytes)
        layout.assetObject(hash).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val index = VersionJsonParser.parseAssetIndex(
            """{"virtual":true,"objects":{"sound/mob/cow/hurt.ogg":{"hash":"$hash","size":${bytes.size}}}}""",
        )

        val first = LegacyAssetMaterializer.materialize(index, layout)
        val second = LegacyAssetMaterializer.materialize(index, layout)

        val expected = File(tmp.root, "assets/virtual/legacy/sound/mob/cow/hurt.ogg")
        assertEquals(listOf(expected.canonicalFile), first.map { it.canonicalFile })
        assertTrue(expected.readBytes().contentEquals(bytes))
        assertTrue(second.isEmpty())
    }

    @Test
    fun mapToResourcesCopiesNamedAssetsIntoResourcesDirectory() {
        val layout = GameLayout(tmp.root)
        val bytes = "old resource".toByteArray()
        val hash = sha1(bytes)
        layout.assetObject(hash).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val index = VersionJsonParser.parseAssetIndex(
            """{"map_to_resources":true,"objects":{"font/default.png":{"hash":"$hash","size":${bytes.size}}}}""",
        )

        LegacyAssetMaterializer.materialize(index, layout)

        val expected = File(tmp.root, "resources/font/default.png")
        assertTrue(expected.readBytes().contentEquals(bytes))
    }

    @Test
    fun pathTraversalInAssetNamesIsRejected() {
        val layout = GameLayout(tmp.root)
        val bytes = byteArrayOf(1, 2, 3)
        val hash = sha1(bytes)
        layout.assetObject(hash).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val index = VersionJsonParser.parseAssetIndex(
            """{"virtual":true,"objects":{"../outside.bin":{"hash":"$hash","size":3}}}""",
        )

        try {
            LegacyAssetMaterializer.materialize(index, layout)
            throw AssertionError("Expected unsafe path rejection")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("outside") || e.message!!.contains("path"))
        }
        assertTrue(!File(tmp.root.parentFile, "outside.bin").exists())
    }
}
