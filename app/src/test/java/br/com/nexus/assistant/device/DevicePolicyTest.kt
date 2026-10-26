package br.com.nexus.assistant.device

import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile
import org.junit.Assert.*
import org.junit.Test

class DevicePolicyTest {
    private val gb = 1_000_000_000L
    private fun device() = DeviceCapabilities(
        sdkInt = 29, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"), process64Bit = true,
        processAbi = "arm64-v8a", totalRamBytes = 6 * gb, availableRamBytes = 2 * gb,
        lowRam = false, cpuCores = 8, freeDiskBytes = 8 * gb,
        hasFrontCamera = true, hasRearCamera = true, hasMicrophone = true, glesVersion = 0x30000,
    )

    @Test fun sixGbClassSelectsStrongerIncludedModelWithoutAssumingPhoneModel() {
        val policy = DevicePolicy.evaluate(device())
        assertTrue(policy.localAiSupported)
        assertEquals(LocalModelProfile.BALANCED, policy.recommendedProfile)
        assertEquals(2048, policy.contextTokens)
        assertEquals(3, policy.inferenceThreads)
        assertEquals(512, policy.maxOutputTokens)
        assertTrue(policy.handTrackingSupported)
        assertTrue(policy.supportsGles2)
    }

    @Test fun thirtyTwoBitKeepsCameraFeaturesButNeverRecommendsNativeLlm() {
        val hardware = device().copy(process64Bit = false, processAbi = "armeabi-v7a")
        val policy = DevicePolicy.evaluate(hardware)
        assertFalse(policy.localAiSupported)
        assertNull(policy.recommendedProfile)
        assertTrue(policy.handTrackingSupported)
        assertFalse(DevicePolicy.canRunProfile(hardware, LocalModelProfile.COMPACT))
    }

    @Test fun lowRamOrInsufficientFreeRamCannotBeOverriddenByManualProfile() {
        for (hardware in listOf(device().copy(lowRam = true), device().copy(availableRamBytes = gb - 1),
            device().copy(totalRamBytes = 3 * gb - 1))) {
            assertFalse(DevicePolicy.evaluate(hardware).localAiSupported)
            assertFalse(DevicePolicy.canRunProfile(hardware, LocalModelProfile.COMPACT))
            assertFalse(DevicePolicy.canRunProfile(hardware, LocalModelProfile.BALANCED))
        }
    }

    @Test fun modestHardwareUsesSmallContextAndSlowCameraCadence() {
        val hardware = device().copy(totalRamBytes = 3 * gb, availableRamBytes = gb, cpuCores = 2)
        val policy = DevicePolicy.evaluate(hardware)
        assertTrue(policy.localAiSupported)
        assertEquals(LocalModelProfile.COMPACT, policy.recommendedProfile)
        assertEquals(2048, policy.contextTokens)
        assertEquals(1, policy.inferenceThreads)
        assertEquals(384, policy.maxOutputTokens)
        assertEquals(320, policy.cameraWidth)
        assertEquals(240, policy.cameraHeight)
        assertEquals(220L, policy.analysisIntervalMs)
        assertEquals(1, policy.maxHands)
        assertFalse(DevicePolicy.canRunProfile(hardware, LocalModelProfile.BALANCED))
    }

    @Test fun strongestProfileStillCapsNativeMemoryAndCpuBudgets() {
        val policy = DevicePolicy.evaluate(device().copy(totalRamBytes = 12 * gb, availableRamBytes = 5 * gb, cpuCores = 16))
        assertEquals(LocalModelProfile.BALANCED, policy.recommendedProfile)
        assertEquals(4096, policy.contextTokens)
        assertEquals(3, policy.inferenceThreads)
        assertEquals(768, policy.maxOutputTokens)
        assertEquals(640, policy.cameraWidth)
        assertEquals(2, policy.maxHands)
    }

    @Test fun x86_64CanRunTextModelAndUpdatedMediaPipeNativeCore() {
        val policy = DevicePolicy.evaluate(device().copy(processAbi = "x86_64", supportedAbis = listOf("x86_64", "x86")))
        assertTrue(policy.localAiSupported)
        assertTrue(policy.handTrackingSupported)
    }

    @Test fun missingCameraOrGlesIsASeparateCapabilityFromAiAndMicrophone() {
        val hardware = device().copy(hasCamera = false, hasFrontCamera = false, hasRearCamera = false,
            hasMicrophone = false, glesVersion = 0x10000)
        val policy = DevicePolicy.evaluate(hardware)
        assertTrue(policy.localAiSupported)
        assertFalse(policy.handTrackingSupported)
        assertFalse(policy.supportsGles2)
        assertFalse(hardware.hasMicrophone)
    }

    @Test fun lowStorageChangesPreparationAvailabilityWithoutChangingTheBase() {
        val policy = DevicePolicy.evaluate(device().copy(freeDiskBytes = LocalModelCatalog.compact.bytes + 64L * 1024 * 1024))
        assertTrue(policy.localAiSupported)
        assertEquals(LocalModelProfile.COMPACT, policy.recommendedProfile)
        assertTrue(DevicePolicy.evaluate(device().copy(freeDiskBytes = 1)).reason.contains("armazenamento"))
    }

