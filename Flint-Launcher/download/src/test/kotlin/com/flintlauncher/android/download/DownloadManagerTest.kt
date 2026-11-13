package com.flintlauncher.android.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Serves in-memory files, with optional Range support and scripted failures. */
private class FileServer : Dispatcher() {
    val files = ConcurrentHashMap<String, ByteArray>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    val failuresLeft = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile var supportRanges = true

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests.add(request)
        val path = request.path ?: return MockResponse().setResponseCode(404)
        val data = files[path] ?: return MockResponse().setResponseCode(404)
        val left = failuresLeft[path]
        if (left != null && left.getAndDecrement() > 0) return MockResponse().setResponseCode(500)

        val range = request.getHeader("Range")
        if (supportRanges && range != null) {
            val start = range.removePrefix("bytes=").substringBefore('-').toInt()
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-${data.size - 1}/${data.size}")
                .setBody(Buffer().write(data.copyOfRange(start, data.size)))
        }
        return MockResponse().setBody(Buffer().write(data))
    }
}

class DownloadManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fileServer = FileServer()
    private val server = MockWebServer()
    private val config = DownloadConfig(concurrency = 2, maxAttempts = 3, backoffBaseMillis = 0, progressIntervalMillis = 20)

    @Before
    fun setUp() {
        server.dispatcher = fileServer
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun newManager(usableSpace: (File) -> Long = { it.usableSpace }) =
        DownloadManager(OkHttpClient(), config, usableSpace = usableSpace)

    private fun bytes(size: Int, seed: Int = 1) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun sha1(data: ByteArray) =
        MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }

    private fun request(path: String, data: ByteArray, withHash: Boolean = true, name: String = path.trimStart('/')) =
        DownloadRequest(
            id = name,
            url = server.url(path).toString(),
            destination = File(tmp.root, name),
            sizeBytes = data.size.toLong(),
            hash = if (withHash) ExpectedHash(HashAlgorithm.SHA1, sha1(data)) else null,
        )

    @Test
    fun downloadsAllFilesAndVerifiesThem() = runBlocking {
        val a = bytes(5_000, 1)
        val b = bytes(20_000, 2)
        val c = bytes(1, 3)
        fileServer.files["/a.bin"] = a
        fileServer.files["/b.bin"] = b
        fileServer.files["/c.bin"] = c
        val manager = newManager()

        val result = manager.run(listOf(request("/a.bin", a), request("/b.bin", b), request("/c.bin", c)))

        assertEquals(3, result.completed)
        assertTrue(result.failed.isEmpty())
        assertTrue(File(tmp.root, "a.bin").readBytes().contentEquals(a))
        assertTrue(File(tmp.root, "b.bin").readBytes().contentEquals(b))
        val progress = manager.progress.value
        assertEquals(BatchState.COMPLETED, progress.state)
        assertEquals(25_001L, progress.downloadedBytes)
        assertEquals(25_001L, progress.totalBytes)
        assertEquals(100.0, progress.percent!!, 0.001)
        assertEquals(0, progress.pendingFiles)
    }

    @Test
    fun doesNotDownloadFilesThatAreAlreadyValid() = runBlocking {
        val data = bytes(3_000)
        fileServer.files["/valid.bin"] = data
        File(tmp.root, "valid.bin").writeBytes(data)

        val result = newManager().run(listOf(request("/valid.bin", data)))

        assertEquals(1, result.completed)
        assertTrue(fileServer.requests.isEmpty())
    }

    @Test
    fun replacesAnExistingFileThatIsCorrupt() = runBlocking {
        val data = bytes(3_000)
        fileServer.files["/bad.bin"] = data
        File(tmp.root, "bad.bin").writeBytes(ByteArray(3_000)) // right size, wrong content

        val result = newManager().run(listOf(request("/bad.bin", data)))

        assertEquals(1, result.completed)
        assertTrue(File(tmp.root, "bad.bin").readBytes().contentEquals(data))
    }

    @Test
    fun failsOnChecksumMismatchAndLeavesNoFileBehind() = runBlocking {
        val data = bytes(2_000)
        fileServer.files["/x.bin"] = data
        val wrong = request("/x.bin", data).copy(hash = ExpectedHash(HashAlgorithm.SHA1, "0".repeat(40)))

        val result = newManager().run(listOf(wrong))

        assertEquals(1, result.failed.size)
        assertTrue(result.failed.single().reason.contains("Checksum", ignoreCase = true))
        assertFalse(File(tmp.root, "x.bin").exists())
        assertFalse(File(tmp.root, "x.bin.part").exists())
        assertEquals(3, fileServer.requests.size) // maxAttempts
    }

    @Test
    fun resumesAnInterruptedFileWithARangeRequest() = runBlocking {
        val data = bytes(3_000)
        fileServer.files["/r.bin"] = data
        File(tmp.root, "r.bin.part").writeBytes(data.copyOfRange(0, 1_000))

        val result = newManager().run(listOf(request("/r.bin", data)))

        assertEquals(1, result.completed)
        assertEquals("bytes=1000-", fileServer.requests.single().getHeader("Range"))
        assertTrue(File(tmp.root, "r.bin").readBytes().contentEquals(data))
        assertFalse(File(tmp.root, "r.bin.part").exists())
    }

    @Test
    fun restartsFromZeroWhenTheServerIgnoresRange() = runBlocking {
        val data = bytes(3_000)
        fileServer.files["/n.bin"] = data
        fileServer.supportRanges = false
        File(tmp.root, "n.bin.part").writeBytes(data.copyOfRange(0, 500))
        val manager = newManager()

        val result = manager.run(listOf(request("/n.bin", data)))

        assertEquals(1, result.completed)
        assertTrue(File(tmp.root, "n.bin").readBytes().contentEquals(data))
        assertEquals(3_000L, manager.progress.value.downloadedBytes)
    }

    @Test
    fun retriesServerErrorsThenSucceeds() = runBlocking {
        val data = bytes(1_000)
        fileServer.files["/flaky.bin"] = data
        fileServer.failuresLeft["/flaky.bin"] = AtomicInteger(2)

        val result = newManager().run(listOf(request("/flaky.bin", data)))

        assertEquals(1, result.completed)
        assertEquals(3, fileServer.requests.size)
    }

    @Test
    fun doesNotRetryClientErrors() = runBlocking {
        val result = newManager().run(
            listOf(DownloadRequest("missing", server.url("/missing.bin").toString(), File(tmp.root, "missing.bin"), 10, null)),
        )

        assertEquals(1, result.failed.size)
        assertEquals("HTTP 404", result.failed.single().reason)
        assertEquals(1, fileServer.requests.size)
    }

    @Test
    fun failsBeforeAnyRequestWhenStorageIsInsufficient() = runBlocking {
        val request = DownloadRequest("big", server.url("/big.bin").toString(), File(tmp.root, "big.bin"), 10L * 1024 * 1024, null)
        val manager = newManager(usableSpace = { 1L })

        val result = manager.run(listOf(request))

        assertEquals(1, result.failed.size)
        assertTrue(result.failed.single().reason.contains("storage", ignoreCase = true))
        assertTrue(fileServer.requests.isEmpty())
        assertEquals(BatchState.FAILED, manager.progress.value.state)
    }

    @Test
    fun retryFailedRunsOnlyTheFilesThatFailed() = runBlocking {
        val ok = bytes(500, 1)
        val later = bytes(700, 2)
        fileServer.files["/ok.bin"] = ok
        val manager = newManager()

        val first = manager.run(listOf(request("/ok.bin", ok), request("/later.bin", later)))
        assertEquals(1, first.completed)
        assertEquals(1, first.failed.size)

        fileServer.files["/later.bin"] = later
        val second = manager.retryFailed()

        assertEquals(1, second.completed)
        assertTrue(second.failed.isEmpty())
        assertTrue(File(tmp.root, "later.bin").readBytes().contentEquals(later))
    }

    @Test
    fun unknownSizesMeanNoTotalAndNoPercent() = runBlocking {
        val data = bytes(400)
        fileServer.files["/u.bin"] = data
        val manager = newManager()

        manager.run(listOf(request("/u.bin", data, withHash = false).copy(sizeBytes = null)))

        assertEquals(null, manager.progress.value.totalBytes)
        assertEquals(null, manager.progress.value.percent)
        assertNotNull(File(tmp.root, "u.bin").takeIf { it.exists() })
    }
}
