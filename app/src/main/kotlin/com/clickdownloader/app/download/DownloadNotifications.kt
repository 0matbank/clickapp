package com.clickdownloader.app.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clickdownloader.app.MainActivity
import com.clickdownloader.app.R
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadProgress

object DownloadNotifications {
    const val CHANNEL_ACTIVE = "active_downloads"
    const val FOREGROUND_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ACTIVE, context.getString(R.string.notification_channel_downloads), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    fun build(
        context: Context,
        jobId: String,
        title: String,
        state: DownloadJobState,
        progress: DownloadProgress? = null,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(statusText(context, state, progress))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(state !in setOf(DownloadJobState.COMPLETED, DownloadJobState.FAILED, DownloadJobState.CANCELLED))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        val total = progress?.totalBytes
        if (progress != null && total != null && total > 0) {
            builder.setProgress(100, ((progress.downloadedBytes * 100 / total).coerceIn(0, 100)).toInt(), false)
        } else if (state !in setOf(DownloadJobState.COMPLETED, DownloadJobState.FAILED, DownloadJobState.CANCELLED)) {
            builder.setProgress(0, 0, true)
        }
        if (state in setOf(DownloadJobState.DOWNLOADING_VIDEO, DownloadJobState.DOWNLOADING_AUDIO, DownloadJobState.DOWNLOADING_FRAGMENTS, DownloadJobState.MERGING)) {
            builder.addAction(0, context.getString(R.string.pause), action(context, DownloadService.ACTION_PAUSE, jobId, 20))
            builder.addAction(0, context.getString(R.string.cancel), action(context, DownloadService.ACTION_CANCEL, jobId, 21))
        } else if (state == DownloadJobState.PAUSED) {
            builder.addAction(0, context.getString(R.string.resume), action(context, DownloadService.ACTION_RESUME, jobId, 22))
            builder.addAction(0, context.getString(R.string.cancel), action(context, DownloadService.ACTION_CANCEL, jobId, 23))
        }
        return builder.build()
    }

    fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun action(context: Context, action: String, jobId: String, requestCode: Int) = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, DownloadService::class.java).setAction(action).putExtra(DownloadService.EXTRA_JOB_ID, jobId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusText(context: Context, state: DownloadJobState, progress: DownloadProgress?): String {
        val totalBytes = progress?.totalBytes
        if (progress != null && totalBytes != null && totalBytes > 0) {
            val percent = progress.downloadedBytes * 100 / totalBytes
            return context.getString(R.string.notification_progress, percent, humanBytes(progress.bytesPerSecond))
        }
        return when (state) {
            DownloadJobState.PREPARING -> context.getString(R.string.job_preparing)
            DownloadJobState.VERIFYING -> context.getString(R.string.job_verifying)
            DownloadJobState.COMPLETED -> context.getString(R.string.job_completed)
            DownloadJobState.FAILED -> context.getString(R.string.job_failed)
            else -> context.getString(R.string.notification_working)
        }
    }

    private fun humanBytes(value: Long): String = when {
        value >= 1_048_576 -> "${value / 1_048_576} MB/s"
        value >= 1_024 -> "${value / 1_024} KB/s"
        else -> "$value B/s"
    }
}
