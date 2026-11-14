package com.flintlauncher.android

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.installer.GameLayout
import com.flintlauncher.android.installer.InstalledVersion
import com.flintlauncher.android.minecraft.version.LaunchConfigurationException
import com.flintlauncher.android.minecraft.version.VersionJsonParser
import com.flintlauncher.android.runtime.JavaRuntimeInfo
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
            """{"id":"test","type":"release","mainClass":"net.example.Main","minecraftArguments":"--username ${'$'}{auth_player_name} --gameDir ${'$'}{game_directory}"}""",
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
}
