package br.com.nexus.assistant.device

import android.content.Context
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile

/** Conservative resource policy. Evaluation is pure and independently covered by JVM tests. */
data class DevicePolicy(
    val localAiSupported: Boolean,
    val recommendedProfile: LocalModelProfile?,
    val reason: String,
    val contextTokens: Int,
    val inferenceThreads: Int,
    val maxOutputTokens: Int,
    val cameraWidth: Int,
    val cameraHeight: Int,
    val analysisIntervalMs: Long,
    val maxHands: Int,
    val supportsGles2: Boolean,
    val handTrackingSupported: Boolean,
) {
    companion object {
        private const val GB = 1_000_000_000L
        private const val DOWNLOAD_MARGIN = 64L * 1024 * 1024

        fun detect(context: Context): DevicePolicy = evaluate(DeviceCapabilities.read(context))

        fun evaluate(device: DeviceCapabilities): DevicePolicy {
            val abiSupported = device.process64Bit && device.processAbi in setOf("arm64-v8a", "x86_64")
            val compactRam = device.totalRamBytes >= 3 * GB && device.availableRamBytes >= GB
            val compactFits = device.freeDiskBytes >= requiredModelPreparationBytes(device, LocalModelProfile.COMPACT)
            val localSupported = device.sdkInt >= 26 && abiSupported && !device.lowRam && compactRam
            // One base model remains stable across launches. Extra resources
            // increase its runtime budget instead of selecting another download.
            val selected = if (localSupported) LocalModelProfile.COMPACT else null
            val strong = device.totalRamBytes >= 8 * GB && device.availableRamBytes >= 3 * GB && !device.lowRam
            val medium = device.totalRamBytes >= 4 * GB && device.availableRamBytes >= 1_500_000_000 && !device.lowRam
            val reason = when {
                device.sdkInt < 26 -> "Esta versão do app requer Android 8.0 ou mais recente."
                !abiSupported -> "O app funciona neste processador; a IA local deste APK exige um processo Android de 64 bits (ARM64 ou x86_64). Use texto, voz e um provedor opcional."
                device.lowRam -> "Android identificou um aparelho de pouca memória. A IA local foi desativada para preservar o funcionamento do app."
                !compactRam -> "Não há RAM suficiente disponível para carregar a IA local com a margem de segurança. Feche outros apps e reabra o NEXUS, ou use um provedor opcional."
                !compactFits -> "IA local compatível, mas falta armazenamento livre para preparar os dados da IA neste aparelho. Libere espaço e tente novamente."
                else -> "O NEXUS usa a mesma IA base compacta e ajusta o processamento à memória e à CPU deste aparelho. O modelo pode errar respostas; confira os resultados."
            }
            return DevicePolicy(
                localAiSupported = localSupported,
                recommendedProfile = selected,
                reason = reason,
                contextTokens = when { strong -> 1536; medium -> 1024; else -> 512 },
                inferenceThreads = when { device.cpuCores <= 2 || !medium -> 1; device.cpuCores <= 4 -> 2; else -> 3 },
                maxOutputTokens = when { strong -> 192; medium -> 128; else -> 96 },
                cameraWidth = when { strong -> 640; medium -> 480; else -> 320 },
                cameraHeight = when { strong -> 480; medium -> 360; else -> 240 },
                analysisIntervalMs = when { strong -> 110; medium -> 160; else -> 220 },
                maxHands = if (medium) 2 else 1,
                supportsGles2 = device.glesVersion >= 0x20000,
                // MediaPipe Tasks 0.10.35 supplies the native core on all four packaged ABIs.
                handTrackingSupported = device.hasCamera && device.processAbi in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
            )
        }

        /** Full private files need verification, not a second copy's worth of disk space. */
        fun requiredModelPreparationBytes(device: DeviceCapabilities, profile: LocalModelProfile): Long {
            if (profile in device.installedModelProfiles) return 0
            val expected = LocalModelCatalog.forProfile(profile).bytes
            val existing = device.downloadedModelBytes[profile]?.takeIf { it in 0..expected } ?: 0L
            if (existing == expected) return 0
            return expected - existing + DOWNLOAD_MARGIN
        }

        /** A manual model selection never overrides native ABI or minimum RAM requirements. */
        fun canRunProfile(device: DeviceCapabilities, profile: LocalModelProfile): Boolean {
            val policy = evaluate(device)
            if (!policy.localAiSupported) return false
            return profile != LocalModelProfile.BALANCED || device.totalRamBytes >= 6 * GB && device.availableRamBytes >= 2 * GB
        }
    }
}
