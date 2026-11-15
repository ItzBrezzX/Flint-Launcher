package com.flintlauncher.android.runtime

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LaunchRequestValidatorTest {
    @Test
    fun acceptsCommandWhenJvmClasspathMatchesInstalledClasspath() {
        val root = Files.createTempDirectory("flint-launch-valid").toFile()
        val request = request(root)

        assertNull(LaunchRequestValidator.validate(request))
    }

    @Test
    fun rejectsCommandWhenJvmClasspathDoesNotMatchInstalledClasspath() {
        val root = Files.createTempDirectory("flint-launch-mismatch").toFile()
        val request = request(root).copy(jvmArgs = listOf("-cp", File(root, "wrong.jar").absolutePath))

        val error = LaunchRequestValidator.validate(request)

        assertTrue(error!!.contains("does not match"))
    }

    @Test
    fun rejectsCommandWithoutClasspathOption() {
        val root = Files.createTempDirectory("flint-launch-no-cp").toFile()
        val request = request(root).copy(jvmArgs = listOf("-Xmx1G"))

        val error = LaunchRequestValidator.validate(request)

        assertTrue(error!!.contains("complete -cp"))
    }

    private fun request(root: File): LaunchRequest {
        val library = File(root, "library.jar").apply { writeText("library") }
        val client = File(root, "client.jar").apply { writeText("client") }
        val nativeDir = File(root, "natives").apply { mkdirs() }
        val gameDir = File(root, "game").apply { mkdirs() }
        val classpath = listOf(library, client)
        val classpathArgument = classpath.joinToString(File.pathSeparator) { it.absolutePath }
        val runtimeHome = File(root, "jre").apply { mkdirs() }

        return LaunchRequest(
            versionId = "test",
            gameDir = gameDir,
            assetsDir = File(root, "assets"),
            classpath = classpath,
            nativeDir = nativeDir,
            mainClass = "net.minecraft.client.main.Main",
            jvmArgs = listOf("-Xmx1G", "-cp", classpathArgument),
            gameArgs = listOf("--username", "FlintPlayer"),
            accountUsername = "FlintPlayer",
            accountUuid = "00000000-0000-0000-0000-000000000001",
            accessToken = "0",
            userType = "legacy",
            runtime = JavaRuntimeInfo(
                id = "test-jre",
                majorVersion = 17,
                vendor = "test",
                homeDir = runtimeHome,
                javaExecutable = null,
                arch = "arm64",
                verified = true,
            ),
            renderer = RendererInfo(
                id = "test-renderer",
                kind = RendererKind.UNKNOWN,
                displayName = "test",
                available = true,
            ),
        )
    }
}
