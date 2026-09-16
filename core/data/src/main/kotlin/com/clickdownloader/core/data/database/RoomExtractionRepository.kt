package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.ExtractionRepository
import com.clickdownloader.core.model.MediaMetadata
import com.clickdownloader.core.model.SelectedFormat

class RoomExtractionRepository(private val dao: ExtractionDao) : ExtractionRepository {
    override suspend fun saveMetadata(metadata: MediaMetadata) = dao.upsertMetadata(
        MediaMetadataEntity(
            jobId = metadata.jobId,
            title = metadata.title,
            description = metadata.description,
            creator = metadata.creator,
            sourcePlatform = metadata.sourcePlatform,
            canonicalUrl = metadata.canonicalUrl,
            durationMillis = metadata.durationMillis,
            uploadDate = metadata.uploadDate,
            liveStatus = metadata.liveStatus.name,
            expiryHintEpochMillis = metadata.expiryHintEpochMillis,
            thumbnailUrlsJson = metadata.thumbnailUrls.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"") { it.replace("\\", "\\\\").replace("\"", "\\\"") }
                ?: "[]",
        ),
    )

    override suspend fun saveSelectedFormat(format: SelectedFormat) = dao.upsertSelectedFormat(
        SelectedFormatEntity(
            jobId = format.jobId,
            formatId = format.formatId,
            width = format.width,
            height = format.height,
            framesPerSecond = format.framesPerSecond,
            videoBitrate = format.videoBitrate,
            videoCodec = format.videoCodec,
            audioCodec = format.audioCodec,
            audioBitrate = format.audioBitrate,
            container = format.container,
            dynamicRange = format.dynamicRange,
            bitDepth = format.bitDepth,
            audioLanguage = format.audioLanguage,
            estimatedBytes = format.estimatedBytes,
            hasVideo = format.hasVideo,
            hasAudio = format.hasAudio,
        ),
    )

    override suspend fun findSelectedFormat(jobId: String): SelectedFormat? = dao.findSelectedFormat(jobId)?.let {
        SelectedFormat(
            jobId = it.jobId,
            formatId = it.formatId,
            width = it.width,
            height = it.height,
            framesPerSecond = it.framesPerSecond,
            videoBitrate = it.videoBitrate,
            videoCodec = it.videoCodec,
            audioCodec = it.audioCodec,
            audioBitrate = it.audioBitrate,
            container = it.container,
            dynamicRange = it.dynamicRange,
            bitDepth = it.bitDepth,
            audioLanguage = it.audioLanguage,
            estimatedBytes = it.estimatedBytes,
            hasVideo = it.hasVideo,
            hasAudio = it.hasAudio,
        )
    }
}
