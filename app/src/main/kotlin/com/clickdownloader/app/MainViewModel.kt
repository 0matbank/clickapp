package com.clickdownloader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clickdownloader.core.download.CreateDirectDownloadUseCase
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val inputUrl: String = "",
    val jobs: List<DownloadJob> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val message: UiMessage? = null,
)

enum class UiMessage {
    INVALID_URL,
    ANALYZE_FAILED,
    DOWNLOAD_QUEUED,
    FOLDER_SAVED,
    FOLDER_ERROR,
}

class MainViewModel(
    private val jobs: DownloadJobRepository,
    private val settings: SettingsRepository,
    private val storage: StorageGateway,
    private val createDirectDownload: CreateDirectDownloadUseCase,
) : ViewModel() {
    private val inputUrl = MutableStateFlow("")
    private val message = MutableStateFlow<UiMessage?>(null)

    val uiState = combine(
        inputUrl,
        jobs.observeJobs(),
        settings.settings,
        message,
    ) { url, jobList, appSettings, currentMessage ->
        MainUiState(url, jobList, appSettings, currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun setInputUrl(value: String) {
        inputUrl.value = value
        message.value = null
    }

    fun analyzeDirect(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            createDirectDownload(inputUrl.value)
                .onSuccess { jobId ->
                    inputUrl.value = ""
                    message.value = UiMessage.DOWNLOAD_QUEUED
                    onSuccess(jobId)
                }
                .onFailure { message.value = UiMessage.ANALYZE_FAILED }
        }
    }

    fun setLanguage(value: AppLanguage) {
        viewModelScope.launch { settings.setLanguage(value) }
    }

    fun setTheme(value: AppThemeMode) {
        viewModelScope.launch { settings.setThemeMode(value) }
    }

    fun setAskQualityEveryTime(value: Boolean) {
        viewModelScope.launch { settings.setAskQualityEveryTime(value) }
    }

    fun selectDownloadDirectory(uri: String) {
        viewModelScope.launch {
            storage.persistDirectoryAccess(uri)
                .onSuccess {
                    settings.setDownloadDirectoryUri(uri)
                    message.value = UiMessage.FOLDER_SAVED
                }
                .onFailure { message.value = UiMessage.FOLDER_ERROR }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
                    jobs = container.downloadJobRepository,
                    settings = container.settingsRepository,
                    storage = container.storageGateway,
                    createDirectDownload = CreateDirectDownloadUseCase(
                        jobs = container.downloadJobRepository,
                        requests = container.downloadRequestRepository,
                        probe = container.directMediaProbe,
                        appVersion = BuildConfig.VERSION_NAME,
                    ),
                ) as T
            }
    }
}
