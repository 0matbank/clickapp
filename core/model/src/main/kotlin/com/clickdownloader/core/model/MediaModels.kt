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
    val expiryHintEpochMillis: Long? = null,
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
    val videoBitrate: Long? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioBitrate: Long? = null,
    val container: String? = null,
    val dynamicRange: String? = null,
    val bitDepth: Int? = null,
    val audioLanguage: String? = null,
    val estimatedBytes: Long? = null,
    val isSizeApproximate: Boolean = false,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
)

enum class StreamProtocol { HTTP, HLS, DASH, OTHER }
enum class FormatCompatibility { DIRECT, REMUX_REQUIRED, TRANSCODE_REQUIRED, UNSUPPORTED_DRM, UNKNOWN }

data class MediaFormatOption(
    val formatId: String,
    val formatNote: String? = null,
    val extension: String? = null,
    val protocol: StreamProtocol = StreamProtocol.OTHER,
    val directUrl: String? = null,
    val manifestUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val videoCodec: String? = null,
    val videoBitrate: Long? = null,
    val dynamicRange: String? = null,
    val bitDepth: Int? = null,
    val audioCodec: String? = null,
    val audioBitrate: Long? = null,
    val audioSampleRate: Long? = null,
    val audioChannels: Int? = null,
    val audioLanguage: String? = null,
    val estimatedBytes: Long? = null,
    val isSizeApproximate: Boolean = false,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val drmProtected: Boolean = false,
    val compatibility: FormatCompatibility = FormatCompatibility.UNKNOWN,
    val httpHeaders: Map<String, String> = emptyMap(),
) {
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val isProgressive: Boolean get() = hasAudio && hasVideo && protocol == StreamProtocol.HTTP
}

data class SubtitleTrack(
    val language: String,
    val name: String? = null,
    val extension: String? = null,
    val url: String? = null,
    val isAutomatic: Boolean = false,
)

data class PlaylistItem(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val position: Int,
    val durationMillis: Long? = null,
    val thumbnailUrl: String? = null,
)

data class MediaAnalysis(
    val metadata: MediaMetadata,
    val formats: List<MediaFormatOption>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val extractorKey: String? = null,
    val webpageUrl: String,
    val isPlaylist: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
)
