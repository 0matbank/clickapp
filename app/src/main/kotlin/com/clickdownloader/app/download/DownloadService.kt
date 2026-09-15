package com.clickdownloader.app.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.clickdownloader.app.ClickDownloaderApplication
import com.clickdownloader.core.download.DirectFileVerifier
import com.clickdownloader.core.download.RetryPolicy
import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure
import com.clickdownloader.core.model.DownloadControl
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadProgress
import com.clickdownloader.core.model.PartialFilePolicy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val activeJobId = AtomicReference<String?>(null)
    private val control = AtomicReference<DownloadControl>(DownloadControl.Continue)
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
        control.set(DownloadControl.Continue)
        jobs.updateState(job.id, DownloadJobState.PREPARING)
        foreground(job.id, job.displayTitle, DownloadJobState.PREPARING)
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
        try {
            val result = container.directDownloader.download(
                request = request,
                partialFile = partial,
                control = { control.get() },
                onProgress = { progress ->
                    jobs.updateProgress(job.id, progress.downloadedBytes, progress.totalBytes)
                    foreground(job.id, job.displayTitle, downloadState, progress)
                },
            )
            jobs.updateState(job.id, DownloadJobState.VERIFYING)
            foreground(job.id, job.displayTitle, DownloadJobState.VERIFYING)
            val verifiedSize = DirectFileVerifier.verify(result.file, result.totalBytes)
            val finalized = container.downloadFinalizer.finalizeFromTemporary(
                result.file.absolutePath,
                request.displayName,
                request.mimeType ?: "application/octet-stream",
            ).getOrElse { throw DirectDownloadException(DirectDownloadFailure.STORAGE, it.message ?: "Storage finalization failed", it) }
            require(finalized.sizeBytes == verifiedSize) { "Final output size changed during finalization" }
            container.outputFileRepository.add(job.id, finalized, verified = true)
            container.downloadRequestRepository.upsert(request.copy(outputUri = finalized.uri))
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
            handleFailure(request, DirectDownloadException(DirectDownloadFailure.VERIFICATION, error.message ?: "Verification failed", error))
        } finally {
            activeJobId.compareAndSet(job.id, null)
        }
    }

    private suspend fun handleFailure(request: com.clickdownloader.core.model.DownloadRequest, error: DirectDownloadException) {
        val terminalState = when (error.failure) {
            DirectDownloadFailure.AUTH_REQUIRED -> DownloadJobState.AUTH_REQUIRED
            DirectDownloadFailure.LINK_EXPIRED -> DownloadJobState.LINK_EXPIRED
            DirectDownloadFailure.STORAGE -> DownloadJobState.STORAGE_REQUIRED
            DirectDownloadFailure.CANCELLED -> DownloadJobState.CANCELLED
            DirectDownloadFailure.NETWORK, DirectDownloadFailure.RATE_LIMITED -> DownloadJobState.RETRY_SCHEDULED
            else -> DownloadJobState.FAILED
        }
        val retryable = terminalState == DownloadJobState.RETRY_SCHEDULED && request.attempt < request.maxAttempts
        container.downloadJobRepository.updateState(request.jobId, if (retryable) DownloadJobState.RETRY_SCHEDULED else terminalState, error.failure.name, error.message)
        if (retryable) {
            val next = request.copy(attempt = request.attempt + 1)
            container.downloadRequestRepository.upsert(next)
            RecoveryWorker.schedule(this, retryPolicy.delayMillis(next.attempt), request.jobId)
        }
    }

    private fun foreground(jobId: String, title: String, state: DownloadJobState, progress: DownloadProgress? = null) {
        ServiceCompat.startForeground(
            this,
            DownloadNotifications.FOREGROUND_ID,
            DownloadNotifications.build(this, jobId, title, state, progress),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun resume(jobId: String) = scope.launch {
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
        const val EXTRA_JOB_ID = "job_id"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(ACTION_START))
        }
    }
}
