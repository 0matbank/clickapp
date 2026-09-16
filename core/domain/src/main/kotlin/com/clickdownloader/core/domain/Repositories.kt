package com.clickdownloader.core.domain

import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.FinalizedFile
import com.clickdownloader.core.model.MediaAnalysis
import com.clickdownloader.core.model.MediaMetadata
import com.clickdownloader.core.model.SelectedFormat
import com.clickdownloader.core.model.FragmentCheckpoint
import com.clickdownloader.core.model.MediaProcessProgress
import com.clickdownloader.core.model.ProcessedMediaArtifact
import com.clickdownloader.core.model.PlaylistItem
import java.io.File
import kotlinx.coroutines.flow.Flow

interface DownloadJobRepository {
    fun observeJobs(): Flow<List<DownloadJob>>
    suspend fun upsert(job: DownloadJob)
    suspend fun findById(id: String): DownloadJob?
    suspend fun updateState(id: String, state: DownloadJobState, errorCode: String? = null, errorMessage: String? = null)
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long?)
    suspend fun recoverInterruptedJobs()
}

interface DownloadRequestRepository {
    fun observeRequests(): Flow<List<DownloadRequest>>
    suspend fun upsert(request: DownloadRequest)
    suspend fun findByJobId(jobId: String): DownloadRequest?
    suspend fun nextQueued(): DownloadRequest?
    suspend fun delete(jobId: String)
}

interface OutputFileRepository {
    suspend fun add(jobId: String, file: FinalizedFile, verified: Boolean)
}

interface MediaExtractor {
    suspend fun analyze(url: String, cookieFilePath: String? = null): MediaAnalysis
    suspend fun analyzePlaylist(url: String, cookieFilePath: String? = null): MediaAnalysis = analyze(url, cookieFilePath)
    fun cancel(operationId: String): Boolean
    fun engineVersion(): String?
}

interface PlaylistRepository {
    suspend fun savePlaylist(id: String, sourceUrl: String, title: String, items: List<PlaylistItem>)
    suspend fun attachJob(playlistId: String, itemId: String, jobId: String)
}

interface ExtractionRepository {
    suspend fun saveMetadata(metadata: MediaMetadata)
    suspend fun saveSelectedFormat(format: SelectedFormat)
    suspend fun findSelectedFormat(jobId: String): SelectedFormat?
}

interface FragmentCheckpointRepository {
    suspend fun save(checkpoint: FragmentCheckpoint)
    suspend fun clear(jobId: String)
}

interface AdaptiveMediaProcessor {
    suspend fun process(
        jobId: String,
        sourceUrl: String,
        exactFormatSpec: String,
        workingDirectory: File,
        preferredContainer: String?,
        onProgress: suspend (MediaProcessProgress) -> Unit,
        onCheckpoint: suspend (FragmentCheckpoint) -> Unit,
    ): ProcessedMediaArtifact
    fun cancel(jobId: String): Boolean
    fun requestLiveFinalization(jobId: String): Boolean = false
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setThemeMode(themeMode: AppThemeMode)
    suspend fun setAskQualityEveryTime(enabled: Boolean)
    suspend fun setDownloadDirectoryUri(uri: String?)
}

interface StorageGateway {
    suspend fun persistDirectoryAccess(uri: String): Result<Unit>
    fun defaultDownloadsDescription(): String
}

interface DownloadFinalizer {
    suspend fun availableBytes(): Long?
    suspend fun finalizeFromTemporary(
        temporaryPath: String,
        displayName: String,
        mimeType: String,
    ): Result<FinalizedFile>
}
