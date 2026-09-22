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
import com.clickdownloader.core.domain.AdaptiveMediaProcessor
import com.clickdownloader.core.domain.FragmentCheckpointRepository
import com.clickdownloader.core.domain.PlaylistRepository
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.storage.AndroidStorageGateway
import com.clickdownloader.core.storage.AndroidDownloadFinalizer
import com.clickdownloader.core.download.DirectMediaProbe
import com.clickdownloader.core.download.HttpDirectDownloader
import com.clickdownloader.core.extractor.YtDlpExtractor
import com.clickdownloader.core.extractor.ExactFormatRefresher
import com.clickdownloader.core.extractor.ExtractorUpdateManager
import com.clickdownloader.core.media.YtDlpAdaptiveMediaProcessor
import com.clickdownloader.core.browser.SessionCookieExporter
import com.clickdownloader.core.browser.SessionVault
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import com.clickdownloader.app.performance.HeavyWorkCoordinator

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val heavyWorkCoordinator = HeavyWorkCoordinator()

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
    val exactFormatRefresher: ExactFormatRefresher by lazy { ExactFormatRefresher(mediaExtractor) }
    val extractorUpdateManager: ExtractorUpdateManager by lazy { ExtractorUpdateManager(appContext) }

    val fragmentCheckpointRepository: FragmentCheckpointRepository by lazy {
        DataRepositories.createFragmentCheckpointRepository(appContext)
    }

    val playlistRepository: PlaylistRepository by lazy {
        DataRepositories.createPlaylistRepository(appContext)
    }

    val adaptiveMediaProcessor: AdaptiveMediaProcessor by lazy { YtDlpAdaptiveMediaProcessor(appContext) }

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

    fun exportBrowserSession(host: String): String? = SessionVault(appContext).restore(host)
        ?.let { SessionCookieExporter(appContext).export(host, it).absolutePath }

    fun hasBrowserSession(host: String): Boolean = SessionVault(appContext).restore(host) != null
}
