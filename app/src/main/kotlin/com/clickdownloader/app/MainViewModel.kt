package com.clickdownloader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.domain.OutputFileRepository
import com.clickdownloader.core.download.CreateDirectDownloadUseCase
import com.clickdownloader.core.extractor.PendingMediaSelection
import com.clickdownloader.core.extractor.QueueExactFormatUseCase
import com.clickdownloader.core.extractor.BatchPreparation
import com.clickdownloader.core.extractor.BatchQualityRule
import com.clickdownloader.core.extractor.ConfirmPlaylistBatchUseCase
import com.clickdownloader.core.extractor.ExtractorUpdateChannel
import com.clickdownloader.app.extractor.IsolatedAnalysisClient
import com.clickdownloader.core.extractor.PreparePlaylistBatchUseCase
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.LibraryMedia
import com.clickdownloader.core.media.CompatibleCopyProcessor
import com.clickdownloader.core.media.ConversionPreflight
import android.net.Uri
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
    val selectedPlaylistIds: Set<String> = emptySet(),
    val batchRule: BatchQualityRule = BatchQualityRule.BEST_SOURCE_WITH_AUDIO,
    val batchPreparation: BatchPreparation? = null,
    val liveJobIds: Set<String> = emptySet(),
    val library: List<LibraryMedia> = emptyList(),
    val compatibleCopyPrompt: CompatibleCopyPrompt? = null,
    val extractorStatus: ExtractorStatus = ExtractorStatus(),
)

data class ExtractorStatus(val running: Boolean = false, val version: String? = null, val message: String? = null)

data class CompatibleCopyPrompt(val media: LibraryMedia, val preflight: ConversionPreflight)

enum class UiMessage { INVALID_URL, ANALYZE_FAILED, SOURCE_UNAVAILABLE, SESSION_REQUIRED, DOWNLOAD_QUEUED, BATCH_PARTIAL, FOLDER_SAVED, FOLDER_ERROR, BUBBLE_SHARE_FALLBACK, CONVERSION_PREFLIGHT_FAILED }

internal fun classifyAnalysisFailure(error: Throwable): UiMessage {
    val detail = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
    return when {
        "sign in" in detail || "login" in detail || "log in" in detail || "cookies" in detail || "age-restricted" in detail -> UiMessage.SESSION_REQUIRED
        "video is unavailable" in detail || "media is unavailable" in detail || "private video" in detail || "removed" in detail -> UiMessage.SOURCE_UNAVAILABLE
        else -> UiMessage.ANALYZE_FAILED
    }
}

private data class SelectionState(
    val pending: PendingMediaSelection? = null,
    val video: MediaFormatOption? = null,
    val loading: Boolean = false,
    val selectedPlaylistIds: Set<String> = emptySet(),
    val batchRule: BatchQualityRule = BatchQualityRule.BEST_SOURCE_WITH_AUDIO,
    val batchPreparation: BatchPreparation? = null,
)

