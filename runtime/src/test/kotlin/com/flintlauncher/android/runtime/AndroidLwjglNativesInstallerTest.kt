package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidLwjglNativesInstallerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val nativeNames = listOf(
        "liblwjgl.so",
        "liblwjgl_opengl.so",
        "libglfw.so",
        "libflint_game_glfw.so",
        "libGL.so",
    )

    @Test
    fun stagesAllRequiredArm64LibrariesAndPreservesTheElfHeaders() {
        val source = File(temporary.root, "apk-libs").apply { mkdirs() }
        nativeNames.forEach { name -> File(source, name).writeBytes(elfHeader(elfClass = 2, machine = 183)) }
        val destination = File(temporary.root, "minecraft-natives")

        val staged = AndroidLwjglNativesInstaller.install(source, destination, "arm64")

        assertEquals(nativeNames, staged.map { it.name })
        assertTrue(staged.all { it.isFile && it.length() >= 20L })
        assertTrue(staged.all { it.readBytes().take(4) == listOf(0x7f, 0x45, 0x4c, 0x46).map(Int::toByte) })
    }

    @Test
    fun refusesAHostLibraryWithTheWrongMachineType() {
        val source = File(temporary.root, "wrong-abi").apply { mkdirs() }
        nativeNames.forEach { name -> File(source, name).writeBytes(elfHeader(elfClass = 2, machine = 62)) }

        val error = runCatching {
            AndroidLwjglNativesInstaller.install(source, File(temporary.root, "out"), "arm64")
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("Wrong ABI"))
    }

    @Test
    fun refusesToProceedWhenOneRequiredLibraryIsMissing() {
        val source = File(temporary.root, "missing").apply { mkdirs() }
        nativeNames.dropLast(1).forEach { name -> File(source, name).writeBytes(elfHeader(2, 183)) }

        val error = runCatching {
            AndroidLwjglNativesInstaller.install(source, File(temporary.root, "out"), "arm64")
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("libGL.so"))
    }

    @Test
    fun rejectsAnUnknownAndroidAbi() {
        val error = runCatching {
            AndroidLwjglNativesInstaller.install(
                File(temporary.root, "missing"),
                File(temporary.root, "out"),
                "mips",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun elfHeader(elfClass: Int, machine: Int): ByteArray =
        ByteArray(32).also {
            it[0] = 0x7f
            it[1] = 'E'.code.toByte()
            it[2] = 'L'.code.toByte()
            it[3] = 'F'.code.toByte()
            it[4] = elfClass.toByte()
            it[5] = 1 // ELFDATA2LSB
            it[6] = 1 // EV_CURRENT
            it[18] = (machine and 0xff).toByte()
            it[19] = ((machine ushr 8) and 0xff).toByte()
        }
}
