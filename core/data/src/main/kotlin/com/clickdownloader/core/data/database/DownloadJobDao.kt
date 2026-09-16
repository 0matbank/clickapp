package com.clickdownloader.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadJobEntity?

    @Upsert
    suspend fun upsert(job: DownloadJobEntity)

    @Query("UPDATE download_jobs SET state = :state, errorCode = :errorCode, errorMessage = :errorMessage, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateState(id: String, state: String, errorCode: String?, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE download_jobs SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long?, updatedAt: Long)

    @Query("UPDATE download_jobs SET state = 'QUEUED', updatedAtEpochMillis = :updatedAt WHERE state IN ('PREPARING', 'DOWNLOADING_VIDEO', 'DOWNLOADING_AUDIO', 'DOWNLOADING_FRAGMENTS', 'MERGING', 'VERIFYING') AND id IN (SELECT jobId FROM download_requests)")
    suspend fun recoverInterruptedJobs(updatedAt: Long)
}

@Dao
interface DownloadRequestDao {
    @Query("SELECT * FROM download_requests ORDER BY priority DESC, queuePosition ASC")
    fun observeAll(): Flow<List<DownloadRequestEntity>>

    @Query("SELECT * FROM download_requests WHERE jobId = :jobId LIMIT 1")
    suspend fun findByJobId(jobId: String): DownloadRequestEntity?

    @Query("SELECT r.* FROM download_requests r JOIN download_jobs j ON j.id = r.jobId WHERE j.state = 'QUEUED' ORDER BY r.priority DESC, r.queuePosition ASC LIMIT 1")
    suspend fun nextQueued(): DownloadRequestEntity?

    @Upsert
    suspend fun upsert(request: DownloadRequestEntity)

    @Query("DELETE FROM download_requests WHERE jobId = :jobId")
    suspend fun delete(jobId: String)
}

@Dao
interface OutputFileDao {
    @Query("SELECT * FROM output_files ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<OutputFileEntity>>

    @Upsert
    suspend fun upsert(file: OutputFileEntity)
}

@Dao
interface ExtractionDao {
    @Upsert
    suspend fun upsertMetadata(metadata: MediaMetadataEntity)

    @Upsert
    suspend fun upsertSelectedFormat(format: SelectedFormatEntity)

    @Query("SELECT * FROM selected_formats WHERE jobId = :jobId LIMIT 1")
    suspend fun findSelectedFormat(jobId: String): SelectedFormatEntity?
}

@Dao
interface FragmentCheckpointDao {
    @Upsert
    suspend fun upsert(checkpoint: FragmentStateEntity)

    @Query("DELETE FROM fragment_states WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: String)
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("UPDATE playlist_items SET jobId = :jobId WHERE playlistId = :playlistId AND itemId = :itemId")
    suspend fun attachJob(playlistId: String, itemId: String, jobId: String)
}
