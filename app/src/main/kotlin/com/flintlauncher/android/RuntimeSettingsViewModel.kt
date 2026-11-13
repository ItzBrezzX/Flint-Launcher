package com.flintlauncher.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintlauncher.android.runtime.KnownJreArtifacts
import com.flintlauncher.android.runtime.MobileJreManager
import com.flintlauncher.android.runtime.RuntimeSmoke
import com.flintlauncher.android.ui.state.RuntimeEntryUi
import com.flintlauncher.android.ui.state.RuntimeSettingsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeSettingsViewModel(
    private val runtimeManager: MobileJreManager?,
    private val deviceArch: String?,
    deviceModel: String,
    androidVersion: String,
    supportedAbis: List<String>,
    totalMemoryMb: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RuntimeSettingsUiState(
            deviceModel = deviceModel.ifBlank { "Unknown device" },
            androidVersion = androidVersion.ifBlank { "Unknown" },
            processArch = deviceArch,
            supportedAbis = supportedAbis,
            totalMemoryMb = totalMemoryMb,
            availableJavaMajors = deviceArch?.let(KnownJreArtifacts::majorsFor).orEmpty(),
        ),
    )
    val state: StateFlow<RuntimeSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val result = try {
                withContext(Dispatchers.IO) {
                    val runtimes = runtimeManager?.listInstalled().orEmpty().map { runtime ->
                        RuntimeEntryUi(
                            id = runtime.id,
                            major = runtime.majorVersion,
                            arch = runtime.arch,
                            vendor = runtime.vendor,
                            verified = runtime.verified,
                        )
                    }
                    val gpuProbe = RuntimeSmoke.run().eglCapabilities
                    runtimes to gpuProbe
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = "Could not refresh runtime diagnostics: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    installedRuntimes = result.first,
                    rendererDiagnostics = result.second,
                    loading = false,
                )
            }
        }
    }

    fun installJava(major: Int) {
        val manager = runtimeManager
        if (manager == null) {
            _state.update { it.copy(message = "This Android process ABI is not supported by the available runtime catalog.") }
            return
        }
        if (major !in _state.value.availableJavaMajors) {
            _state.update { it.copy(message = "Java $major is not published for this device architecture.") }
            return
        }
        if (_state.value.installingMajor != null) return

        viewModelScope.launch {
            _state.update { it.copy(installingMajor = major, message = "Downloading and verifying Java $major…") }
            val result = try {
                manager.install(major)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { runtime ->
                    _state.update {
                        it.copy(
                            installingMajor = null,
                            message = "Java " + runtime.majorVersion + " (" + runtime.arch + ") installed and verified.",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            installingMajor = null,
                            message = "Java $major installation failed: " +
                                (error.message ?: error.javaClass.simpleName),
                        )
                    }
                },
            )
            refresh()
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
}
