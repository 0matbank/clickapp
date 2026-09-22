package com.clickdownloader.core.extractor

import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.ExtractionRepository
import com.clickdownloader.core.domain.MediaExtractor
import com.clickdownloader.core.domain.PlaylistRepository
import com.clickdownloader.core.download.FilenamePolicy
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.MediaAnalysis
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.SelectedFormat
import com.clickdownloader.core.model.StreamProtocol
import java.util.UUID

enum class BatchQualityRule { BEST_SOURCE_WITH_AUDIO, BEST_MUXED, AUDIO_ONLY }

data class PreparedBatchItem(
    val playlistItemId: String,
    val pending: PendingMediaSelection,
    val primary: MediaFormatOption,
    val audio: MediaFormatOption?,
)

data class BatchPreparation(
    val playlistId: String,
    val items: List<PreparedBatchItem>,
    val failedItemIds: List<String>,
) {
    val knownBytes: Long = items.mapNotNull { item ->
        listOfNotNull(item.primary.estimatedBytes, item.audio?.estimatedBytes).takeIf { it.isNotEmpty() }?.sum()
    }.sum()
    val allSizesKnown: Boolean = items.all { it.primary.estimatedBytes != null && (it.audio == null || it.audio.estimatedBytes != null) }
}

data class PendingMediaSelection(val jobId: String, val analysis: MediaAnalysis, val sessionHost: String? = null) : java.io.Serializable

class AnalyzeExtractedMediaUseCase(
    private val jobs: DownloadJobRepository,
    private val extraction: ExtractionRepository,
    private val extractor: MediaExtractor,
    private val appVersion: String,
    private val playlists: PlaylistRepository? = null,
) {
    suspend operator fun invoke(url: String, cookieFilePath: String? = null, sessionHost: String? = null): PendingMediaSelection {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        jobs.upsert(DownloadJob(id, url, url, DownloadJobState.CREATED, now, now, appVersion = appVersion, engineVersion = extractor.engineVersion()))
        jobs.updateState(id, DownloadJobState.ANALYZING)
        return try {
            val raw = extractor.analyzePlaylist(url, cookieFilePath)
            val analysis = raw.copy(
                metadata = raw.metadata.copy(jobId = id),
                formats = raw.formats.map { it.copy(compatibility = AndroidFormatCompatibility.evaluate(it)) },
            )
            require(analysis.formats.isNotEmpty() || analysis.playlistItems.isNotEmpty()) { "The extractor returned no source formats or playlist items" }
            extraction.saveMetadata(analysis.metadata)
            if (analysis.isPlaylist) playlists?.savePlaylist(id, url, analysis.metadata.title, analysis.playlistItems)
            jobs.upsert(jobs.findById(id)!!.copy(displayTitle = analysis.metadata.title, engineVersion = extractor.engineVersion()))
            jobs.updateState(id, DownloadJobState.WAITING_FOR_SELECTION)
            PendingMediaSelection(id, analysis, sessionHost)
        } catch (error: Throwable) {
            jobs.updateState(id, DownloadJobState.FAILED, "EXTRACTION_FAILED", error.message)
            throw error
        }
    }
}

class PreparePlaylistBatchUseCase(
    private val analyze: suspend (String, String?, String?) -> PendingMediaSelection,
) {
    constructor(analyze: AnalyzeExtractedMediaUseCase) : this({ url, cookie, host -> analyze(url, cookie, host) })
    suspend operator fun invoke(
        playlistId: String,
        items: List<com.clickdownloader.core.model.PlaylistItem>,
        selectedIds: Set<String>,
        rule: BatchQualityRule,
        cookieFilePath: String? = null,
        sessionHost: String? = null,
    ): BatchPreparation {
        val prepared = mutableListOf<PreparedBatchItem>()
        val failures = mutableListOf<String>()
        items.filter { it.id in selectedIds }.forEach { item ->
            runCatching {
                val pending = analyze(item.sourceUrl, cookieFilePath, sessionHost)
                val formats = pending.analysis.formats.filterNot { it.drmProtected }
                val audioOnly = formats.filter(MediaFormatOption::isAudioOnly).maxByOrNull { it.audioBitrate ?: 0L }
                val primary = when (rule) {
                    BatchQualityRule.AUDIO_ONLY -> audioOnly
                    BatchQualityRule.BEST_MUXED -> formats.filter { it.hasVideo && it.hasAudio }.maxWithOrNull(videoComparator)
                    BatchQualityRule.BEST_SOURCE_WITH_AUDIO -> formats.filter { it.hasVideo }.maxWithOrNull(videoComparator)
                } ?: error("No format matches the selected batch rule")
                val companion = if (rule == BatchQualityRule.BEST_SOURCE_WITH_AUDIO && !primary.hasAudio) {
                    audioOnly ?: error("No companion audio format is available")
                } else null
                PreparedBatchItem(item.id, pending, primary, companion)
            }.onSuccess(prepared::add).onFailure { failures += item.id }
        }
        return BatchPreparation(playlistId, prepared, failures)
    }

    private companion object {
        val videoComparator = compareBy<MediaFormatOption>(
            { (it.width ?: 0).toLong() * (it.height ?: 0) },
            { it.framesPerSecond ?: 0.0 },
            { it.videoBitrate ?: 0L },
        )
    }
}

