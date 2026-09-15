package com.clickdownloader.core.domain

import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import kotlinx.coroutines.flow.Flow

interface DownloadJobRepository {
    fun observeJobs(): Flow<List<DownloadJob>>
    suspend fun upsert(job: DownloadJob)
    suspend fun findById(id: String): DownloadJob?
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

