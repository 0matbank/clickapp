package com.clickdownloader.core.model

data class DownloadJob(
    val id: String,
    val sourceUrl: String,
    val displayTitle: String,
    val state: DownloadJobState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val retryCount: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val appVersion: String,
    val engineVersion: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(sourceUrl.isNotBlank())
        require(displayTitle.isNotBlank())
        require(createdAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= createdAtEpochMillis)
        require(downloadedBytes >= 0)
        require(totalBytes == null || totalBytes >= 0)
        require(retryCount >= 0)
    }
}

