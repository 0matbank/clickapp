package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadJobRepository(
    private val dao: DownloadJobDao,
) : DownloadJobRepository {
    override fun observeJobs(): Flow<List<DownloadJob>> = dao.observeAll().map { jobs ->
        jobs.map(DownloadJobEntity::asExternalModel)
    }

    override suspend fun upsert(job: DownloadJob) = dao.upsert(job.asEntity())

    override suspend fun findById(id: String): DownloadJob? = dao.findById(id)?.asExternalModel()

    override suspend fun updateState(id: String, state: DownloadJobState, errorCode: String?, errorMessage: String?) {
        dao.updateState(id, state.name, errorCode, errorMessage, System.currentTimeMillis())
    }

    override suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long?) {
        dao.updateProgress(id, downloadedBytes, totalBytes, System.currentTimeMillis())
    }

    override suspend fun recoverInterruptedJobs() = dao.recoverInterruptedJobs(System.currentTimeMillis())
}

internal fun DownloadJob.asEntity() = DownloadJobEntity(
    id = id,
    sourceUrl = sourceUrl,
    displayTitle = displayTitle,
    state = state.name,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    retryCount = retryCount,
    errorCode = errorCode,
    errorMessage = errorMessage,
    appVersion = appVersion,
    engineVersion = engineVersion,
)

internal fun DownloadJobEntity.asExternalModel() = DownloadJob(
    id = id,
    sourceUrl = sourceUrl,
    displayTitle = displayTitle,
    state = DownloadJobState.valueOf(state),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    retryCount = retryCount,
    errorCode = errorCode,
    errorMessage = errorMessage,
    appVersion = appVersion,
    engineVersion = engineVersion,
)
