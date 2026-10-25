package br.com.nexus.assistant.data.local

/** A public third-party model, pinned to one immutable upstream revision. */
data class LocalModelSpec(
    val fileName: String,
    val displayName: String,
    val downloadUrl: String,
    val bytes: Long,
    val sha256: String,
)

object LocalModelCatalog {
    val compact = LocalModelSpec(
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        displayName = "Qwen2.5 0.5B Instruct • Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
        bytes = 491_400_032,
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
    )

    val balanced = LocalModelSpec(
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        displayName = "Qwen2.5 1.5B Instruct • Q4_K_M",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
        bytes = 1_117_320_736,
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
    )

    fun forProfile(profile: LocalModelProfile): LocalModelSpec = when (profile) {
        LocalModelProfile.COMPACT -> compact
        LocalModelProfile.BALANCED -> balanced
    }
}

enum class LocalModelProfile { COMPACT, BALANCED }

enum class LocalModelStatus { CHECKING, ABSENT, CONNECTING, DOWNLOADING, VERIFYING, READY, PAUSED, FAILED }

data class LocalModelState(
    val status: LocalModelStatus = LocalModelStatus.CHECKING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = LocalModelCatalog.compact.bytes,
    val message: String? = null,
    val bytesPerSecond: Long = 0,
    val verifiedBytes: Long = 0,
)
