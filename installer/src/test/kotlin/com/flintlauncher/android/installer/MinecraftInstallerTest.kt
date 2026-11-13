package com.flintlauncher.android.installer

import com.flintlauncher.android.download.BatchState
import com.flintlauncher.android.download.DownloadConfig
import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.minecraft.version.Environment
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A stand-in for Mojang's servers: manifest, version JSON, asset index, jars and asset objects. */
private class FakeMojang : Dispatcher() {
    val blobs = ConcurrentHashMap<String, ByteArray>()
    val requested = CopyOnWriteArrayList<String>()
    val failuresLeft = ConcurrentHashMap<String, AtomicInteger>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path ?: return MockResponse().setResponseCode(404)
        requested.add(path)
        val data = blobs[path] ?: return MockResponse().setResponseCode(404)
        val left = failuresLeft[path]
        if (left != null && left.getAndDecrement() > 0) return MockResponse().setResponseCode(503)
        return MockResponse().setBody(Buffer().write(data))
    }

    fun count(path: String) = requested.count { it == path }
}

class MinecraftInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val fake = FakeMojang()
    private lateinit var base: String
    private lateinit var layout: GameLayout
    private lateinit var downloads: DownloadManager
    private lateinit var installer: MinecraftInstaller

    private val clientJar = ByteArray(3000) { (it % 251).toByte() }
    private val libJar = ByteArray(1500) { ((it * 7) % 251).toByte() }
    private val logXml = "<Configuration status=\"WARN\"/>".toByteArray()
    private val asset1 = "first asset".toByteArray()
    private val asset2 = "second asset, different".toByteArray()
    private val hash1 = sha1(asset1)
    private val hash2 = sha1(asset2)

    private fun sha1(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        server.dispatcher = fake
        server.start()
        base = server.url("/").toString().trimEnd('/')
        layout = GameLayout(tmp.root)
        downloads = DownloadManager(
            OkHttpClient(),
            DownloadConfig(concurrency = 2, maxAttempts = 2, backoffBaseMillis = 0, progressIntervalMillis = 20),
        )
        installer = MinecraftInstaller(
            http = OkHttpClient(),
            layout = layout,
            downloads = downloads,
            environment = Environment(osName = "linux", osArch = "arm64"),
            manifestUrl = "$base/manifest.json",
            assetBaseUrl = "$base/assets/objects/",
            metadataRetryDelayMillis = 0,
        )
        publishVersion("1.0-test")
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun publishVersion(id: String, manifestHashOverride: String? = null) {
        fake.blobs["/files/client.jar"] = clientJar
        fake.blobs["/files/lib.jar"] = libJar
        fake.blobs["/files/client-test.xml"] = logXml
        fake.blobs["/assets/objects/${hash1.take(2)}/$hash1"] = asset1
        fake.blobs["/assets/objects/${hash2.take(2)}/$hash2"] = asset2
        val index = """{"objects":{
            "a.txt":{"hash":"$hash1","size":${asset1.size}},
            "b.txt":{"hash":"$hash2","size":${asset2.size}},
            "dup.txt":{"hash":"$hash1","size":${asset1.size}}}}""".toByteArray()
        fake.blobs["/assets/indexes/idx.json"] = index

        val versionJson = """{
          "id":"$id","type":"release","mainClass":"net.example.Main","assets":"idx",
          "javaVersion":{"component":"test","majorVersion":21},
          "downloads":{"client":{"sha1":"${sha1(clientJar)}","size":${clientJar.size},"url":"$base/files/client.jar"}},
          "assetIndex":{"id":"idx","sha1":"${sha1(index)}","size":${index.size},"url":"$base/assets/indexes/idx.json"},
          "logging":{"client":{"argument":"-Dlog4j.configurationFile=${'$'}{path}","type":"log4j2-xml",
            "file":{"id":"client-test.xml","sha1":"${sha1(logXml)}","size":${logXml.size},"url":"$base/files/client-test.xml"}}},
          "libraries":[{"name":"com.example:lib:1.0","downloads":{"artifact":{
            "path":"com/example/lib/1.0/lib-1.0.jar","sha1":"${sha1(libJar)}","size":${libJar.size},"url":"$base/files/lib.jar"}}}],
          "arguments":{"game":["--version","${'$'}{version_name}"],"jvm":["-cp","${'$'}{classpath}"]}
        }"""
        fake.blobs["/meta/$id.json"] = versionJson.toByteArray()
        val manifestHash = manifestHashOverride ?: sha1(versionJson.toByteArray())
        fake.blobs["/manifest.json"] = """{"latest":{"release":"$id","snapshot":"$id"},"versions":[
            {"id":"$id","type":"release","url":"$base/meta/$id.json",
             "time":"2026-01-01T00:00:00+00:00","releaseTime":"2026-01-01T00:00:00+00:00",
             "sha1":"$manifestHash","complianceLevel":1}]}""".toByteArray()
    }

    private fun success(result: InstallResult): InstalledVersion {
        assertTrue("expected Success but was $result", result is InstallResult.Success)
        return (result as InstallResult.Success).installed
    }

    @Test
    fun fullInstallDownloadsEverythingAndReportsStagesInOrder() = runBlocking {
        val stages = mutableListOf<InstallStage>()

        val installed = success(installer.install("1.0-test") { stage -> stages.add(stage) })

        assertEquals(
            listOf(
                InstallStage.RESOLVING_METADATA,
                InstallStage.PREPARING_QUEUE,
                InstallStage.DOWNLOADING_ASSETS,
                InstallStage.VALIDATING_INTEGRITY,
                InstallStage.GENERATING_METADATA,
                InstallStage.FINAL_VERIFICATION,
                InstallStage.READY,
            ),
            stages,
        )
        assertTrue(layout.versionJar("1.0-test").readBytes().contentEquals(clientJar))
        assertTrue(layout.versionJson("1.0-test").isFile)
        assertTrue(layout.library("com/example/lib/1.0/lib-1.0.jar").readBytes().contentEquals(libJar))
        assertTrue(layout.assetObject(hash1).readBytes().contentEquals(asset1))
        assertTrue(layout.assetObject(hash2).readBytes().contentEquals(asset2))
        assertTrue(layout.assetIndex("idx").isFile)
        assertEquals(listOf(layout.library("com/example/lib/1.0/lib-1.0.jar"), layout.versionJar("1.0-test")), installed.classpath)
        assertEquals(21, installed.requiredJavaMajor)
        assertEquals("idx", installed.assetIndexId)
        assertEquals(
            "-Dlog4j.configurationFile=${layout.logConfig("client-test.xml").absolutePath}",
            installed.loggingJvmArgument,
        )
        assertEquals(BatchState.COMPLETED, downloads.progress.value.state)
        assertTrue(installer.isInstalled("1.0-test"))
        assertEquals(1, fake.count("/assets/objects/${hash1.take(2)}/$hash1")) // shared asset fetched once
    }

    @Test
    fun runningAgainDownloadsNoGameFiles() = runBlocking {
        success(installer.install("1.0-test"))
        fake.requested.clear()

        success(installer.install("1.0-test"))

        assertEquals(setOf("/manifest.json", "/meta/1.0-test.json"), fake.requested.toSet())
    }

    @Test
    fun aCorruptedLibraryIsRepairedAndNothingElseIsFetched() = runBlocking {
        success(installer.install("1.0-test"))
        val lib = layout.library("com/example/lib/1.0/lib-1.0.jar")
        lib.writeBytes(ByteArray(libJar.size)) // same size, wrong content
        fake.requested.clear()

        success(installer.install("1.0-test"))

        assertTrue(lib.readBytes().contentEquals(libJar))
        assertEquals(listOf("/files/lib.jar"), fake.requested.filter { it.startsWith("/files/") })
    }

    @Test
    fun aBadVersionJsonChecksumIsRejectedBeforeAnythingIsDownloaded() = runBlocking {
        publishVersion("1.0-test", manifestHashOverride = "0".repeat(40))

        val result = installer.install("1.0-test")

        assertTrue(result is InstallResult.Failed)
        assertTrue((result as InstallResult.Failed).message.contains("Checksum"))
        assertTrue(fake.requested.none { it.startsWith("/files/") })
    }

    @Test
    fun anUnknownVersionIsAClearFailure() = runBlocking {
        val result = installer.install("no-such-version")
        assertTrue(!installer.isInstalled("no-such-version"))
        assertTrue(result is InstallResult.Failed)
        assertTrue((result as InstallResult.Failed).message.contains("Unknown Minecraft version"))
    }

    @Test
    fun oneBrokenAssetFailsTheInstallThenAnotherRunFinishesIt() = runBlocking {
        val brokenPath = "/assets/objects/${hash2.take(2)}/$hash2"
        fake.failuresLeft[brokenPath] = AtomicInteger(10)

        val first = installer.install("1.0-test")

        assertTrue(first is InstallResult.Failed)
        assertTrue(!installer.isInstalled("1.0-test")) // a failed install is never marked installed
        assertEquals(1, (first as InstallResult.Failed).failures.size)
        assertTrue(layout.assetObject(hash1).isFile) // the healthy files still arrived

        fake.failuresLeft.clear()
        success(installer.install("1.0-test"))
        assertTrue(layout.assetObject(hash2).readBytes().contentEquals(asset2))
        assertEquals(1, fake.count("/assets/objects/${hash1.take(2)}/$hash1")) // not downloaded twice
    }

    @Test
    fun aTransientServerErrorOnTheManifestIsRetried() = runBlocking {
        fake.failuresLeft["/manifest.json"] = AtomicInteger(1)

        success(installer.install("1.0-test"))

        assertTrue(fake.count("/manifest.json") >= 2)
    }

    @Test
    fun aLoaderProfileInheritsFromTheVanillaVersion() = runBlocking {
        fake.blobs["/maven/net/example/loader/0.1/loader-0.1.jar"] = "loader jar".toByteArray()
        val profile = layout.versionJson("loader-1.0")
        profile.parentFile!!.mkdirs()
        profile.writeText(
            """{"id":"loader-1.0","inheritsFrom":"1.0-test","mainClass":"net.example.LoaderMain",
               "libraries":[{"name":"net.example:loader:0.1","url":"$base/maven"}],
               "arguments":{"game":[],"jvm":["-Dloader=1"]}}""",
        )

        val installed = success(installer.install("loader-1.0"))

        assertEquals("net.example.LoaderMain", installed.version.mainClass)
        assertEquals(layout.library("net/example/loader/0.1/loader-0.1.jar"), installed.classpath.first())
        assertEquals(layout.versionJar("1.0-test"), installed.clientJar)
        assertEquals(21, installed.requiredJavaMajor)
        assertTrue(installed.version.arguments!!.jvm.size > 1)
    }
}

class NativesExtractorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(tmp.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entryName, content) in entries) {
                out.putNextEntry(ZipEntry(entryName))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsLibrariesAndSkipsMetaInf() {
        val jar = zip("natives.jar", mapOf("libfoo.so" to "elf", "META-INF/MANIFEST.MF" to "x", "sub/libbar.so" to "elf2"))
        val target = File(tmp.root, "natives")

        val extracted = NativesExtractor.extract(listOf(jar), target)

        assertEquals(setOf("libfoo.so", "libbar.so"), extracted.map { it.name }.toSet())
        assertTrue(File(target, "libfoo.so").isFile)
        assertTrue(!File(target, "META-INF").exists())
    }

    @Test
    fun anEntryThatEscapesTheTargetAbortsExtraction() {
        val jar = zip("evil.jar", mapOf("../evil.so" to "x"))
        try {
            NativesExtractor.extract(listOf(jar), File(tmp.root, "natives"))
            fail("expected InstallPlanException")
        } catch (e: InstallPlanException) {
            assertTrue(e.message!!.contains("outside"))
        }
        assertTrue(!File(tmp.root, "evil.so").exists())
    }
}
