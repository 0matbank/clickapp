package com.clickdownloader.app

import android.content.Context
import com.clickdownloader.core.data.database.DataRepositories
import com.clickdownloader.core.data.settings.DataStoreSettingsRepository
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.OutputFileRepository
import com.clickdownloader.core.domain.DownloadFinalizer
import com.clickdownloader.core.domain.ExtractionRepository
import com.clickdownloader.core.domain.MediaExtractor
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.storage.AndroidStorageGateway
import com.clickdownloader.core.storage.AndroidDownloadFinalizer
import com.clickdownloader.core.download.DirectMediaProbe
import com.clickdownloader.core.download.HttpDirectDownloader
import com.clickdownloader.core.extractor.YtDlpExtractor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val downloadJobRepository: DownloadJobRepository by lazy {
        DataRepositories.createDownloadJobRepository(appContext)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    val downloadRequestRepository: DownloadRequestRepository by lazy {
        DataRepositories.createDownloadRequestRepository(appContext)
    }

    val outputFileRepository: OutputFileRepository by lazy {
        DataRepositories.createOutputFileRepository(appContext)
    }

    val extractionRepository: ExtractionRepository by lazy {
        DataRepositories.createExtractionRepository(appContext)
    }

    val mediaExtractor: MediaExtractor by lazy { YtDlpExtractor(appContext) }

    val storageGateway: StorageGateway by lazy {
        AndroidStorageGateway(appContext)
    }

    val downloadFinalizer: DownloadFinalizer by lazy {
        AndroidDownloadFinalizer(appContext) { settingsRepository.settings.first().downloadDirectoryUri }
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val directMediaProbe: DirectMediaProbe by lazy { DirectMediaProbe(httpClient) }
    val directDownloader: HttpDirectDownloader by lazy { HttpDirectDownloader(httpClient) }
}
