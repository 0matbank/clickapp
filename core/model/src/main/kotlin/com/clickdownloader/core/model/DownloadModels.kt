package com.clickdownloader.core.model

enum class DownloadKind { DIRECT, EXTRACTED, HLS, DASH, LIVE }

enum class DuplicatePolicy { SKIP, REPLACE, KEEP_BOTH }

enum class PartialFilePolicy { KEEP, DELETE }

data class DownloadRequest(
    val jobId: String,
    val url: String,
    val displayName: String,
    val mimeType: String? = null,
    val kind: DownloadKind = DownloadKind.DIRECT,
    val expectedBytes: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val temporaryPath: String? = null,
    val outputUri: String? = null,
    val secondaryUrl: String? = null,
    val supportsRanges: Boolean? = null,
    val headers: Map<String, String> = emptyMap(),
    val secondaryHeaders: Map<String, String> = emptyMap(),
    val priority: Int = 0,
    val queuePosition: Long = 0,
    val attempt: Int = 0,
    val maxAttempts: Int = 5,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH,
    val partialFilePolicy: PartialFilePolicy = PartialFilePolicy.KEEP,
    val sessionHost: String? = null,
) {
    init {
        require(jobId.isNotBlank())
        require(url.startsWith("https://") || url.startsWith("http://"))
        require(displayName.isNotBlank())
        require(expectedBytes == null || expectedBytes >= 0)
        require(attempt >= 0 && maxAttempts >= 0)
    }
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
)

data class FinalizedFile(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

sealed interface DownloadControl {
    data object Continue : DownloadControl
    data object Pause : DownloadControl
    data object Cancel : DownloadControl
}

enum class DirectDownloadFailure {
    INVALID_RESPONSE,
    AUTH_REQUIRED,
    LINK_EXPIRED,
    RATE_LIMITED,
    NETWORK,
    STORAGE,
    CANCELLED,
    VERIFICATION,
}

class DirectDownloadException(
    val failure: DirectDownloadFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class FragmentCheckpoint(
    val jobId: String,
    val trackId: String,
    val fragmentIndex: Long,
    val downloadedBytes: Long,
    val temporaryPath: String,
    val completed: Boolean,
)

data class MediaProcessProgress(
    val percent: Float,
    val etaSeconds: Long,
    val line: String,
)

data class ProcessedMediaArtifact(
    val path: String,
    val sidecarPaths: List<String> = emptyList(),
)
