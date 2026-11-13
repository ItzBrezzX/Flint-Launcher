package com.flintlauncher.android.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeedTrackerTest {
    @Test
    fun currentSpeedUsesTheSlidingWindow() {
        val tracker = SpeedTracker(windowMillis = 2_000)
        tracker.record(0, 0)
        tracker.record(1_000, 1_000_000)
        tracker.record(2_000, 2_000_000)
        assertEquals(1_000_000L, tracker.currentSpeed())

        // the fast start falls out of the window; only the last two seconds count
        tracker.record(3_000, 2_500_000)
        tracker.record(4_000, 3_000_000)
        assertEquals(500_000L, tracker.currentSpeed())
    }

    @Test
    fun averageExcludesPausedTime() {
        val tracker = SpeedTracker()
        tracker.record(0, 0)
        tracker.record(1_000, 1_000)
        tracker.pause(1_000)
        tracker.resume(11_000) // ten idle seconds
        tracker.record(12_000, 2_000)
        // 2000 bytes over 12s minus 10s paused = 2s
        assertEquals(1_000L, tracker.averageSpeed(12_000, 2_000))
    }

    @Test
    fun resumingDoesNotDragCurrentSpeedDown() {
        val tracker = SpeedTracker()
        tracker.record(0, 0)
        tracker.record(1_000, 1_000)
        tracker.pause(1_000)
        tracker.resume(60_000)
        assertEquals(0L, tracker.currentSpeed()) // no samples yet after resume
    }

    @Test
    fun etaRoundsUpAndIsNullWhenUnknown() {
        assertEquals(43L, SpeedTracker.etaSeconds(remainingBytes = 3_600_000, speedBytesPerSecond = 85_000))
        assertNull(SpeedTracker.etaSeconds(remainingBytes = null, speedBytesPerSecond = 1_000))
        assertNull(SpeedTracker.etaSeconds(remainingBytes = 1_000, speedBytesPerSecond = 0))
    }
}

class FileQueueStoreTest {
    @Test
    fun roundTripsRequestsIncludingHashes() {
        val dir = Files.createTempDirectory("flint-queue").toFile()
        val store = FileQueueStore(File(dir, "queue.json"))
        val requests = listOf(
            DownloadRequest("a", "https://example.invalid/a", File(dir, "a.jar"), 10, ExpectedHash(HashAlgorithm.SHA1, "ab".repeat(20))),
            DownloadRequest("b", "https://example.invalid/b", File(dir, "b.jar")),
        )

        store.save(requests)

        assertEquals(requests, store.load())
        store.clear()
        assertEquals(emptyList<DownloadRequest>(), store.load())
    }

    @Test
    fun corruptOrMissingStoreIsAnEmptyQueue() {
        val dir = Files.createTempDirectory("flint-queue").toFile()
        val file = File(dir, "queue.json")
        assertEquals(emptyList<DownloadRequest>(), FileQueueStore(file).load())
        file.writeText("{not json")
        assertEquals(emptyList<DownloadRequest>(), FileQueueStore(file).load())
    }
}
