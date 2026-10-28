package br.com.nexus.assistant.device

import org.junit.Assert.*
import org.junit.Test

class TrackingProfilesTest {
    private val gb = 1_000_000_000L
    private fun device() = DeviceCapabilities(
        sdkInt = 29, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"), process64Bit = true,
        processAbi = "arm64-v8a", totalRamBytes = 4 * gb, availableRamBytes = 1_500_000_000L,
        lowRam = false, cpuCores = 8, freeDiskBytes = 8 * gb,
        hasFrontCamera = true, hasRearCamera = true, hasMicrophone = true, glesVersion = 0x30000,
    )

    @Test fun automaticUsesActualMemoryAndCpuRatherThanPhoneNames() {
        assertEquals(TrackingProfile.BALANCED, TrackingProfiles.resolve(device()).profile)
        assertEquals(TrackingProfile.ECONOMY,
            TrackingProfiles.resolve(device().copy(totalRamBytes = 3 * gb)).profile)
        assertEquals(TrackingProfile.ECONOMY,
            TrackingProfiles.resolve(device().copy(lowRam = true)).profile)
        assertEquals(TrackingProfile.ECONOMY,
            TrackingProfiles.resolve(device().copy(cpuCores = 2)).profile)
        assertEquals(TrackingProfile.PERFORMANCE,
            TrackingProfiles.resolve(device().copy(totalRamBytes = 8 * gb, availableRamBytes = 3 * gb)).profile)
    }

    @Test fun twoHandsStayEnabledOnModestHardwareWithSmallerBudgets() {
        val modest = device().copy(totalRamBytes = 2 * gb, availableRamBytes = 700_000_000L, lowRam = true)
        val config = TrackingProfiles.resolve(modest, requestedHands = 2)
        assertEquals(2, config.maxHands)
        assertEquals(320, config.cameraWidth)
        assertEquals(240, config.cameraHeight)
        assertEquals(200L, config.analysisIntervalMs)
        assertEquals(2, DevicePolicy.evaluate(modest).maxHands)
    }

    @Test fun twoHandsReduceResolutionBeforeAnyRestrictionOfHandCount() {
        val single = TrackingProfiles.resolve(device(), TrackingProfile.PERFORMANCE)
        val pair = TrackingProfiles.resolve(device(), TrackingProfile.PERFORMANCE, requestedHands = 2)
        assertEquals(640, single.cameraWidth)
        assertEquals(480, pair.cameraWidth)
        assertEquals(2, pair.maxHands)
        assertTrue(pair.analysisIntervalMs > single.analysisIntervalMs)
    }

    @Test fun explicitProfilesAreRespectedWhenMemoryHeadroomAllows() {
        for (profile in listOf(TrackingProfile.ECONOMY, TrackingProfile.BALANCED, TrackingProfile.PERFORMANCE)) {
            val result = TrackingProfiles.resolve(device(), profile)
            assertEquals(profile, result.profile)
            assertEquals(profile, result.requestedProfile)
        }
    }

    @Test fun memoryPressureReducesCostWithoutSilentlyRemovingSecondHand() {
        val result = TrackingProfiles.resolve(device().copy(availableRamBytes = 128L * 1024 * 1024),
            TrackingProfile.PERFORMANCE, requestedHands = 2)
        assertEquals(TrackingProfile.PERFORMANCE, result.requestedProfile)
        assertEquals(TrackingProfile.ECONOMY, result.profile)
        assertEquals(2, result.maxHands)
        assertTrue(result.reason.contains("Pouca RAM"))
    }

    @Test fun pointerPacingPrioritizesMovementWithoutClaimingMeasuredFps() {
        for (profile in listOf(TrackingProfile.ECONOMY, TrackingProfile.BALANCED, TrackingProfile.PERFORMANCE)) {
            val pointer = TrackingProfiles.resolve(device(), profile, pointerMode = true)
            val practice = TrackingProfiles.resolve(device(), profile)
            assertTrue(pointer.analysisIntervalMs < practice.analysisIntervalMs)
            assertTrue(pointer.analysisIntervalMs >= 50L)
            assertEquals(1, pointer.maxHands)
        }
    }

    @Test fun dimensionsAndHandCountsStayWithinSupportedBounds() {
        for (hands in listOf(Int.MIN_VALUE, 0, 1, 2, Int.MAX_VALUE)) {
            for (profile in TrackingProfile.entries) {
                val result = TrackingProfiles.resolve(device(), profile, requestedHands = hands)
                assertTrue(result.maxHands in 1..2)
                assertTrue(result.cameraWidth in 320..640)
                assertEquals(result.cameraWidth * 3 / 4, result.cameraHeight)
                assertTrue(result.analysisIntervalMs in 50L..200L)
            }
        }
    }

    @Test fun corruptedOrNewPreferenceUsesAutomaticRatherThanCrashing() {
        assertEquals(TrackingProfile.AUTO, TrackingProfile.fromStored(null))
        assertEquals(TrackingProfile.AUTO, TrackingProfile.fromStored("unknown-profile"))
        for (profile in TrackingProfile.entries) assertEquals(profile, TrackingProfile.fromStored(profile.name))
    }

    @Test fun thirtyTwoBitTrackingKeepsTheSameTwoHandConfiguration() {
        val hardware = device().copy(process64Bit = false, processAbi = "armeabi-v7a")
        assertEquals(2, TrackingProfiles.resolve(hardware, requestedHands = 2).maxHands)
        assertFalse(DevicePolicy.evaluate(hardware).localAiSupported)
        assertTrue(DevicePolicy.evaluate(hardware).handTrackingSupported)
    }
}
