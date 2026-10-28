package br.com.nexus.assistant.device

/** User choices are independent from the optional offline-language-model profile. */
enum class TrackingProfile(val label: String) {
    AUTO("Automático"),
    ECONOMY("Econômico"),
    BALANCED("Equilibrado"),
    PERFORMANCE("Alto desempenho");

    companion object {
        /** A preference from an older/newer version cannot prevent camera startup. */
        fun fromStored(value: String?): TrackingProfile = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

data class TrackingConfiguration(
    val cameraWidth: Int,
    val cameraHeight: Int,
    /** Target spacing, not an assertion that the device delivers this frame rate. */
    val analysisIntervalMs: Long,
    /** Requested landmark count; never reduced to one solely because RAM is modest. */
    val maxHands: Int,
    val profile: TrackingProfile,
    val requestedProfile: TrackingProfile,
    val reason: String,
)

/**
 * Bounded starting budgets based on a permission-free hardware snapshot. Inference
 * cadence still adapts to measured processing cost. These are tuning defaults,
 * not benchmarks or a claim about any phone sold under a particular model name.
 */
object TrackingProfiles {
    private const val GB = 1_000_000_000L
    private const val PRESSURE_BYTES = 512L * 1024 * 1024

    fun resolve(
        device: DeviceCapabilities,
        profile: TrackingProfile = TrackingProfile.AUTO,
        requestedHands: Int = 1,
        pointerMode: Boolean = false,
    ): TrackingConfiguration {
        val memoryPressure = device.availableRamBytes < PRESSURE_BYTES
        val automatic = when {
            device.lowRam || device.totalRamBytes < 4 * GB || device.availableRamBytes < GB || device.cpuCores <= 2 -> TrackingProfile.ECONOMY
            device.totalRamBytes >= 8 * GB && device.availableRamBytes >= 3 * GB && device.cpuCores >= 6 -> TrackingProfile.PERFORMANCE
            else -> TrackingProfile.BALANCED
        }
        // Manual performance selection never requests larger frames while the OS
        // reports less than 512 MiB available. It still preserves two-hand input.
        val effective = when {
            memoryPressure -> TrackingProfile.ECONOMY
            profile == TrackingProfile.AUTO -> automatic
            else -> profile
        }
        val hands = requestedHands.coerceIn(1, 2)
        val twoHands = hands == 2
        val width = when (effective) {
            TrackingProfile.PERFORMANCE -> if (twoHands) 480 else 640
            TrackingProfile.BALANCED -> if (twoHands) 320 else 480
            else -> 320
        }
        val spacing = when {
            twoHands -> when (effective) {
                TrackingProfile.PERFORMANCE -> 110L
                TrackingProfile.BALANCED -> 150L
                else -> 200L
            }
            pointerMode -> when (effective) {
                TrackingProfile.PERFORMANCE -> 50L
                TrackingProfile.BALANCED -> 65L
                else -> 100L
            }
            else -> when (effective) {
                TrackingProfile.PERFORMANCE -> 80L
                TrackingProfile.BALANCED -> 120L
                else -> 180L
            }
        }
        return TrackingConfiguration(
            cameraWidth = width,
            cameraHeight = width * 3 / 4,
            analysisIntervalMs = spacing,
            maxHands = hands,
            profile = effective,
            requestedProfile = profile,
            reason = when {
                memoryPressure -> "Pouca RAM disponível agora: resolução e frequência econômicas; a quantidade de mãos solicitada foi preservada."
                profile == TrackingProfile.AUTO -> "Perfil ${effective.label.lowercase()} escolhido pela RAM disponível, classificação do Android e núcleos de CPU."
                else -> "Perfil ${effective.label.lowercase()} escolhido pelo usuário. A frequência real depende do tempo medido pelo rastreador."
            },
        )
    }
}
