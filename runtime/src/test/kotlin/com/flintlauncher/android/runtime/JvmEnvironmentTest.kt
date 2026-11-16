package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JvmEnvironmentTest {
    private fun dir(parent: File, name: String) = File(parent, name).also { it.mkdirs() }

    @Test
    fun coversJreGameAndAppNativeDirectoriesInOrder() {
        val root = Files.createTempDirectory("flint-env").toFile()
        val home = dir(root, "jre")
        dir(home, "lib")
        dir(home, "lib/jli")
        dir(home, "lib/server")
        val natives = dir(root, "natives")
        val appLibs = dir(root, "applibs")

        val env = JvmEnvironment.build(
            home, natives, appLibs, dir(root, "game"), dir(root, "tmp"), "/system/lib64"
        )

        assertEquals(home.absolutePath, env["JAVA_HOME"])
        assertEquals(
            listOf(
                File(home, "lib").absolutePath,
                File(home, "lib/jli").absolutePath,
                File(home, "lib/server").absolutePath,
                natives.absolutePath,
                appLibs.absolutePath,
                "/system/lib64",
            ),
            env.getValue("LD_LIBRARY_PATH").split(File.pathSeparator),
        )
    }

    @Test
    fun skipsMissingDirectoriesAndDeduplicatesPaths() {
        val root = Files.createTempDirectory("flint-env").toFile()
        val home = dir(root, "jre")
        dir(home, "lib")
        val shared = dir(root, "shared")

        val env = JvmEnvironment.build(
            home, shared, shared, dir(root, "game"), dir(root, "tmp"), shared.absolutePath
        )
        val parts = env.getValue("LD_LIBRARY_PATH").split(File.pathSeparator)

        assertEquals(parts.distinct(), parts)
        assertFalse(parts.any { it.endsWith("lib/aarch64") })
        assertTrue(parts.contains(shared.absolutePath))
    }

    @Test
    fun setsHomeTmpAndGl4esVariables() {
        val root = Files.createTempDirectory("flint-env").toFile()
        val game = dir(root, "game")
        val temporary = dir(root, "tmp")

        val env = JvmEnvironment.build(
            dir(root, "jre"), dir(root, "natives"), dir(root, "app-libs"), game, temporary
        )

        assertEquals(game.absolutePath, env["HOME"])
        assertEquals(temporary.absolutePath, env["TMPDIR"])
        assertEquals("2", env["LIBGL_ES"])
        assertEquals("3", env["LIBGL_MIPMAP"])
        assertEquals("1", env["LIBGL_NORMALIZE"])
    }
}
