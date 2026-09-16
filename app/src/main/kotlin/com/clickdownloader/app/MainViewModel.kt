package com.clickdownloader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.download.CreateDirectDownloadUseCase
import com.clickdownloader.core.extractor.AnalyzeExtractedMediaUseCase
import com.clickdownloader.core.extractor.PendingMediaSelection
import com.clickdownloader.core.extractor.QueueExactFormatUseCase
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.MediaFormatOption
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
    val pendingSelection: PendingMediaSelection? = null,
    val selectedVideo: MediaFormatOption? = null,
    val isAnalyzing: Boolean = false,
)

enum class UiMessage { INVALID_URL, ANALYZE_FAILED, DOWNLOAD_QUEUED, FOLDER_SAVED, FOLDER_ERROR }

private data class SelectionState(
    val pending: PendingMediaSelection? = null,
    val video: MediaFormatOption? = null,
    val loading: Boolean = false,
)

class MainViewModel(
    private val jobs: DownloadJobRepository,
    private val settings: SettingsRepository,
    private val storage: StorageGateway,
    private val createDirectDownload: CreateDirectDownloadUseCase,
    private val analyzeExtracted: AnalyzeExtractedMediaUseCase,
    private val queueExactFormat: QueueExactFormatUseCase,
) : ViewModel() {
    private val inputUrl = MutableStateFlow("")
    private val message = MutableStateFlow<UiMessage?>(null)
    private val selection = MutableStateFlow(SelectionState())

    val uiState = combine(inputUrl, jobs.observeJobs(), settings.settings, message, selection) {
            url, jobList, appSettings, currentMessage, selectionState ->
        MainUiState(
            inputUrl = url,
            jobs = jobList,
            settings = appSettings,
            message = currentMessage,
            pendingSelection = selectionState.pending,
            selectedVideo = selectionState.video,
            isAnalyzing = selectionState.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setInputUrl(value: String) {
        inputUrl.value = value
        message.value = null
    }

    fun analyze(onQueued: (String) -> Unit) {
        if (selection.value.loading) return
        viewModelScope.launch {
            selection.value = SelectionState(loading = true)
            val direct = createDirectDownload(inputUrl.value)
            if (direct.isSuccess) {
                finishQueue(direct.getOrThrow(), onQueued)
                return@launch
            }
            runCatching { analyzeExtracted(inputUrl.value) }
                .onSuccess { selection.value = SelectionState(pending = it) }
                .onFailure {
                    selection.value = SelectionState()
                    message.value = UiMessage.ANALYZE_FAILED
                }
        }
    }

    fun selectFormat(format: MediaFormatOption, onQueued: (String) -> Unit) {
        val pending = selection.value.pending ?: return
        if (format.drmProtected) return
        if (format.hasVideo && !format.hasAudio) selection.value = selection.value.copy(video = format)
        else queue(pending, format, null, onQueued)
    }

    fun selectCompanionAudio(audio: MediaFormatOption, onQueued: (String) -> Unit) {
        val current = selection.value
        queue(current.pending ?: return, current.video ?: return, audio, onQueued)
    }

    fun backToFormats() { selection.value = selection.value.copy(video = null) }

    private fun queue(pending: PendingMediaSelection, primary: MediaFormatOption, audio: MediaFormatOption?, onQueued: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { queueExactFormat(pending, primary, audio) }
                .onSuccess { finishQueue(it, onQueued) }
                .onFailure { message.value = UiMessage.ANALYZE_FAILED }
        }
    }

    private fun finishQueue(jobId: String, onQueued: (String) -> Unit) {
        selection.value = SelectionState()
        inputUrl.value = ""
        message.value = UiMessage.DOWNLOAD_QUEUED
        onQueued(jobId)
    }

    fun setLanguage(value: AppLanguage) { viewModelScope.launch { settings.setLanguage(value) } }
    fun setTheme(value: AppThemeMode) { viewModelScope.launch { settings.setThemeMode(value) } }
    fun setAskQualityEveryTime(value: Boolean) { viewModelScope.launch { settings.setAskQualityEveryTime(value) } }

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

    fun consumeMessage() { message.value = null }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
                container.downloadJobRepository,
                container.settingsRepository,
                container.storageGateway,
                CreateDirectDownloadUseCase(container.downloadJobRepository, container.downloadRequestRepository, container.directMediaProbe, BuildConfig.VERSION_NAME),
                AnalyzeExtractedMediaUseCase(container.downloadJobRepository, container.extractionRepository, container.mediaExtractor, BuildConfig.VERSION_NAME),
                QueueExactFormatUseCase(container.downloadJobRepository, container.downloadRequestRepository, container.extractionRepository),
            ) as T
        }
    }
}
