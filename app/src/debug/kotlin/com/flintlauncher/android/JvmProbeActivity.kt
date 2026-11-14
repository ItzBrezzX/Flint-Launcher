package com.flintlauncher.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.flintlauncher.android.runtime.MobileJreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only device gate. Runs inside the isolated :game process so HotSpot is
 * never loaded into the launcher UI process. It downloads a pinned mobile JRE,
 * executes a real Java class through JNI/JLI, and emits a machine-readable result.
 */
class JvmProbeActivity : Activity() {
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = (application as? FlintApplication)?.container?.mobileJreManager
        val major = intent.getIntExtra(EXTRA_JAVA_MAJOR, DEFAULT_JAVA_MAJOR)
        if (manager == null) {
            fail("No supported mobile JRE manager for this app process ABI")
            return
        }

        probeScope.launch {
            try {
                val runtime = manager.install(major) { stage, progress ->
                    val detail = progress?.let {
                        " bytes=${it.downloadedBytes}/${it.totalBytes ?: -1}"
                    }.orEmpty()
                    Log.i(TAG, "stage=$stage$detail")
                }.getOrElse { throw it }

                Log.i(TAG, "runtime-installed major=${runtime.majorVersion} arch=${runtime.arch}")
                if (!manager.verify(runtime)) {
                    throw IllegalStateException(manager.lastVerificationError ?: "JVM execution probe failed")
                }

                Log.i(TAG, "FLINT_JVM_OK java=${runtime.majorVersion} arch=${runtime.arch}")
            } catch (error: Throwable) {
                Log.e(TAG, "FLINT_JVM_FAILED java=$major: ${error.message}", error)
            } finally {
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    private fun fail(reason: String) {
        Log.e(TAG, "FLINT_JVM_FAILED: $reason")
        finish()
    }

    private companion object {
        const val TAG = "FlintJvmProbe"
        const val EXTRA_JAVA_MAJOR = "java-major"
        const val DEFAULT_JAVA_MAJOR = 17
    }
}
