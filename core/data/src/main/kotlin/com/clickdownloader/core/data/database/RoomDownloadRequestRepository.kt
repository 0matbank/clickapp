package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.OutputFileRepository
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.DuplicatePolicy
import com.clickdownloader.core.model.FinalizedFile
import com.clickdownloader.core.model.PartialFilePolicy
import java.util.UUID
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
    displayName = displayName,
    mimeType = mimeType,
    kind = kind.name,
    expectedBytes = expectedBytes,
    etag = etag,
    lastModified = lastModified,
    temporaryPath = temporaryPath,
    outputUri = outputUri,
    supportsRanges = supportsRanges,
    priority = priority,
    queuePosition = queuePosition,
    attempt = attempt,
    maxAttempts = maxAttempts,
    duplicatePolicy = duplicatePolicy.name,
    partialFilePolicy = partialFilePolicy.name,
)

private fun DownloadRequestEntity.asModel() = DownloadRequest(
    jobId = jobId,
    url = url,
    displayName = displayName,
    mimeType = mimeType,
    kind = DownloadKind.valueOf(kind),
    expectedBytes = expectedBytes,
    etag = etag,
    lastModified = lastModified,
    temporaryPath = temporaryPath,
    outputUri = outputUri,
    supportsRanges = supportsRanges,
    priority = priority,
    queuePosition = queuePosition,
    attempt = attempt,
    maxAttempts = maxAttempts,
    duplicatePolicy = DuplicatePolicy.valueOf(duplicatePolicy),
    partialFilePolicy = PartialFilePolicy.valueOf(partialFilePolicy),
)