class ConfirmPlaylistBatchUseCase(
    private val queueExactFormat: QueueExactFormatUseCase,
    private val playlists: PlaylistRepository,
    private val jobs: DownloadJobRepository,
) {
    suspend operator fun invoke(batch: BatchPreparation): Pair<List<String>, List<String>> {
        val queued = mutableListOf<String>()
        val failed = batch.failedItemIds.toMutableList()
        batch.items.forEach { item ->
            runCatching {
                queueExactFormat(item.pending, item.primary, item.audio).also {
                    playlists.attachJob(batch.playlistId, item.playlistItemId, it)
                }
            }.onSuccess(queued::add).onFailure { failed += item.playlistItemId }
        }
        if (queued.isNotEmpty()) jobs.updateState(batch.playlistId, DownloadJobState.PLAYLIST_QUEUED)
        return queued to failed
    }
}

class QueueExactFormatUseCase(
    private val jobs: DownloadJobRepository,
    private val requests: DownloadRequestRepository,
    private val extraction: ExtractionRepository,
) {
    suspend operator fun invoke(
        pending: PendingMediaSelection,
        primary: MediaFormatOption,
        audio: MediaFormatOption? = null,
    ): String {
        require(primary in pending.analysis.formats && (audio == null || audio in pending.analysis.formats))
        require(!primary.drmProtected && audio?.drmProtected != true) { "DRM-protected formats are not downloadable" }
        require(primary.hasVideo || primary.isAudioOnly) { "Select a video or audio source format" }
        require(primary.hasAudio || audio?.isAudioOnly == true) { "This video-only source requires an explicit audio selection" }
        val primaryUrl = primary.directUrl ?: primary.manifestUrl ?: error("Selected source has no downloadable URL")
        val formatSpec = listOfNotNull(primary.formatId, audio?.formatId).joinToString("+")
        val extension = when {
            primary.isAudioOnly -> primary.extension ?: "m4a"
            audio != null && audio.extension != primary.extension -> "mkv"
            else -> primary.extension ?: "mp4"
        }
        val outputName = FilenamePolicy.sanitize("${pending.analysis.metadata.title}.$extension")
        extraction.saveSelectedFormat(
            SelectedFormat(
                jobId = pending.jobId,
                formatId = formatSpec,
                width = primary.width,
                height = primary.height,
                framesPerSecond = primary.framesPerSecond,
                videoBitrate = primary.videoBitrate,
                videoCodec = primary.videoCodec,
                audioCodec = primary.audioCodec ?: audio?.audioCodec,
                audioBitrate = primary.audioBitrate ?: audio?.audioBitrate,
                container = extension,
                dynamicRange = primary.dynamicRange,
                bitDepth = primary.bitDepth,
                audioLanguage = primary.audioLanguage ?: audio?.audioLanguage,
                estimatedBytes = listOfNotNull(primary.estimatedBytes, audio?.estimatedBytes).takeIf { it.isNotEmpty() }?.sum(),
                hasVideo = primary.hasVideo,
                hasAudio = primary.hasAudio || audio?.hasAudio == true,
            ),
        )
        val protocols = listOfNotNull(primary.protocol, audio?.protocol)
        val kind = when {
            pending.analysis.metadata.liveStatus == com.clickdownloader.core.model.LiveStatus.LIVE -> DownloadKind.LIVE
            StreamProtocol.HLS in protocols -> DownloadKind.HLS
            StreamProtocol.DASH in protocols -> DownloadKind.DASH
            else -> DownloadKind.EXTRACTED
        }
        requests.upsert(
            DownloadRequest(
                jobId = pending.jobId,
                url = primaryUrl,
                secondaryUrl = audio?.directUrl ?: audio?.manifestUrl,
                displayName = outputName,
                mimeType = if (primary.isAudioOnly) audioMime(extension) else videoMime(extension),
                kind = kind,
                expectedBytes = exactSize(primary, audio),
                headers = primary.httpHeaders,
                secondaryHeaders = audio?.httpHeaders.orEmpty(),
                queuePosition = System.currentTimeMillis(),
                sessionHost = pending.sessionHost,
            ),
        )
        jobs.updateState(pending.jobId, DownloadJobState.QUEUED)
        return pending.jobId
    }

    private fun audioMime(extension: String) = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "webm", "opus" -> "audio/opus"
        else -> "audio/mp4"
    }

    private fun videoMime(extension: String) = when (extension.lowercase()) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun exactSize(primary: MediaFormatOption, audio: MediaFormatOption?): Long? {
        if (primary.isSizeApproximate || audio?.isSizeApproximate == true) return null
        val primarySize = primary.estimatedBytes ?: return null
        return if (audio == null) primarySize else audio.estimatedBytes?.let(primarySize::plus)
    }
}
