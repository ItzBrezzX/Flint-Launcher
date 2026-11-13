package com.flintlauncher.android.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private class PausedSignal : RuntimeException()
private class CancelledSignal : RuntimeException()
private class HttpStatusException(val code: Int) : IOException("HTTP $code")
private class RetryableDownloadException(message: String) : IOException(message)

/**
 * Parallel, resumable, verified downloads.
 *
 * - Bytes land in `<dest>.part`; an interrupted file resumes with an HTTP Range request
 *   (and restarts cleanly if the server ignores Range).
 * - Existing files that already match size and hash are not downloaded again.
 * - Size and hash are checked before the part file is moved into place.
 * - Network errors, 408/429 and 5xx are retried with exponential backoff; other 4xx and
 *   a full disk are not.
 * - Cancel keeps `.part` files so the batch can be resumed later.
 *
 * One batch runs at a time. Pause/cancel take effect between buffer reads.
 */
private val CONTENT_RANGE = Regex("""bytes (\\d+)-(\\d+)/(\\d+|\\*)""", RegexOption.IGNORE_CASE)

class DownloadManager(
    private val client: OkHttpClient,
    private val config: DownloadConfig = DownloadConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val usableSpace: (File) -> Long = { it.usableSpace },
) {
    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    private val running = AtomicBoolean(false)
    private val paused = MutableStateFlow(false)
    @Volatile private var cancelled = false
    @Volatile private var state = BatchState.IDLE

    private val downloaded = AtomicLong(0)      // progress, including bytes that were already on disk
    private val networkBytes = AtomicLong(0)    // bytes received in this session, used for speed
    private val completed = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val active: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val tracker = SpeedTracker()
    @Volatile private var totalFiles = 0
    @Volatile private var totalBytes: Long? = null
    @Volatile private var lastFailures: List<FailedDownload> = emptyList()

    fun pause() {
        if (state != BatchState.RUNNING) return
        state = BatchState.PAUSED
        synchronized(tracker) { tracker.pause(clock()) }
        paused.value = true
        publish()
    }

    fun resume() {
        if (state != BatchState.PAUSED) return
        state = BatchState.RUNNING
        synchronized(tracker) { tracker.resume(clock()) }
        paused.value = false
        publish()
    }

    fun cancel() {
        cancelled = true
        paused.value = false
    }

    /** Re-runs only the files that failed in the previous batch. */
    suspend fun retryFailed(): BatchResult = run(lastFailures.map { it.request })

    suspend fun run(requests: List<DownloadRequest>): BatchResult {
        check(running.compareAndSet(false, true)) { "A download batch is already running" }
        try {
            return runBatch(requests.distinctBy { it.destination.absoluteFile })
        } finally {
            running.set(false)
        }
    }

    private suspend fun runBatch(requests: List<DownloadRequest>): BatchResult {
        cancelled = false
        paused.value = false
        downloaded.set(0)
        networkBytes.set(0)
        completed.set(0)
        failedCount.set(0)
        active.clear()
        synchronized(tracker) { tracker.reset() }
        totalFiles = requests.size
        totalBytes = if (requests.all { it.sizeBytes != null }) requests.sumOf { it.sizeBytes ?: 0L } else null

        storageShortfall(requests)?.let { reason ->
            val failures = requests.map { FailedDownload(it, reason) }
            failedCount.set(failures.size)
            lastFailures = failures
            state = BatchState.FAILED
            publish()
            return BatchResult(0, failures, cancelled = false)
        }

        state = BatchState.RUNNING
        publish()
        val permits = Semaphore(config.concurrency)
        val failures: List<FailedDownload> = try {
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        delay(config.progressIntervalMillis)
                        publish()
                    }
                }
                val results = requests.map { request ->
                    async(Dispatchers.IO) { permits.withPermit { process(request) } }
                }.awaitAll()
                ticker.cancel()
                results.filterNotNull()
            }
        } catch (e: CancellationException) {
            state = BatchState.CANCELLED
            publish()
            throw e
        }
        lastFailures = failures
        state = when {
            cancelled -> BatchState.CANCELLED
            failures.isNotEmpty() -> BatchState.FAILED
            else -> BatchState.COMPLETED
        }
        publish()
        return BatchResult(completed.get(), failures, cancelled)
    }

    // ------------------------------------------------------------------ per file

    private suspend fun process(request: DownloadRequest): FailedDownload? {
        if (cancelled) return null
        val counted = AtomicLong(0)
        active.add(request.destination.name)
        try {
            if (isAlreadyValid(request)) {
                sync(counted, request.destination.length())
                completed.incrementAndGet()
                return null
            }
            var attempt = 0
            while (true) {
                if (cancelled) return null
                awaitResumed()
                if (cancelled) return null
                try {
                    withContext(Dispatchers.IO) { downloadOnce(request, counted) }
                    completed.incrementAndGet()
                    return null
                } catch (e: PausedSignal) {
                    continue // pausing is not a failed attempt
                } catch (e: CancelledSignal) {
                    return null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    if (!isRetryable(e) || attempt >= config.maxAttempts) {
                        failedCount.incrementAndGet()
                        return FailedDownload(request, describe(e))
                    }
                    delay(backoffMillis(attempt))
                }
            }
        } finally {
            active.remove(request.destination.name)
        }
    }

    private suspend fun awaitResumed() {
        if (paused.value) paused.first { !it }
    }

    private fun isAlreadyValid(request: DownloadRequest): Boolean {
        val file = request.destination
        if (!file.isFile) return false
        if (request.sizeBytes != null && file.length() != request.sizeBytes) return false
        val hash = request.hash
        if (hash != null) return FileHasher.matches(file, hash)
        // Unknown size and no hash: all we can check is that the file is not empty.
        return request.sizeBytes != null || file.length() > 0
    }

    private fun downloadOnce(request: DownloadRequest, counted: AtomicLong) {
        val destination = request.destination
        destination.absoluteFile.parentFile?.mkdirs()
        val part = File(destination.path + ".part")
        var offset = if (part.isFile) part.length() else 0L
        val expectedSize = request.sizeBytes
        if (expectedSize != null && offset > expectedSize) {
            part.delete()
            offset = 0L
        }
        sync(counted, offset)

        if (expectedSize == null || offset != expectedSize) {
            val builder = Request.Builder().url(request.url)
            if (offset > 0) builder.header("Range", "bytes=$offset-")
            client.newCall(builder.build()).execute().use { response ->
                var append = false
                when {
                    response.code == 416 -> {
                        part.delete()
                        sync(counted, 0)
                        throw RetryableDownloadException("Range not satisfiable, restarting")
                    }
                    response.code == 206 -> {
                        val range = response.header("Content-Range")
                            ?.let { CONTENT_RANGE.matchEntire(it.trim()) }
                        val start = range?.groupValues?.get(1)?.toLongOrNull()
                        val end = range?.groupValues?.get(2)?.toLongOrNull()
                        val total = range?.groupValues?.get(3)?.takeUnless { it == "*" }?.toLongOrNull()
                        val bodyLength = response.body?.contentLength() ?: -1L
                        val validRange = start == offset &&
                            end != null && end >= offset &&
                            (total == null || end < total) &&
                            (expectedSize == null || total == expectedSize) &&
                            (bodyLength < 0L || bodyLength == end - offset + 1L)
                        if (!validRange) {
                            // Never append a response whose range cannot be proven to
                            // continue this exact checkpoint.
                            part.delete()
                            sync(counted, 0)
                            throw RetryableDownloadException("Invalid or inconsistent Content-Range; checkpoint reset")
                        }
                        append = true
                    }
                    response.isSuccessful -> {
                        // The server ignored Range (or none was sent): start from zero.
                        if (offset > 0) {
                            part.delete()
                            sync(counted, 0)
                        }
                    }
                    else -> throw HttpStatusException(response.code)
                }
                val body = response.body ?: throw IOException("Empty response body")
                body.byteStream().use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(config.bufferSize)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            counted.addAndGet(read.toLong())
                            downloaded.addAndGet(read.toLong())
                            networkBytes.addAndGet(read.toLong())
                            if (cancelled) throw CancelledSignal()
                            if (paused.value) throw PausedSignal()
                        }
                    }
                }
            }
        }
        finish(request, part, counted)
    }

    private fun finish(request: DownloadRequest, part: File, counted: AtomicLong) {
        val size = request.sizeBytes
        if (size != null && part.length() != size) {
            val actual = part.length()
            if (actual > size) {
                // An oversized partial cannot be safely resumed.
                part.delete()
                sync(counted, 0)
            } else {
                // Keep a short partial: the next attempt (or a later Retry) resumes
                // from this exact byte offset using HTTP Range instead of restarting.
                sync(counted, actual)
            }
            throw RetryableDownloadException("Incomplete download: expected $size bytes, received $actual; checkpoint preserved")
        }
        val hash = request.hash
        if (hash != null && !FileHasher.matches(part, hash)) {
            part.delete()
            sync(counted, 0)
            throw RetryableDownloadException("Checksum mismatch (${hash.algorithm.javaName})")
        }
        try {
            Files.move(part.toPath(), request.destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Files.move(part.toPath(), request.destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Makes this file's contribution to [downloaded] equal [target]. */
    private fun sync(counted: AtomicLong, target: Long) {
        val delta = target - counted.getAndSet(target)
        if (delta != 0L) downloaded.addAndGet(delta)
    }

    private fun isRetryable(e: Exception): Boolean = when {
        e is HttpStatusException -> e.code == 408 || e.code == 429 || e.code in 500..599
        e is IOException && e.message?.contains("No space left", ignoreCase = true) == true -> false
        e is IOException -> true
        else -> false
    }

    private fun describe(e: Exception): String = when {
        e is IOException && e.message?.contains("No space left", ignoreCase = true) == true -> "Not enough free storage"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponential = config.backoffBaseMillis * (1L shl (attempt - 1).coerceAtMost(20))
        val capped = exponential.coerceAtMost(config.backoffMaxMillis)
        return capped + if (capped > 0) Random.nextLong(capped / 4 + 1) else 0L
    }

    private fun storageShortfall(requests: List<DownloadRequest>): String? {
        val needed = requests.sumOf { request ->
            val size = request.sizeBytes ?: 0L
            val part = File(request.destination.path + ".part")
            val existing = when {
                request.destination.isFile -> request.destination.length()
                part.isFile -> part.length()
                else -> 0L
            }
            (size - existing).coerceAtLeast(0L)
        }
        if (needed <= 0L) return null
        var probe: File? = requests.first().destination.absoluteFile.parentFile
        while (probe != null && !probe.exists()) probe = probe.parentFile
        val available = probe?.let(usableSpace) ?: return null
        if (available >= needed + config.storageMarginBytes) return null
        return "Not enough free storage: need %.1f MB, %.1f MB available".format(
            needed / 1_048_576.0,
            available / 1_048_576.0,
        )
    }

    private fun publish() {
        val now = clock()
        val bytes = downloaded.get()
        val network = networkBytes.get()
        val current = state
        val speed: Long
        val average: Long
        synchronized(tracker) {
            if (current == BatchState.RUNNING) tracker.record(now, network)
            speed = tracker.currentSpeed()
            average = tracker.averageSpeed(now, network)
        }
        val total = totalBytes
        val remaining = total?.let { (it - bytes).coerceAtLeast(0L) }
        val live = current == BatchState.RUNNING
        _progress.value = DownloadProgress(
            state = current,
            totalFiles = totalFiles,
            completedFiles = completed.get(),
            failedFiles = failedCount.get(),
            downloadedBytes = bytes,
            totalBytes = total,
            activeFiles = active.sorted(),
            speedBytesPerSecond = if (live) speed else 0L,
            averageBytesPerSecond = average,
            etaSeconds = if (live) SpeedTracker.etaSeconds(remaining, speed) else null,
        )
    }
}
