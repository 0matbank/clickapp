package com.clickdownloader.core.model

data class MediaMetadata(
    val jobId: String,
    val title: String,
    val description: String? = null,
    val creator: String? = null,
    val sourcePlatform: String? = null,
    val canonicalUrl: String,
    val thumbnailUrls: List<String> = emptyList(),
    val durationMillis: Long? = null,
    val uploadDate: String? = null,
    val liveStatus: LiveStatus = LiveStatus.NOT_LIVE,
)

enum class LiveStatus {
    NOT_LIVE,
    LIVE,
    WAS_LIVE,
    UPCOMING,
}

data class SelectedFormat(
    val jobId: String,
    val formatId: String,
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    val audioLanguage: String? = null,
    val estimatedBytes: Long? = null,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
)

