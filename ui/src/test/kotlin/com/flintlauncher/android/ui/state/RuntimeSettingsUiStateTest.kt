package com.flintlauncher.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSettingsUiStateTest {
    @Test
    fun recommendsConservativeHeapForLowMemoryDevices() {
        val state = RuntimeSettingsUiState(totalMemoryMb = 2_800)
        assertEquals(512, state.recommendedMemoryMb)
    }

    @Test
    fun reservesMemoryForAndroidOnArm64() {
        val state = RuntimeSettingsUiState(totalMemoryMb = 3_072, processArch = "arm64")
        assertEquals(2_304, state.maxHeapMb)
    }

    @Test
    fun capsHeapOn32BitProcesses() {
        val state = RuntimeSettingsUiState(totalMemoryMb = 6_000, processArch = "arm")
        assertEquals(1_024, state.maxHeapMb)
    }

    @Test
    fun keepsHeapBoundsPositiveWhenMemoryProbeIsUnavailable() {
        val state = RuntimeSettingsUiState(totalMemoryMb = 0)
        assertEquals(768, state.recommendedMemoryMb)
        assertEquals(2_048, state.maxHeapMb)
    }
}
