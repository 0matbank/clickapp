package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.FragmentCheckpointRepository
import com.clickdownloader.core.model.FragmentCheckpoint

class RoomFragmentCheckpointRepository(private val dao: FragmentCheckpointDao) : FragmentCheckpointRepository {
    override suspend fun save(checkpoint: FragmentCheckpoint) = dao.upsert(
        FragmentStateEntity(
            jobId = checkpoint.jobId,
            trackId = checkpoint.trackId,
            fragmentIndex = checkpoint.fragmentIndex,
            state = if (checkpoint.completed) "COMPLETED" else "PARTIAL",
            downloadedBytes = checkpoint.downloadedBytes,
            temporaryUri = checkpoint.temporaryPath,
            checksum = null,
        ),
    )

    override suspend fun clear(jobId: String) = dao.deleteForJob(jobId)
}
