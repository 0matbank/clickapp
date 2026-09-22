package com.clickdownloader.app.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.BatteryManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.clickdownloader.app.ClickDownloaderApplication
import com.clickdownloader.core.download.DirectFileVerifier
import com.clickdownloader.core.download.RetryPolicy
import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure
import com.clickdownloader.core.model.DownloadControl
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.DownloadProgress
import com.clickdownloader.core.model.PartialFilePolicy
import com.clickdownloader.core.media.MediaArtifactVerifier
import com.clickdownloader.core.media.DevicePerformancePolicy
import com.clickdownloader.core.browser.SecretRedactor
import com.clickdownloader.core.extractor.ExactFormatUnavailableException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val activeJobId = AtomicReference<String?>(null)
    private val control = AtomicReference<DownloadControl>(DownloadControl.Continue)
    private val activeIsLive = AtomicBoolean(false)
    private val retryPolicy = RetryPolicy()
    private val container by lazy { (application as ClickDownloaderApplication).container }

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        foreground(jobId ?: "queue", getString(com.clickdownloader.app.R.string.app_name), DownloadJobState.PREPARING)
        when (intent?.action ?: ACTION_START) {
            ACTION_PAUSE -> if (activeJobId.get() == jobId) control.set(DownloadControl.Pause)
            ACTION_CANCEL -> if (activeJobId.get() == jobId) control.set(DownloadControl.Cancel) else jobId?.let { cancelQueued(it) }
            ACTION_FINALIZE_LIVE -> if (activeJobId.get() == jobId && activeIsLive.get()) jobId?.let(container.adaptiveMediaProcessor::requestLiveFinalization)
            ACTION_RESUME, ACTION_RETRY -> jobId?.let { resume(it) }
            else -> processQueue()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun processQueue() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                container.downloadJobRepository.recoverInterruptedJobs()
                var request = container.downloadRequestRepository.nextQueued()
                while (request != null) {
                    process(request)
                    request = container.downloadRequestRepository.nextQueued()
                }
            } finally {
                running.set(false)
                activeJobId.set(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun process(request: com.clickdownloader.core.model.DownloadRequest) {
        val jobs = container.downloadJobRepository
        val job = jobs.findById(request.jobId) ?: return
        activeJobId.set(job.id)
        activeIsLive.set(request.kind == DownloadKind.LIVE)
        control.set(DownloadControl.Continue)
        jobs.updateState(job.id, DownloadJobState.PREPARING)
        foreground(job.id, job.displayTitle, DownloadJobState.PREPARING)
        val batteryManager = getSystemService(BatteryManager::class.java)
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (container.settingsRepository.settings.first().pauseDownloadsOnLowBattery &&
            batteryPercent in 0..14 && !batteryManager.isCharging
        ) {
            jobs.updateState(job.id, DownloadJobState.RETRY_SCHEDULED, "LOW_BATTERY", "Waiting for battery level or charging")
            RecoveryWorker.schedule(this, 15 * 60_000L, job.id)
            return
        }
        val partial = request.temporaryPath?.let(::File)
            ?: File(filesDir, "partial/${job.id}.part").also {
                container.downloadRequestRepository.upsert(request.copy(temporaryPath = it.absolutePath))
            }
        val remaining = request.expectedBytes?.minus(partial.takeIf(File::exists)?.length() ?: 0L)
        val available = container.downloadFinalizer.availableBytes()
        if (remaining != null && available != null && remaining > available) {
            jobs.updateState(job.id, DownloadJobState.STORAGE_REQUIRED, "INSUFFICIENT_STORAGE", "More free storage is required")
            return
        }
        val downloadState = if (request.mimeType?.startsWith("audio/") == true) DownloadJobState.DOWNLOADING_AUDIO else DownloadJobState.DOWNLOADING_VIDEO
        jobs.updateState(job.id, downloadState)
        var sessionCookieFile: File? = null
        val workStarted = container.heavyWorkCoordinator.tryStartDownload(DevicePerformancePolicy.detect(this).lowRam)
        if (!workStarted) {
            jobs.updateState(job.id, DownloadJobState.RETRY_SCHEDULED, "LOW_RAM_BUSY", "Waiting for the active conversion to finish")
            RecoveryWorker.schedule(this, 15_000, job.id)
            return
        }
        try {
            sessionCookieFile = request.sessionHost?.let(container::exportBrowserSession)?.let(::File)
            if (request.sessionHost != null && sessionCookieFile == null) {
                throw DirectDownloadException(DirectDownloadFailure.AUTH_REQUIRED, "The saved login session is unavailable or expired")
            }
            val artifactFile: File
            val verifiedSize: Long
            val sidecars: List<String>
            if (request.kind == DownloadKind.DIRECT) {
                val result = container.directDownloader.download(
                    request = request,
                    partialFile = partial,
                    control = { control.get() },
                    onProgress = { progress ->
                        jobs.updateProgress(job.id, progress.downloadedBytes, progress.totalBytes)
                        foreground(job.id, job.displayTitle, downloadState, progress)
                    },
                )
                artifactFile = result.file
                verifiedSize = DirectFileVerifier.verify(result.file, result.totalBytes)
                sidecars = emptyList()
            } else {
                val selected = container.extractionRepository.findSelectedFormat(job.id)
                    ?: throw DirectDownloadException(DirectDownloadFailure.VERIFICATION, "The exact selected format is missing")
                jobs.updateState(job.id, DownloadJobState.DOWNLOADING_FRAGMENTS)
                val workDirectory = File(filesDir, "adaptive/${job.id}")
                var merging = false
                val artifact = container.adaptiveMediaProcessor.process(
                    jobId = job.id,
                    sourceUrl = job.sourceUrl,
                    exactFormatSpec = selected.formatId,
                    workingDirectory = workDirectory,
                    preferredContainer = selected.container,
                    cookieFilePath = sessionCookieFile?.absolutePath,
                    onProgress = { mediaProgress ->
                        when (control.get()) {
                            DownloadControl.Pause, DownloadControl.Cancel -> container.adaptiveMediaProcessor.cancel(job.id)
                            DownloadControl.Continue -> Unit
                        }
                        val bytes = request.expectedBytes?.let { (it * mediaProgress.percent / 100f).toLong() } ?: 0L
                        jobs.updateProgress(job.id, bytes, request.expectedBytes)
                        if (mediaProgress.line.contains("Merger", true) || mediaProgress.line.contains("ffmpeg", true)) merging = true
                        val currentState = if (merging) DownloadJobState.MERGING else DownloadJobState.DOWNLOADING_FRAGMENTS
                        jobs.updateState(job.id, currentState)
                        foreground(job.id, job.displayTitle, currentState, DownloadProgress(bytes, request.expectedBytes, 0))
                    },
                    onCheckpoint = container.fragmentCheckpointRepository::save,
                )
                artifactFile = File(artifact.path)
                MediaArtifactVerifier.verify(artifactFile, selected)
                verifiedSize = artifactFile.length()
                sidecars = artifact.sidecarPaths
            }
            jobs.updateState(job.id, DownloadJobState.VERIFYING)
            foreground(job.id, job.displayTitle, DownloadJobState.VERIFYING)
            val finalized = container.downloadFinalizer.finalizeFromTemporary(
                artifactFile.absolutePath,
                request.displayName,
                request.mimeType ?: "application/octet-stream",
            ).getOrElse { throw DirectDownloadException(DirectDownloadFailure.STORAGE, it.message ?: "Storage finalization failed", it) }
            require(finalized.sizeBytes == verifiedSize) { "Final output size changed during finalization" }
            container.outputFileRepository.add(job.id, finalized, verified = true)
            container.downloadRequestRepository.upsert(request.copy(outputUri = finalized.uri))
            if (request.kind != DownloadKind.DIRECT) {
                container.fragmentCheckpointRepository.clear(job.id)
                sidecars.map(::File).forEach { it.delete() }
            }
            jobs.updateState(job.id, DownloadJobState.COMPLETED)
            DownloadNotifications.post(this, job.id.hashCode(), DownloadNotifications.build(this, job.id, job.displayTitle, DownloadJobState.COMPLETED))
        } catch (error: DirectDownloadException) {
            when (control.get()) {
                DownloadControl.Pause -> jobs.updateState(job.id, DownloadJobState.PAUSED)
                DownloadControl.Cancel -> {
                    if (request.partialFilePolicy == PartialFilePolicy.DELETE) partial.delete()
                    jobs.updateState(job.id, DownloadJobState.CANCELLED)
                }
                DownloadControl.Continue -> handleFailure(request, error)
            }
        } catch (error: Throwable) {
            when (control.get()) {
                DownloadControl.Pause -> jobs.updateState(job.id, DownloadJobState.PAUSED)
                DownloadControl.Cancel -> jobs.updateState(job.id, DownloadJobState.CANCELLED)
                DownloadControl.Continue -> handleFailure(request, classifyProcessingFailure(error))
            }
        } finally {
            container.heavyWorkCoordinator.downloadFinished()
            sessionCookieFile?.delete()
            activeJobId.compareAndSet(job.id, null)
            activeIsLive.set(false)
        }
    }

    private suspend fun handleFailure(request: com.clickdownloader.core.model.DownloadRequest, error: DirectDownloadException) {
        if (error.failure == DirectDownloadFailure.LINK_EXPIRED && request.kind != DownloadKind.DIRECT) {
            if (refreshExpiredExactFormat(request)) return
        }
        val terminalState = when (error.failure) {
            DirectDownloadFailure.AUTH_REQUIRED -> DownloadJobState.AUTH_REQUIRED
            DirectDownloadFailure.LINK_EXPIRED -> DownloadJobState.LINK_EXPIRED
            DirectDownloadFailure.STORAGE -> DownloadJobState.STORAGE_REQUIRED
            DirectDownloadFailure.CANCELLED -> DownloadJobState.CANCELLED
            DirectDownloadFailure.NETWORK, DirectDownloadFailure.RATE_LIMITED -> DownloadJobState.RETRY_SCHEDULED
            else -> DownloadJobState.FAILED
        }
        val retryable = terminalState == DownloadJobState.RETRY_SCHEDULED && request.attempt < request.maxAttempts
        val safeMessage = SecretRedactor.redact(error.message ?: "Download failed")
        container.downloadJobRepository.updateState(request.jobId, if (retryable) DownloadJobState.RETRY_SCHEDULED else terminalState, error.failure.name, safeMessage)
        if (retryable) {
            val next = request.copy(attempt = request.attempt + 1)
            container.downloadRequestRepository.upsert(next)
            container.downloadJobRepository.findById(request.jobId)?.let { job ->
                container.downloadJobRepository.upsert(job.copy(state = DownloadJobState.RETRY_SCHEDULED, retryCount = next.attempt, errorCode = error.failure.name, errorMessage = safeMessage))
            }
            RecoveryWorker.schedule(this, retryPolicy.delayMillis(next.attempt), request.jobId)
        }
    }

    private suspend fun refreshExpiredExactFormat(request: com.clickdownloader.core.model.DownloadRequest): Boolean {
        if (request.attempt >= request.maxAttempts) return false
        val job = container.downloadJobRepository.findById(request.jobId) ?: return false
        val selected = container.extractionRepository.findSelectedFormat(request.jobId) ?: return false
        var cookieFile: File? = null
        return try {
            container.downloadJobRepository.updateState(request.jobId, DownloadJobState.LINK_EXPIRED, "LINK_EXPIRED", "The media link expired; refreshing the exact selected format")
            cookieFile = request.sessionHost?.let(container::exportBrowserSession)?.let(::File)
            if (request.sessionHost != null && cookieFile == null) {
                container.downloadJobRepository.updateState(request.jobId, DownloadJobState.AUTH_REQUIRED, "AUTH_REQUIRED", "The saved login session expired; sign in again")
                true
            } else {
                container.downloadJobRepository.updateState(request.jobId, DownloadJobState.ANALYZING)
                container.exactFormatRefresher.refresh(job.sourceUrl, selected.formatId, cookieFile?.absolutePath)
                val next = request.copy(attempt = request.attempt + 1)
                container.downloadRequestRepository.upsert(next)
                container.downloadJobRepository.upsert(job.copy(
                    state = DownloadJobState.RETRY_SCHEDULED,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    retryCount = next.attempt,
                    errorCode = "LINK_REFRESHED",
                    errorMessage = "The exact selected format was refreshed",
                ))
                RecoveryWorker.schedule(this, retryPolicy.delayMillis(next.attempt), request.jobId)
                true
            }
        } catch (error: ExactFormatUnavailableException) {
            container.downloadJobRepository.updateState(request.jobId, DownloadJobState.LINK_EXPIRED, "EXACT_FORMAT_UNAVAILABLE", SecretRedactor.redact(error.message.orEmpty()))
            true
        } catch (error: Throwable) {
            val classified = classifyProcessingFailure(error)
            if (classified.failure == DirectDownloadFailure.AUTH_REQUIRED) {
                container.downloadJobRepository.updateState(request.jobId, DownloadJobState.AUTH_REQUIRED, "AUTH_REQUIRED", "Sign in again to refresh this source")
                true
            } else false
        } finally {
            cookieFile?.delete()
        }
    }

    private fun classifyProcessingFailure(error: Throwable): DirectDownloadException {
        return DownloadFailureClassifier.classify(error)
    }

    private fun foreground(jobId: String, title: String, state: DownloadJobState, progress: DownloadProgress? = null) {
        ServiceCompat.startForeground(
            this,
            DownloadNotifications.FOREGROUND_ID,
            DownloadNotifications.build(this, jobId, title, state, progress, isLive = activeIsLive.get()),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun resume(jobId: String) = scope.launch {
        val job = container.downloadJobRepository.findById(jobId) ?: return@launch
        val request = container.downloadRequestRepository.findByJobId(jobId) ?: return@launch
        if (job.state.isTerminal) return@launch
        if (request.attempt >= request.maxAttempts && job.state != DownloadJobState.PAUSED) {
            container.downloadJobRepository.updateState(jobId, DownloadJobState.FAILED, "RETRY_LIMIT", "Retry limit reached; analyze the source again")
            return@launch
        }
        val requiredSessionHost = request.sessionHost
        if (requiredSessionHost != null && !container.hasBrowserSession(requiredSessionHost)) {
            container.downloadJobRepository.updateState(jobId, DownloadJobState.AUTH_REQUIRED, "AUTH_REQUIRED", "The saved login session expired; sign in again")
            return@launch
        }
        container.downloadJobRepository.updateState(jobId, DownloadJobState.QUEUED)
        processQueue()
    }

    private fun cancelQueued(jobId: String) = scope.launch {
        val request = container.downloadRequestRepository.findByJobId(jobId)
        if (request?.partialFilePolicy == PartialFilePolicy.DELETE) request.temporaryPath?.let(::File)?.delete()
        container.downloadJobRepository.updateState(jobId, DownloadJobState.CANCELLED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.clickdownloader.action.START"
        const val ACTION_PAUSE = "com.clickdownloader.action.PAUSE"
        const val ACTION_RESUME = "com.clickdownloader.action.RESUME"
        const val ACTION_RETRY = "com.clickdownloader.action.RETRY"
        const val ACTION_CANCEL = "com.clickdownloader.action.CANCEL"
        const val ACTION_FINALIZE_LIVE = "com.clickdownloader.action.FINALIZE_LIVE"
        const val EXTRA_JOB_ID = "job_id"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(ACTION_START))
        }
    }
}
