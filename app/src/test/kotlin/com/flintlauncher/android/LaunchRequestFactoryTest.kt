package com.flintlauncher.android

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.installer.GameLayout
import com.flintlauncher.android.installer.InstalledVersion
import com.flintlauncher.android.minecraft.version.LaunchConfigurationException
import com.flintlauncher.android.minecraft.version.Library
import com.flintlauncher.android.minecraft.version.VersionJsonParser
import com.flintlauncher.android.runtime.JavaRuntimeInfo
import com.flintlauncher.android.runtime.LwjglCompatibilityLine
import com.flintlauncher.android.runtime.RendererInfo
import com.flintlauncher.android.runtime.RendererKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LaunchRequestFactoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val account = FlintAccount(
        id = "offline-1",
        type = AccountType.OFFLINE,
        username = "Steve",
        uuid = "00000000-0000-0000-0000-000000000001",
    )
    private val runtime = JavaRuntimeInfo(
        id = "java-17-arm64",
        majorVersion = 17,
        vendor = "Test JDK",
        homeDir = File("/runtime"),
        javaExecutable = File("/runtime/bin/java"),
        arch = "arm64",
        verified = true,
    )
    private val renderer = RendererInfo(
        id = "gl4es",
        kind = RendererKind.GL4ES,
        displayName = "GL4ES",
        available = true,
    )

    private data class Fixture(
        val installed: InstalledVersion,
        val layout: GameLayout,
        val gameDir: File,
        val nativesDir: File,
    )

    private fun fixture(): Fixture {
        val root = temporary.newFolder("minecraft")
        val layout = GameLayout(root)
        layout.assetsDir.mkdirs()
        layout.assetIndex("test-assets").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        val gameDir = temporary.newFolder("profile")
        val nativesDir = temporary.newFolder("natives")
        val client = layout.versionJar("test").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val library = layout.library("com/example/test/1.0/test-1.0.jar").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val version = VersionJsonParser.parse(
            """{"id":"test","type":"release","mainClass":"net.example.Main","libraries":[{"name":"org.lwjgl.lwjgl:lwjgl:2.9.4"}],"minecraftArguments":"--username ${'
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals(LwjglCompatibilityLine.LWJGL2, request.lwjglLine)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun carriesModernLwjglLineFromResolvedVersionMetadata() {
        val original = fixture()
        val modernVersion = original.installed.version.copy(
            libraries = listOf(Library(name = "org.lwjgl:lwjgl:3.3.3")),
        )
        val updated = original.copy(
            installed = original.installed.copy(version = modernVersion),
        )

        val request = create(updated)

        assertEquals(LwjglCompatibilityLine.LWJGL3, request.lwjglLine)
    }

    @Test
    fun blocksLaunchRequestWhenResolvedMetadataCannotIdentifyLwjglLine() {
        val original = fixture()
        val unsupported = original.copy(
            installed = original.installed.copy(version = original.installed.version.copy(libraries = emptyList())),
        )

        try {
            create(unsupported)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("LWJGL"))
        }
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{auth_player_name} --gameDir ${',
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{game_directory}"}""",
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{auth_player_name} --gameDir ${'
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals(LwjglCompatibilityLine.LWJGL2, request.lwjglLine)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun carriesModernLwjglLineFromResolvedVersionMetadata() {
        val original = fixture()
        val modernVersion = original.installed.version.copy(
            libraries = listOf(Library(name = "org.lwjgl:lwjgl:3.3.3")),
        )
        val updated = original.copy(
            installed = original.installed.copy(version = modernVersion),
        )

        val request = create(updated)

        assertEquals(LwjglCompatibilityLine.LWJGL3, request.lwjglLine)
    }

    @Test
    fun blocksLaunchRequestWhenResolvedMetadataCannotIdentifyLwjglLine() {
        val original = fixture()
        val unsupported = original.copy(
            installed = original.installed.copy(version = original.installed.version.copy(libraries = emptyList())),
        )

        try {
            create(unsupported)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("LWJGL"))
        }
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{auth_player_name} --gameDir ${',
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{game_directory}"}""",
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{game_directory}"}""",
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals(LwjglCompatibilityLine.LWJGL2, request.lwjglLine)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun carriesModernLwjglLineFromResolvedVersionMetadata() {
        val original = fixture()
        val modernVersion = original.installed.version.copy(
            libraries = listOf(Library(name = "org.lwjgl:lwjgl:3.3.3")),
        )
        val updated = original.copy(
            installed = original.installed.copy(version = modernVersion),
        )

        val request = create(updated)

        assertEquals(LwjglCompatibilityLine.LWJGL3, request.lwjglLine)
    }

    @Test
    fun blocksLaunchRequestWhenResolvedMetadataCannotIdentifyLwjglLine() {
        val original = fixture()
        val unsupported = original.copy(
            installed = original.installed.copy(version = original.installed.version.copy(libraries = emptyList())),
        )

        try {
            create(unsupported)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("LWJGL"))
        }
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{auth_player_name} --gameDir ${',
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
}{game_directory}"}""",
        )
        return Fixture(
            installed = InstalledVersion(
                versionId = "test",
                version = version,
                clientJar = client,
                classpath = listOf(library, client),
                nativeJars = emptyList(),
                assetsRoot = layout.assetsDir,
                assetIndexId = "test-assets",
                loggingJvmArgument = "-Dlog4j.configurationFile=/logs/client.xml",
                requiredJavaMajor = 8,
            ),
            layout = layout,
            gameDir = gameDir,
            nativesDir = nativesDir,
        )
    }

    private fun create(
        fixture: Fixture,
        account: FlintAccount = this.account,
        token: String = "",
        renderer: RendererInfo = this.renderer,
        profileJvm: List<String> = emptyList(),
    ) = LaunchRequestFactory.create(
        installed = fixture.installed,
        layout = fixture.layout,
        gameDirectory = fixture.gameDir,
        nativesDirectory = fixture.nativesDir,
        account = account,
        accessToken = token,
        runtime = runtime,
        renderer = renderer,
        launcherVersion = "test",
        runtimeHeapMb = 1024,
        customJvmArguments = "-Dfile.encoding=UTF-8",
        profileJvmArguments = profileJvm,
        profileGameArguments = listOf("--fullscreen"),
    )

    @Test
    fun assemblesOfflineLaunchWithManagedRuntimeSettings() {
        val request = create(fixture())

        assertEquals("net.example.Main", request.mainClass)
        assertEquals("legacy", request.userType)
        assertEquals("0", request.accessToken)
        assertTrue(request.jvmArgs.contains("-Dfile.encoding=UTF-8"))
        assertTrue(request.jvmArgs.contains("-Dlog4j.configurationFile=/logs/client.xml"))
        assertEquals("-Xmx1024M", request.jvmArgs.last())
        assertTrue(request.gameArgs.containsAll(listOf("--username", "Steve", "--gameDir")))
        assertEquals("--fullscreen", request.gameArgs.last())
    }

    @Test
    fun prefersTheNativeDirectoryPreparedByTheInstaller() {
        val original = fixture()
        val extractedNatives = temporary.newFolder("extracted-natives")
        val updated = original.copy(
            installed = original.installed.copy(nativesDirectory = extractedNatives),
        )

        val request = create(updated)

        assertEquals(extractedNatives.canonicalFile, request.nativeDir.canonicalFile)
        assertTrue(request.jvmArgs.contains("-Djava.library.path=${extractedNatives.absolutePath}"))
    }

    @Test
    fun rejectsUnverifiedRendererBeforeProducingRequest() {
        val unavailable = renderer.copy(available = false)
        try {
            create(fixture(), renderer = unavailable)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("renderer"))
        }
    }

    @Test
    fun rejectsMicrosoftAccountWithoutAccessToken() {
        val microsoft = account.copy(type = AccountType.MICROSOFT, tokenRef = "secure-token-ref")
        try {
            create(fixture(), account = microsoft)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("token"))
        }
    }

    @Test
    fun rejectsDangerousImportedProfileJvmArguments() {
        try {
            create(fixture(), profileJvm = listOf("-javaagent:/tmp/agent.jar"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("blocked"))
        }
    }

    @Test
    fun rejectsProfileArgumentsThatOverrideManagedHeapOrClasspath() {
        try {
            create(fixture(), profileJvm = listOf("-Xmx8G", "-cp"))
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("managed"))
        }
    }

}
