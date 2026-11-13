package com.flintlauncher.android

import com.flintlauncher.android.download.BatchState
import com.flintlauncher.android.download.DownloadProgress
import com.flintlauncher.android.download.DownloadRequest
import com.flintlauncher.android.download.FailedDownload
import com.flintlauncher.android.installer.InstallResult
import com.flintlauncher.android.installer.InstallStage
import com.flintlauncher.android.installer.InstalledVersion
import com.flintlauncher.android.minecraft.version.VersionJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeControls : DownloadControls {
    override val progress = MutableStateFlow(DownloadProgress())
    var paused = 0
    var resumed = 0
    var cancelled = 0
    override fun pause() { paused++ }
    override fun resume() { resumed++ }
    override fun cancel() { cancelled++ }
}

private class FakeRunner : InstallRunner {
    val gate = CompletableDeferred<InstallResult>()
    var calls = 0
    override suspend fun install(versionId: String, onStage: (InstallStage) -> Unit): InstallResult {
        calls++
        onStage(InstallStage.RESOLVING_METADATA)
        onStage(InstallStage.DOWNLOADING_ASSETS)
        return gate.await()
    }
}

private class MemoryPending : PendingInstallStore {
    var value: String? = null
    override fun save(versionId: String) { value = versionId }
    override fun load(): String? = value
    override fun clear() { value = null }
}

private fun installed(id: String = "1.0") = InstalledVersion(
    versionId = id, version = VersionJson(id = id), clientJar = File("client.jar"), classpath = emptyList(),
    nativeJars = emptyList(), assetsRoot = File("assets"), assetIndexId = "idx", loggingJvmArgument = null, requiredJavaMajor = 21,
)

private fun failure(name: String, reason: String) =
    FailedDownload(DownloadRequest(name, "https://x.invalid/$name", File("/tmp/$name")), reason)

class InstallCoordinatorTest {
    private val runner = FakeRunner()
    private val controls = FakeControls()
    private val pending = MemoryPending()
    private val coordinator = InstallCoordinator(runner, controls, pending, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun startingMarksRunningRemembersTheInstallAndReportsStages() {
        assertTrue(coordinator.start("1.0"))

        val state = coordinator.state.value
        assertEquals(InstallState.Phase.RUNNING, state.phase)
        assertEquals("1.0", state.versionId)
        assertEquals(InstallStage.DOWNLOADING_ASSETS, state.stage)
        assertEquals("1.0", pending.value)
        assertTrue(coordinator.isRunning)
    }

    @Test
    fun successClearsThePendingMarker() {
        coordinator.start("1.0")
        runner.gate.complete(InstallResult.Success(installed()))

        assertEquals(InstallState.Phase.SUCCEEDED, coordinator.state.value.phase)
        assertNull(pending.value)
        assertFalse(coordinator.isRunning)
    }

    @Test
    fun aSingleFailedFileIsNamedInTheMessageAndTheInstallStaysResumable() {
        coordinator.start("1.0")
        runner.gate.complete(InstallResult.Failed("1 file(s) failed", listOf(failure("lib.jar", "HTTP 404"))))

        val state = coordinator.state.value
        assertEquals(InstallState.Phase.FAILED, state.phase)
        assertEquals("lib.jar: HTTP 404", state.message)
        assertEquals("1.0", pending.value)
    }

    @Test
    fun manyFilesWithTheSameReasonAreSummarised() {
        coordinator.start("1.0")
        val reason = "Not enough free storage: need 400.0 MB, 12.0 MB available"
        runner.gate.complete(InstallResult.Failed("3 file(s) failed", listOf(failure("a", reason), failure("b", reason), failure("c", reason))))

        assertEquals("$reason (3 files)", coordinator.state.value.message)
    }

    @Test
    fun aFailureWithoutFileDetailsUsesTheMessage() {
        coordinator.start("1.0")
        runner.gate.complete(InstallResult.Failed("Unknown Minecraft version: x"))

        assertEquals("Unknown Minecraft version: x", coordinator.state.value.message)
    }

    @Test
    fun cancelStopsTheInstallAndForgetsIt() {
        coordinator.start("1.0")

        coordinator.cancel()

        assertEquals(InstallState.Phase.CANCELLED, coordinator.state.value.phase)
        assertEquals(1, controls.cancelled)
        assertNull(pending.value)
        assertFalse(coordinator.isRunning)
    }

    @Test
    fun aRunnerReportingCancelledIsShownAsCancelled() {
        coordinator.start("1.0")
        runner.gate.complete(InstallResult.Cancelled)

        assertEquals(InstallState.Phase.CANCELLED, coordinator.state.value.phase)
    }

    @Test
    fun aSecondInstallIsRefusedWhileOneRuns() {
        assertTrue(coordinator.start("1.0"))
        assertFalse(coordinator.start("1.1"))

        assertEquals(1, runner.calls)
        assertEquals("1.0", coordinator.state.value.versionId)
    }

    @Test
    fun progressIsMirroredWhileRunningAndKeptAtTheEnd() {
        coordinator.start("1.0")

        controls.progress.value = DownloadProgress(state = BatchState.RUNNING, downloadedBytes = 5, totalBytes = 10)
        assertEquals(5L, coordinator.state.value.progress.downloadedBytes)

        controls.progress.value = DownloadProgress(state = BatchState.COMPLETED, downloadedBytes = 10, totalBytes = 10)
        runner.gate.complete(InstallResult.Success(installed()))

        assertEquals(10L, coordinator.state.value.progress.downloadedBytes)
        assertEquals(InstallState.Phase.SUCCEEDED, coordinator.state.value.phase)
    }

    @Test
    fun pauseAndResumeAreForwardedToTheDownloadManager() {
        coordinator.pause()
        coordinator.resume()

        assertEquals(1, controls.paused)
        assertEquals(1, controls.resumed)
    }

    @Test
    fun dismissClearsAFinishedResultButNotARunningInstall() {
        coordinator.start("1.0")
        coordinator.dismiss()
        assertEquals(InstallState.Phase.RUNNING, coordinator.state.value.phase)

        runner.gate.complete(InstallResult.Failed("boom"))
        coordinator.dismiss()

        assertEquals(InstallState.Phase.IDLE, coordinator.state.value.phase)
        assertNull(pending.value)
    }

    @Test
    fun anInterruptedInstallIsOfferedOnlyWhenNothingIsRunning() {
        pending.value = "1.2"
        assertEquals("1.2", coordinator.interruptedVersion())

        coordinator.start("1.2")
        assertNull(coordinator.interruptedVersion())
    }
}

class FilePendingInstallStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun savesLoadsAndClears() {
        val store = FilePendingInstallStore(File(tmp.root, "sub/pending.txt"))
        assertNull(store.load())

        store.save("1.21.11")
        assertEquals("1.21.11", store.load())

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun anEmptyFileMeansNothingPending() {
        val file = File(tmp.root, "pending.txt")
        file.writeText("  \n")
        assertNull(FilePendingInstallStore(file).load())
    }
}