    @Test fun anInstalledBaseModelDoesNotRequireDuplicateStorageAtNextStartup() {
        val hardware = device().copy(freeDiskBytes = 0, installedModelProfiles = setOf(LocalModelProfile.COMPACT))
        assertEquals(LocalModelProfile.COMPACT, DevicePolicy.evaluate(hardware).recommendedProfile)
        assertEquals(0L, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.COMPACT))
        assertFalse(DevicePolicy.evaluate(hardware).reason.contains("falta armazenamento"))
    }

    @Test fun aCompletedBasePartialCanFinishVerificationEvenWithoutExtraFreeSpace() {
        val hardware = device().copy(freeDiskBytes = 0,
            downloadedModelBytes = mapOf(LocalModelProfile.COMPACT to LocalModelCatalog.compact.bytes))
        val policy = DevicePolicy.evaluate(hardware)
        assertTrue(policy.localAiSupported)
        assertEquals(LocalModelProfile.COMPACT, policy.recommendedProfile)
        assertEquals(0L, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.COMPACT))
        assertFalse(policy.reason.contains("falta armazenamento"))
    }

    @Test fun anInterruptedTransferRequiresOnlyItsMissingBytesAndSafetyMargin() {
        val missing = 1_048_576L
        val margin = 64L * 1024 * 1024
        val hardware = device().copy(freeDiskBytes = missing + margin,
            downloadedModelBytes = mapOf(LocalModelProfile.COMPACT to LocalModelCatalog.compact.bytes - missing))
        assertEquals(missing + margin, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.COMPACT))
        assertFalse(DevicePolicy.evaluate(hardware).reason.contains("falta armazenamento"))
        assertTrue(DevicePolicy.evaluate(hardware.copy(freeDiskBytes = missing + margin - 1)).reason.contains("falta armazenamento"))
    }

    @Test fun aCompleteBalancedPartialCanBeSelectedWithoutAdditionalStorage() {
        val hardware = device().copy(freeDiskBytes = 400_000_000,
            downloadedModelBytes = mapOf(LocalModelProfile.BALANCED to LocalModelCatalog.balanced.bytes))
        assertEquals(0L, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.BALANCED))
        assertTrue(DevicePolicy.canRunProfile(hardware, LocalModelProfile.BALANCED))
        assertEquals(LocalModelProfile.BALANCED, DevicePolicy.evaluate(hardware).recommendedProfile)
        assertEquals(LocalModelProfile.COMPACT,
            DevicePolicy.evaluate(hardware.copy(availableRamBytes = 1_100_000_000)).recommendedProfile)
    }

    @Test fun installedOrDownloadedLegacyBalancedDataNeverOverridesRamRequirements() {
        val hardware = device().copy(totalRamBytes = 3 * gb, availableRamBytes = gb, freeDiskBytes = 0,
            installedModelProfiles = setOf(LocalModelProfile.BALANCED),
            downloadedModelBytes = mapOf(LocalModelProfile.BALANCED to LocalModelCatalog.balanced.bytes))
        assertEquals(0L, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.BALANCED))
        assertFalse(DevicePolicy.canRunProfile(hardware, LocalModelProfile.BALANCED))
        assertTrue(DevicePolicy.canRunProfile(hardware, LocalModelProfile.COMPACT))
    }

    @Test fun malformedPartialMetadataCannotPretendTheModelHasAlreadyArrived() {
        val required = LocalModelCatalog.compact.bytes + 64L * 1024 * 1024
        for (invalid in listOf(-1L, LocalModelCatalog.compact.bytes + 1)) {
            val hardware = device().copy(freeDiskBytes = 1,
                downloadedModelBytes = mapOf(LocalModelProfile.COMPACT to invalid))
            assertEquals(required, DevicePolicy.requiredModelPreparationBytes(hardware, LocalModelProfile.COMPACT))
            assertTrue(DevicePolicy.evaluate(hardware).reason.contains("falta armazenamento"))
        }
    }

    @Test fun measuredFiveGbCapacitySupportsMarketedSixGbPhonesWithoutModelSpoofing() {
        val hardware = device().copy(totalRamBytes = 5_300_000_000L, availableRamBytes = 1_900_000_000L)
        assertEquals(LocalModelProfile.BALANCED, DevicePolicy.evaluate(hardware).recommendedProfile)
        assertTrue(DevicePolicy.canRunProfile(hardware, LocalModelProfile.BALANCED))
        assertEquals(LocalModelProfile.COMPACT,
            DevicePolicy.evaluate(hardware.copy(availableRamBytes = 1_799_999_999L)).recommendedProfile)
        assertFalse(DevicePolicy.canRunProfile(hardware.copy(availableRamBytes = 1_799_999_999L), LocalModelProfile.BALANCED))
    }

    @Test fun strongerModelFallsBackToCompactWhenOnlyCompactPreparationFits() {
        val hardware = device().copy(freeDiskBytes = LocalModelCatalog.compact.bytes + 64L * 1024 * 1024)
        val policy = DevicePolicy.evaluate(hardware)
        assertEquals(LocalModelProfile.COMPACT, policy.recommendedProfile)
        assertTrue(policy.reason.contains("qualidade limitada"))
    }

    @Test fun unsupportedAndroidAndUnknownAbiNeverEnableLocalInference() {
        assertFalse(DevicePolicy.evaluate(device().copy(sdkInt = 25)).localAiSupported)
        assertFalse(DevicePolicy.evaluate(device().copy(processAbi = "unknown64")).localAiSupported)
    }
}
