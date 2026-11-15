package com.flintlauncher.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.flintlauncher.android.minecraft.version.LwjglRuntimeSpec
import com.flintlauncher.android.minecraft.version.MavenCoordinate
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LaunchRequestValidatorTest {
    @Test
    fun launchRequestCarriesTheResolvedLwjglRuntimeVersion() {
        val root = Files.createTempDirectory("flint-lwjgl-request").toFile()
        val spec = LwjglRuntimeSpec(
            major = 3,
            version = "3.3.3",
            artifacts = listOf(MavenCoordinate.parse("org.lwjgl:lwjgl:3.3.3")!!),
        )

        val request = request(root).copy(lwjglRuntimeSpec = spec)

        assertEquals(spec, request.lwjglRuntimeSpec)
        assertEquals(3, request.lwjglRuntimeSpec!!.major)
    }

    @Test
    fun acceptsCommandWhenJvmClasspathMatchesInstalledClasspath() {
        val root = Files.createTempDirectory("flint-launch-valid").toFile()
        val request = request(root)

        assertNull(LaunchRequestValidator.validate(request))
    }

    @Test
    fun jliArgumentsContainTheExpandedClasspathExactlyOnceAndPreserveMainAndGameArgs() {
        val root = Files.createTempDirectory("flint-jli-argv").toFile()
        val request = request(root)

        val args = buildJliArguments(request)

        assertEquals("java", args.first())
        assertEquals(request.jvmArgs, args.subList(1, 1 + request.jvmArgs.size))
        assertEquals(request.mainClass, args[1 + request.jvmArgs.size])
        assertEquals(request.gameArgs, args.takeLast(request.gameArgs.size))
        assertEquals(1, args.count { it == "-cp" })
    }

    @Test
    fun rejectsCommandWhenJvmClasspathDoesNotMatchInstalledClasspath() {
        val root = Files.createTempDirectory("flint-launch-mismatch").toFile()
        val valid = request(root)
        val request = valid.copy(
            jvmArgs = listOf(
                "-Djava.library.path=${listOf(valid.nativeDir, valid.androidNativeDir).joinToString(File.pathSeparator) { it.absolutePath }}",
                "-Dorg.lwjgl.librarypath=${valid.androidNativeDir.absolutePath}",
                "-cp",
                File(root, "wrong.jar").absolutePath,
            ),
        )

        val error = LaunchRequestValidator.validate(request)

        assertTrue(error!!.contains("does not match"))
    }

    @Test
    fun rejectsCommandWhenLwjglLibraryPathDoesNotPointToAndroidNatives() {
        val root = Files.createTempDirectory("flint-launch-lwjgl-path").toFile()
        val valid = request(root)
        val request = valid.copy(
            jvmArgs = valid.jvmArgs.map {
                if (it.startsWith("-Dorg.lwjgl.librarypath=")) {
                    "-Dorg.lwjgl.librarypath=${valid.nativeDir.absolutePath}"
                } else it
            },
        )

        val error = LaunchRequestValidator.validate(request)

        assertTrue(error!!.contains("LWJGL library path"))
    }

    @Test
    fun rejectsCommandWhenAndroidNativeLibraryDirectoryIsMissing() {
        val root = Files.createTempDirectory("flint-launch-android-native-missing").toFile()
        val valid = request(root)
        valid.androidNativeDir.delete()

        val error = LaunchRequestValidator.validate(valid)

        assertTrue(error!!.contains("Android native library directory"))
    }

    @Test
    fun rejectsCommandWithoutClasspathOption() {
        val root = Files.createTempDirectory("flint-launch-no-cp").toFile()
        val valid = request(root)
        val request = valid.copy(
            jvmArgs = listOf(
                "-Djava.library.path=${listOf(valid.nativeDir, valid.androidNativeDir).joinToString(File.pathSeparator) { it.absolutePath }}",
                "-Dorg.lwjgl.librarypath=${valid.androidNativeDir.absolutePath}",
                "-Xmx1G",
            ),
        )

        val error = LaunchRequestValidator.validate(request)

        assertTrue(error!!.contains("complete -cp"))
    }

    private fun request(root: File): LaunchRequest {
        val library = File(root, "library.jar").apply { writeText("library") }
        val client = File(root, "client.jar").apply { writeText("client") }
        val nativeDir = File(root, "natives").apply { mkdirs() }
        val androidNativeDir = File(root, "android-native").apply { mkdirs() }
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
            androidNativeDir = androidNativeDir,
            mainClass = "net.minecraft.client.main.Main",
            jvmArgs = listOf(
                "-Xmx1G",
                "-Djava.library.path=${listOf(nativeDir, androidNativeDir).joinToString(File.pathSeparator) { it.absolutePath }}",
                "-Dorg.lwjgl.librarypath=${androidNativeDir.absolutePath}",
                "-cp",
                classpathArgument,
            ),
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
