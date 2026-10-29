package br.com.nexus.assistant.device

/** Image data budget based on measured memory, never a handset model name. */
data class ImagePreparationPolicy(val maxEdge: Int, val maxOutputBytes: Long, val jpegQuality: Int) {
    companion object {
        fun forDevice(device: DeviceCapabilities): ImagePreparationPolicy =
            if (device.lowRam || device.totalRamBytes < 4_000_000_000L || device.availableRamBytes < 1_000_000_000L) {
                ImagePreparationPolicy(960, 1024L * 1024, 82)
            } else ImagePreparationPolicy(1280, 2L * 1024 * 1024, 85)
    }
}
