package com.clickdownloader.core.domain

import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.FinalizedFile
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
