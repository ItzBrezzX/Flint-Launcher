package com.flintlauncher.android.installer

import com.flintlauncher.android.core.models.PlayerIdentity
import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.minecraft.session.SessionState
import com.flintlauncher.android.minecraft.session.GameSession
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.VersionEra
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class FakeMojang2 : Dispatcher() {
    val blobs = ConcurrentHashMap<String, ByteArray>()
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path ?: return MockResponse().setResponseCode(404)
        val data = blobs[path] ?: return MockResponse().setResponseCode(404)
        return MockResponse().setBody(Buffer().write(data))
    }
}

class LaunchCoordinatorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val fake = FakeMojang2()
    private lateinit var base: String
    private lateinit var layout: GameLayout
    private lateinit var installer: MinecraftInstaller
    private lateinit var coordinator: LaunchCoordinator

    private val clientJar = ByteArray(500) { (it % 251).toByte() }
    private val libJar = ByteArray(300) { ((it * 3) % 251).toByte() }
    private val nativeJar = nativeJarBytes()
    private val asset1 = "asset-one".toByteArray()
    private val hash1 = sha1(asset1)

    private fun sha1(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun nativeJarBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("liblwjgl.so")); zip.write("elf-lwjgl".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("libtwitchsdk.so")); zip.write("elf-twitch".toByteArray()); zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Before
    fun setUp() {
        server.dispatcher = fake
        server.start()
        base = server.url("/").toString().trimEnd('/')
        layout = GameLayout(tmp.root)
        val downloads = DownloadManager(
            OkHttpClient(),
            DownloadConfig(concurrency = 2, maxAttempts = 2, backoffBaseMillis = 0, progressIntervalMillis = 20),
        )
        installer = MinecraftInstaller(
            http = OkHttpClient(), layout = layout, downloads = downloads,
            environment = Environment(osName = "linux", osArch = "arm64"),
            manifestUrl = "$base/manifest.json", assetBaseUrl = "$base/assets/objects/",
            metadataRetryDelayMillis = 0,
        )
        coordinator = LaunchCoordinator(installer, layout, Environment(osName = "linux", osArch = "arm64"), "test-1.0")
        publishVersion("1.20.4-test")
    }

    @After
    fun tearDown() = runCatching { server.shutdown() }

    private fun publishVersion(id: String) {
        fake.blobs["/files/client.jar"] = clientJar
        fake.blobs["/files/lib.jar"] = libJar
        fake.blobs["/files/natives-linux.jar"] = nativeJar
        fake.blobs["/assets/objects/${hash1.take(2)}/$hash1"] = asset1
        val index = """{"objects":{"a.txt":{"hash":"$hash1","size":${asset1.size}}}}""".toByteArray()
        fake.blobs["/assets/indexes/8.json"] = index
        val versionJson = """{
          "id":"$id","type":"release","mainClass":"net.minecraft.client.main.Main","assets":"8",
          "javaVersion":{"component":"java-runtime-gamma","majorVersion":17},
          "downloads":{"client":{"sha1":"${sha1(clientJar)}","size":${clientJar.size},"url":"$base/files/client.jar"}},
          "assetIndex":{"id":"8","sha1":"${sha1(index)}","size":${index.size},"url":"$base/assets/indexes/8.json"},
          "libraries":[
            {"name":"com.example:lib:1.0","downloads":{"artifact":{
              "path":"com/example/lib/1.0/lib-1.0.jar","sha1":"${sha1(libJar)}","size":${libJar.size},"url":"$base/files/lib.jar"}}},
            {"name":"org.lwjgl:lwjgl:3.3.1","natives":{"linux":"natives-linux"},"downloads":{
              "artifact":{"path":"org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar","sha1":"${sha1(libJar)}","size":${libJar.size},"url":"$base/files/lib.jar"},
              "classifiers":{"natives-linux":{
                "path":"org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar","sha1":"${sha1(nativeJar)}","size":${nativeJar.size},"url":"$base/files/natives-linux.jar"}}}}
          ],
          "arguments":{"game":["--username","${'$'}{auth_player_name}","--version","${'$'}{version_name}"],
                       "jvm":["-cp","${'$'}{classpath}"]}
        }"""
        fake.blobs["/meta/$id.json"] = versionJson.toByteArray()
        fake.blobs["/manifest.json"] = """{"latest":{"release":"$id","snapshot":"$id"},"versions":[
            {"id":"$id","type":"release","url":"$base/meta/$id.json",
             "time":"2026-01-01T00:00:00+00:00","releaseTime":"2026-01-01T00:00:00+00:00",
             "sha1":"${sha1(versionJson.toByteArray())}","complianceLevel":1}]}""".toByteArray()
    }

    @Test
    fun prepareLaunchInstallsExtractsFiltersAndBuildsPrimitiveParams() = runBlocking {
        val gameDir = File(tmp.root, "profiles/p1").apply { mkdirs() }
        val flintNatives = File(tmp.root, "flint-natives").apply { mkdirs() }
        val cacheDir = File(tmp.root, "cache")
        val session = GameSession("1.20.4-test")
        val identity = PlayerIdentity(username = "Steve", uuid = "00000000-0000-0000-0000-000000000001", accessToken = "t", userType = "legacy")

        val result = coordinator.prepareLaunch(
            LaunchInput("1.20.4-test", memoryMb = 2048, gameDir = gameDir),
            identity, flintNatives, cacheDir, session,
        )

        assertTrue("expected success, got $result", result.isSuccess)
        val plan = result.getOrNull()!!
        // Era classification
        assertEquals(VersionEra.RELEASE_1_17_TO_1_20, plan.era)
        assertEquals(17, plan.requiredJavaMajor)
        // Classpath: two library jars + client jar (LWJGL ships both an artifact jar and a native classifier jar)
        assertEquals(3, plan.classpath.size)
        assertTrue(plan.classpath.last().name.endsWith(".jar")) // client jar last
        assertTrue(plan.classpath.any { it.name == "1.20.4-test.jar" })
        // LWJGL native filtered out, twitch kept
        assertTrue("liblwjgl.so should be filtered", plan.filteredNativeLibraries.contains("liblwjgl.so"))
        assertTrue(File(plan.extractedNativesDir, "libtwitchsdk.so").isFile)
        assertTrue(!File(plan.extractedNativesDir, "liblwjgl.so").exists())
        // JVM args include memory, library path with Flint natives, user.home
        assertTrue(plan.jvmArgs.contains("-Xmx2048M"))
        assertTrue(plan.jvmArgs.any { it.startsWith("-Djava.library.path=") && it.contains(flintNatives.absolutePath) })
        assertTrue(plan.jvmArgs.any { it == "-Dorg.lwjgl.librarypath=${flintNatives.absolutePath}" })
        assertTrue(plan.jvmArgs.any { it.startsWith("-Duser.home=") && it.endsWith(gameDir.absolutePath) })
        // Main class + game args with substituted username
        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertTrue(plan.gameArgs.containsAll(listOf("--username", "Steve")))
        // Env includes LD_LIBRARY_PATH with Flint natives first
        val ld = plan.env["LD_LIBRARY_PATH"]!!
        assertTrue(ld.startsWith(flintNatives.absolutePath))
        assertTrue(ld.contains(plan.extractedNativesDir.absolutePath))
        // Working dir is the profile game dir
        assertEquals(gameDir, plan.workingDir)
        // Session advanced to LAUNCHING
        assertEquals(SessionState.LAUNCHING, session.state)
        // Primitive params contract is derivable
        val params = plan.toParameters()
        assertEquals(plan.mainClass, params.mainClass)
        assertEquals(plan.jvmArgs, params.jvmArgs)
        assertEquals(plan.gameArgs, params.gameArgs)
        assertEquals(plan.classpath, params.classpath)
        assertEquals(plan.env, params.env)
        assertEquals(plan.workingDir, params.workingDir)
        assertTrue(params.argumentList().contains("net.minecraft.client.main.Main"))
    }

    @Test
    fun unknownVersionIsACategorizedConfigError() = runBlocking {
        val session = GameSession("nope")
        val result = coordinator.prepareLaunch(
            LaunchInput("nope", 1024, gameDir = File(tmp.root, "g")),
            PlayerIdentity.offline("P"), File(tmp.root, "fn"), File(tmp.root, "c"), session,
        )
        assertTrue(result.isError)
        val err = result.errorOrNull()!!
        assertTrue(err is com.flintlauncher.android.core.FlintError.ConfigError)
        assertEquals(SessionState.FAILED, session.state)
    }
}
