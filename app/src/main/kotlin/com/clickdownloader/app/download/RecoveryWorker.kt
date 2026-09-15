package com.clickdownloader.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.app.ClickDownloaderApplication
import java.util.concurrent.TimeUnit

class RecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ClickDownloaderApplication).container
        val retryJobId = inputData.getString(KEY_JOB_ID)
        if (retryJobId == null) {
            container.downloadJobRepository.recoverInterruptedJobs()
        } else {
            container.downloadJobRepository.updateState(retryJobId, DownloadJobState.QUEUED)
        }
        DownloadService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "download-recovery"
        private const val KEY_JOB_ID = "job_id"

        fun schedule(context: Context, delayMillis: Long = 0, jobId: String? = null) {
            val request = OneTimeWorkRequestBuilder<RecoveryWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_JOB_ID to jobId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            val uniqueName = jobId?.let { "$UNIQUE_NAME-$it" } ?: UNIQUE_NAME
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
