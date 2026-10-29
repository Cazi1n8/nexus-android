package br.com.nexus.assistant.device

import org.junit.Assert.*
import org.junit.Test

class ImagePreparationPolicyTest {
    private val device = DeviceCapabilities(35, listOf("arm64-v8a"), true, "arm64-v8a", 6_000_000_000L,
        2_000_000_000L, false, 8, 10_000_000_000L, true, true, true, 0x30000)

    @Test fun memoryPressureReducesDecodedPixelAndNetworkBudgetsWithoutModelNames() {
        val full = ImagePreparationPolicy.forDevice(device)
        for (limited in listOf(device.copy(lowRam = true), device.copy(totalRamBytes = 3_000_000_000L),
            device.copy(availableRamBytes = 999_999_999L))) {
            val reduced = ImagePreparationPolicy.forDevice(limited)
            assertEquals(960, reduced.maxEdge)
            assertTrue(reduced.maxEdge * reduced.maxEdge * 4 < full.maxEdge * full.maxEdge * 4)
            assertEquals(1024L * 1024, reduced.maxOutputBytes)
        }
        assertEquals(1280, full.maxEdge)
        assertEquals(full, ImagePreparationPolicy.forDevice(device.copy(process64Bit = false, processAbi = "armeabi-v7a")))
    }
}
