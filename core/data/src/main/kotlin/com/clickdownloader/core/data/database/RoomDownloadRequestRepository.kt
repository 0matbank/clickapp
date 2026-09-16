package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.OutputFileRepository
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.DuplicatePolicy
import com.clickdownloader.core.model.FinalizedFile
import com.clickdownloader.core.model.LibraryMedia
import com.clickdownloader.core.model.PartialFilePolicy
import java.util.UUID
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadRequestRepository(private val dao: DownloadRequestDao) : DownloadRequestRepository {
    override fun observeRequests(): Flow<List<DownloadRequest>> = dao.observeAll().map { rows -> rows.map(DownloadRequestEntity::asModel) }
    override suspend fun upsert(request: DownloadRequest) = dao.upsert(request.asEntity())
    override suspend fun findByJobId(jobId: String): DownloadRequest? = dao.findByJobId(jobId)?.asModel()
    override suspend fun nextQueued(): DownloadRequest? = dao.nextQueued()?.asModel()
    override suspend fun delete(jobId: String) = dao.delete(jobId)
}

class RoomOutputFileRepository(private val dao: OutputFileDao) : OutputFileRepository {
    override fun observeFiles(): Flow<List<LibraryMedia>> = dao.observeAll().map { rows ->
        rows.map { row ->
            LibraryMedia(row.id, row.jobId, row.contentUri, row.displayName, row.mimeType.orEmpty(), row.sizeBytes ?: 0L, row.isVerified, row.createdAtEpochMillis)
        }
    }

    override suspend fun add(jobId: String, file: FinalizedFile, verified: Boolean) = dao.upsert(
        OutputFileEntity(
            id = UUID.randomUUID().toString(),
            jobId = jobId,
            contentUri = file.uri,
            displayName = file.displayName,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            isVerified = verified,
            createdAtEpochMillis = System.currentTimeMillis(),
        ),
    )
}

private fun DownloadRequest.asEntity() = DownloadRequestEntity(
    jobId = jobId,
        url = url,
        secondaryUrl = secondaryUrl,
    displayName = displayName,
    mimeType = mimeType,
    kind = kind.name,
    expectedBytes = expectedBytes,
    etag = etag,
    lastModified = lastModified,
    temporaryPath = temporaryPath,
    outputUri = outputUri,
    supportsRanges = supportsRanges,
    headersEncoded = headers.encodeHeaders(),
    secondaryHeadersEncoded = secondaryHeaders.encodeHeaders(),
    priority = priority,
    queuePosition = queuePosition,
    attempt = attempt,
    maxAttempts = maxAttempts,
    duplicatePolicy = duplicatePolicy.name,
        partialFilePolicy = partialFilePolicy.name,
        sessionHost = sessionHost,
)

private fun DownloadRequestEntity.asModel() = DownloadRequest(
    jobId = jobId,
        url = url,
        secondaryUrl = secondaryUrl,
    displayName = displayName,
    mimeType = mimeType,
    kind = DownloadKind.valueOf(kind),
    expectedBytes = expectedBytes,
    etag = etag,
    lastModified = lastModified,
    temporaryPath = temporaryPath,
    outputUri = outputUri,
    supportsRanges = supportsRanges,
    headers = headersEncoded.decodeHeaders(),
    secondaryHeaders = secondaryHeadersEncoded.decodeHeaders(),
    priority = priority,
    queuePosition = queuePosition,
    attempt = attempt,
    maxAttempts = maxAttempts,
    duplicatePolicy = DuplicatePolicy.valueOf(duplicatePolicy),
        partialFilePolicy = PartialFilePolicy.valueOf(partialFilePolicy),
        sessionHost = sessionHost,
)

private fun Map<String, String>.encodeHeaders(): String = entries.joinToString("\n") {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    "${encoder.encodeToString(it.key.toByteArray())}:${encoder.encodeToString(it.value.toByteArray())}"
}

private fun String.decodeHeaders(): Map<String, String> {
    val decoder = Base64.getUrlDecoder()
    return lineSequence().filter(String::isNotBlank).associate { line ->
        val (key, value) = line.split(':', limit = 2)
        String(decoder.decode(key)) to String(decoder.decode(value))
    }
}
