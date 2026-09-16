package com.clickdownloader.app.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.clickdownloader.app.ClickDownloaderApplication
import com.clickdownloader.app.R
import com.clickdownloader.core.media.CompatibleCopyProcessor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CompatibleCopyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.conversion_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return START_NOT_STICKY.also { stopSelf() }
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY.also { stopSelf() }
        val originalName = intent.getStringExtra(EXTRA_NAME) ?: "Media.mp4"
        foreground(getString(R.string.conversion_preparing))
        scope.launch {
            val container = (application as ClickDownloaderApplication).container
            val settings = container.settingsRepository.settings.first()
            val work = File(cacheDir, "compatible/$jobId-${System.currentTimeMillis()}")
            try {
                val result = CompatibleCopyProcessor(this@CompatibleCopyService).convert(
                    uri,
                    work,
                    settings.allowConversionOnLowBattery,
                    settings.allowConversionWhenHot,
                ) { foreground(it) }
                val outputName = compatibleName(originalName)
                val finalized = container.downloadFinalizer.finalizeFromTemporary(result.output.absolutePath, outputName, "video/mp4").getOrThrow()
                container.outputFileRepository.add(jobId, finalized, verified = true)
                foreground(getString(R.string.conversion_complete, outputName), ongoing = false)
            } catch (error: Throwable) {
                foreground(getString(R.string.conversion_failed, error.message ?: getString(R.string.unknown_error)), ongoing = false)
            } finally {
                work.deleteRecursively()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun foreground(text: String, ongoing: Boolean = true) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.conversion_title))
            .setContentText(text.take(180))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0,
        )
    }

    private fun compatibleName(original: String): String {
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        return "$stem - Compatible.mp4"
    }

    companion object {
        private const val CHANNEL_ID = "compatible_copy"
        private const val NOTIFICATION_ID = 8101
        private const val EXTRA_URI = "uri"
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, uri: String, jobId: String, name: String) {
            ContextCompat.startForegroundService(context, Intent(context, CompatibleCopyService::class.java).apply {
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_JOB_ID, jobId)
                putExtra(EXTRA_NAME, name)
            })
        }
    }
}