class MainViewModel(
    private val jobs: DownloadJobRepository,
    private val downloadRequests: DownloadRequestRepository,
    private val outputFiles: OutputFileRepository,
    private val settings: SettingsRepository,
    private val storage: StorageGateway,
    private val createDirectDownload: CreateDirectDownloadUseCase,
    private val analyzeExtracted: suspend (String, String?, String?) -> PendingMediaSelection,
    private val queueExactFormat: QueueExactFormatUseCase,
    private val preparePlaylistBatch: PreparePlaylistBatchUseCase,
    private val confirmPlaylistBatch: ConfirmPlaylistBatchUseCase,
    private val exportSessionCookie: (String) -> String?,
    private val compatibleCopyProcessor: CompatibleCopyProcessor,
    private val extractorClient: IsolatedAnalysisClient,
) : ViewModel() {
    private val inputUrl = MutableStateFlow("")
    private val message = MutableStateFlow<UiMessage?>(null)
    private val selection = MutableStateFlow(SelectionState())
    private val compatibleCopyPrompt = MutableStateFlow<CompatibleCopyPrompt?>(null)
    private val extractorStatus = MutableStateFlow(ExtractorStatus())

    private val jobLibrary = combine(jobs.observeJobs(), downloadRequests.observeRequests(), outputFiles.observeFiles()) { jobList, requests, library ->
        Triple(jobList, requests.filter { it.kind == DownloadKind.LIVE }.mapTo(mutableSetOf()) { it.jobId }, library)
    }
    private val backgroundUi = combine(jobLibrary, compatibleCopyPrompt, extractorStatus) { data, prompt, engine -> Triple(data, prompt, engine) }

    val uiState = combine(inputUrl, backgroundUi, settings.settings, message, selection) {
            url, background, appSettings, currentMessage, selectionState ->
        val (jobInfo, copyPrompt, engine) = background
        MainUiState(
            inputUrl = url,
            jobs = jobInfo.first,
            settings = appSettings,
            message = currentMessage,
            pendingSelection = selectionState.pending,
            selectedVideo = selectionState.video,
            isAnalyzing = selectionState.loading,
            selectedPlaylistIds = selectionState.selectedPlaylistIds,
            batchRule = selectionState.batchRule,
            batchPreparation = selectionState.batchPreparation,
            liveJobIds = jobInfo.second,
            library = jobInfo.third,
            compatibleCopyPrompt = copyPrompt,
            extractorStatus = engine,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setInputUrl(value: String) {
        inputUrl.value = value
        message.value = null
    }

    fun analyze(sessionHost: String? = null, onQueued: (String) -> Unit) {
        if (selection.value.loading) return
        viewModelScope.launch {
            selection.value = SelectionState(loading = true)
            val direct = createDirectDownload(inputUrl.value)
            if (direct.isSuccess) {
                finishQueue(direct.getOrThrow(), onQueued)
                return@launch
            }
            val cookieFilePath = sessionHost?.let(exportSessionCookie)
            val result = runCatching { analyzeExtracted(inputUrl.value, cookieFilePath, sessionHost) }
            cookieFilePath?.let { java.io.File(it).delete() }
            result
                .onSuccess {
                    selection.value = SelectionState(
                        pending = it,
                        selectedPlaylistIds = it.analysis.playlistItems.mapTo(linkedSetOf()) { item -> item.id },
                    )
                }
                .onFailure {
                    selection.value = SelectionState()
                    message.value = classifyAnalysisFailure(it)
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

    fun togglePlaylistItem(id: String) {
        val selected = selection.value.selectedPlaylistIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        selection.value = selection.value.copy(selectedPlaylistIds = selected, batchPreparation = null)
    }

    fun setBatchRule(rule: BatchQualityRule) {
        selection.value = selection.value.copy(batchRule = rule, batchPreparation = null)
    }

    fun prepareBatch() {
        val current = selection.value
        val pending = current.pending ?: return
        if (current.selectedPlaylistIds.isEmpty() || current.loading) return
        viewModelScope.launch {
            selection.value = current.copy(loading = true)
            val cookieFilePath = pending.sessionHost?.let(exportSessionCookie)
            val result = runCatching {
                preparePlaylistBatch(pending.jobId, pending.analysis.playlistItems, current.selectedPlaylistIds, current.batchRule, cookieFilePath, pending.sessionHost)
            }
            cookieFilePath?.let { java.io.File(it).delete() }
            selection.value = selection.value.copy(loading = false, batchPreparation = result.getOrNull())
            if (result.isFailure) message.value = UiMessage.ANALYZE_FAILED
        }
    }

    fun confirmBatch(onQueued: () -> Unit) {
        val prepared = selection.value.batchPreparation ?: return
        viewModelScope.launch {
            val (queued, failed) = confirmPlaylistBatch(prepared)
            selection.value = SelectionState()
            inputUrl.value = ""
            message.value = if (failed.isEmpty()) UiMessage.DOWNLOAD_QUEUED else UiMessage.BATCH_PARTIAL
            if (queued.isNotEmpty()) onQueued()
        }
    }

    fun dismissBatchConfirmation() {
        selection.value = selection.value.copy(batchPreparation = null)
    }

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
    fun setBubbleEnabled(value: Boolean) { viewModelScope.launch { settings.setBubbleEnabled(value) } }
    fun setBubbleOpacity(value: Float) { viewModelScope.launch { settings.setBubbleOpacity(value) } }
    fun setBubbleSizeDp(value: Int) { viewModelScope.launch { settings.setBubbleSizeDp(value) } }
    fun setBubbleAllowlist(value: Set<String>) { viewModelScope.launch { settings.setBubbleAllowlistedPackages(value) } }
    fun setAccessibilityBubbleAssist(value: Boolean) { viewModelScope.launch { settings.setAccessibilityBubbleAssist(value) } }
    fun setAllowConversionOnLowBattery(value: Boolean) { viewModelScope.launch { settings.setAllowConversionOnLowBattery(value) } }
    fun setAllowConversionWhenHot(value: Boolean) { viewModelScope.launch { settings.setAllowConversionWhenHot(value) } }
    fun setPauseDownloadsOnLowBattery(value: Boolean) { viewModelScope.launch { settings.setPauseDownloadsOnLowBattery(value) } }
    fun showBubbleFallback() { message.value = UiMessage.BUBBLE_SHARE_FALLBACK }

    fun prepareCompatibleCopy(media: LibraryMedia) {
        viewModelScope.launch {
            runCatching { compatibleCopyProcessor.preflight(Uri.parse(media.uri)) }
                .onSuccess { compatibleCopyPrompt.value = CompatibleCopyPrompt(media, it) }
                .onFailure { message.value = UiMessage.CONVERSION_PREFLIGHT_FAILED }
        }
    }

    fun dismissCompatibleCopy() { compatibleCopyPrompt.value = null }

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

    fun readExtractorVersion() = extractorOperation {
        val version = extractorClient.currentVersion()
        ExtractorStatus(version = version, message = "Current engine: $version")
    }

    fun updateExtractor(channel: ExtractorUpdateChannel) = extractorOperation {
        val result = extractorClient.update(channel)
        ExtractorStatus(version = result.version, message = if (result.changed) "Extractor updated and rollback copy saved" else "Extractor is already up to date")
    }

    fun rollbackExtractor() = extractorOperation {
        val result = extractorClient.rollback()
        ExtractorStatus(version = result.version, message = "Rolled back to verified engine ${result.version}")
    }

    private fun extractorOperation(block: suspend () -> ExtractorStatus) {
        if (extractorStatus.value.running) return
        viewModelScope.launch {
            extractorStatus.value = extractorStatus.value.copy(running = true, message = null)
            extractorStatus.value = runCatching { block() }
                .getOrElse { ExtractorStatus(message = it.message ?: "Extractor operation failed") }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val analysisClient = IsolatedAnalysisClient(container.appContext)
                val analyze: suspend (String, String?, String?) -> PendingMediaSelection = analysisClient::analyze
                val queue = QueueExactFormatUseCase(container.downloadJobRepository, container.downloadRequestRepository, container.extractionRepository)
                return MainViewModel(
                    container.downloadJobRepository,
                    container.downloadRequestRepository,
                    container.outputFileRepository,
                    container.settingsRepository,
                    container.storageGateway,
                    CreateDirectDownloadUseCase(container.downloadJobRepository, container.downloadRequestRepository, container.directMediaProbe, BuildConfig.VERSION_NAME),
                    analyze,
                    queue,
                    PreparePlaylistBatchUseCase(analyze),
                    ConfirmPlaylistBatchUseCase(queue, container.playlistRepository, container.downloadJobRepository),
                    container::exportBrowserSession,
                    CompatibleCopyProcessor(container.appContext),
                    analysisClient,
                ) as T
            }
        }
    }
}
