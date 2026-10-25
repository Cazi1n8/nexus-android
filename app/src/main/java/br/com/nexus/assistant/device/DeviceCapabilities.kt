package br.com.nexus.assistant.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import br.com.nexus.assistant.data.local.LocalModelCatalog
import br.com.nexus.assistant.data.local.LocalModelProfile
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** File lengths are candidates for preparation, never proof of model integrity. */
internal data class LocalModelFileInventory(
    val installedProfiles: Set<LocalModelProfile>,
    val downloadedBytes: Map<LocalModelProfile, Long>,
)

/** Reads metadata only. A complete .part remains unverified until the manager checks SHA-256. */
internal fun readLocalModelFileInventory(directory: File): LocalModelFileInventory {
    val installed = mutableSetOf<LocalModelProfile>()
    val downloaded = mutableMapOf<LocalModelProfile, Long>()
    for (profile in LocalModelProfile.entries) {
        val spec = LocalModelCatalog.forProfile(profile)
        val finalFile = File(directory, spec.fileName)
        if (Files.isRegularFile(finalFile.toPath(), LinkOption.NOFOLLOW_LINKS) && finalFile.length() == spec.bytes) {
            installed += profile
            downloaded[profile] = spec.bytes
        } else {
            val partial = File(directory, "${spec.fileName}.part")
            if (Files.isRegularFile(partial.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                val bytes = partial.length()
                if (bytes in 1..spec.bytes) downloaded[profile] = bytes
            }
        }
    }
    return LocalModelFileInventory(installed.toSet(), downloaded.toMap())
}

/** Hardware/OS observations, not a promise that a permission or a physical sensor test passed. */
data class DeviceCapabilities(
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val process64Bit: Boolean,
    val processAbi: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowRam: Boolean,
    val cpuCores: Int,
    val freeDiskBytes: Long,
    val hasFrontCamera: Boolean,
    val hasRearCamera: Boolean,
    val hasMicrophone: Boolean,
    val glesVersion: Int,
    val hasCamera: Boolean = hasFrontCamera || hasRearCamera,
    /** Candidates only; LocalModelManager must still verify SHA-256 before inference. */
    val installedModelProfiles: Set<LocalModelProfile> = emptySet(),
    /** Bounded private final/partial bytes already on disk; not a READY flag. */
    val downloadedModelBytes: Map<LocalModelProfile, Long> = emptyMap(),
) {
    companion object {
        /** Cheap, permission-free snapshot; does not start a camera, microphone or native engine. */
        fun read(context: Context): DeviceCapabilities {
            val app = context.applicationContext
            val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(memory)
            val packages = app.packageManager
            val is64 = Process.is64Bit()
            val processAbis = if (is64) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
            val modelInventory = readLocalModelFileInventory(File(app.filesDir, "models"))
            return DeviceCapabilities(
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                process64Bit = is64,
                processAbi = processAbis.firstOrNull().orEmpty(),
                totalRamBytes = memory.totalMem.coerceAtLeast(0),
                availableRamBytes = memory.availMem.coerceAtLeast(0),
                lowRam = manager?.isLowRamDevice ?: true,
                cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
                freeDiskBytes = app.filesDir.usableSpace.coerceAtLeast(0),
                hasFrontCamera = packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
                hasRearCamera = packages.hasSystemFeature(PackageManager.FEATURE_CAMERA),
                hasCamera = packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                hasMicrophone = packages.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
                glesVersion = manager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0,
                installedModelProfiles = modelInventory.installedProfiles,
                downloadedModelBytes = modelInventory.downloadedBytes,
            )
        }
    }
}
