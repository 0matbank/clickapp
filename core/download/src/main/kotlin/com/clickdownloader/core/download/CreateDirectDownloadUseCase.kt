package com.clickdownloader.core.download

import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadRequest
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateDirectDownloadUseCase(
    private val jobs: DownloadJobRepository,
    private val requests: DownloadRequestRepository,
    private val probe: DirectMediaProbe,
    private val appVersion: String,
) {
    suspend operator fun invoke(rawUrl: String): Result<String> = runCatching {
        val uri = URI(rawUrl.trim())
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
        require(!uri.host.isNullOrBlank())
        val normalized = uri.normalize().toASCIIString()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        jobs.upsert(
            DownloadJob(
                id = id,
                sourceUrl = normalized,
                displayTitle = uri.host,
                state = DownloadJobState.CREATED,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                appVersion = appVersion,
            ),
        )
        jobs.updateState(id, DownloadJobState.ANALYZING)
        try {
            val media = withContext(Dispatchers.IO) { probe.probe(normalized) }
            jobs.updateState(id, DownloadJobState.WAITING_FOR_SELECTION)
            requests.upsert(
                DownloadRequest(
                    jobId = id,
                    url = media.finalUrl,
                    displayName = media.displayName,
                    mimeType = media.mimeType,
                    expectedBytes = media.contentLength,
                    etag = media.etag,
                    lastModified = media.lastModified,
                    supportsRanges = media.supportsRanges,
                    queuePosition = now,
                ),
            )
            jobs.upsert(jobs.findById(id)!!.copy(displayTitle = media.displayName, totalBytes = media.contentLength))
            jobs.updateState(id, DownloadJobState.QUEUED)
            id
        } catch (error: Throwable) {
            jobs.updateState(id, DownloadJobState.FAILED, "DIRECT_ANALYZE_FAILED", error.message)
            throw error
        }
    }
}
