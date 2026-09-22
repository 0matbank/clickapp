package com.clickdownloader.app.extractor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.clickdownloader.app.BuildConfig
import com.clickdownloader.app.ClickDownloaderApplication
import com.clickdownloader.app.R
import com.clickdownloader.core.browser.SecretRedactor
import com.clickdownloader.core.extractor.AnalyzeExtractedMediaUseCase
import com.clickdownloader.core.extractor.ExtractorUpdateChannel
import java.io.File
import java.io.ObjectOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExtractorEngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RECEIVER, ResultReceiver::class.java) }
            ?: return START_NOT_STICKY.also { stopSelf(startId) }
        val url = intent.getStringExtra(EXTRA_URL)
            ?: return START_NOT_STICKY.also { stopSelf(startId) }
        startForegroundNow()
        scope.launch {
            val cookie = intent.getStringExtra(EXTRA_COOKIE)
            try {
                val container = (application as ClickDownloaderApplication).container
                val operation = intent.getStringExtra(EXTRA_OPERATION) ?: OP_ANALYZE
                if (operation != OP_ANALYZE) {
                    val result = when (operation) {
                        OP_VERSION -> container.extractorUpdateManager.currentVersion()?.let { false to it }
                            ?: error("Extractor is unavailable")
                        OP_UPDATE_STABLE -> container.extractorUpdateManager.update(ExtractorUpdateChannel.STABLE).let { it.changed to it.version }
                        OP_UPDATE_BETA -> container.extractorUpdateManager.update(ExtractorUpdateChannel.BETA).let { it.changed to it.version }
                        OP_ROLLBACK -> container.extractorUpdateManager.rollback().let { true to it.version }
                        else -> error("Unknown extractor operation")
                    }
                    receiver.send(RESULT_OK, Bundle().apply {
                        putBoolean(EXTRA_CHANGED, result.first)
                        putString(EXTRA_VERSION, result.second)
                        putString(EXTRA_OPERATION, operation)
                    })
                    return@launch
                }
                val result = AnalyzeExtractedMediaUseCase(
                    container.downloadJobRepository,
                    container.extractionRepository,
                    container.mediaExtractor,
                    BuildConfig.VERSION_NAME,
                    container.playlistRepository,
                )(url, cookie, intent.getStringExtra(EXTRA_SESSION_HOST))
                val directory = File(cacheDir, "extractor-results").apply { mkdirs() }
                val output = File(directory, "${UUID.randomUUID()}.bin")
                ObjectOutputStream(output.outputStream().buffered()).use { it.writeObject(result) }
                receiver.send(RESULT_OK, Bundle().apply { putString(EXTRA_RESULT_PATH, output.absolutePath) })
            } catch (error: Throwable) {
                receiver.send(RESULT_ERROR, Bundle().apply {
                    putString(EXTRA_ERROR, SecretRedactor.redact(error.message ?: "Extraction failed"))
                })
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun startForegroundNow() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.extractor_loading), NotificationManager.IMPORTANCE_LOW),
            )
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.extractor_loading))
                .setOngoing(true)
                .build(),
            if (android.os.Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    companion object {
        private const val CHANNEL = "extractor_analysis"
        private const val NOTIFICATION_ID = 8201
        const val RESULT_OK = 1
        const val RESULT_ERROR = 2
        const val EXTRA_RECEIVER = "receiver"
        const val EXTRA_URL = "url"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_SESSION_HOST = "session_host"
        const val EXTRA_RESULT_PATH = "result_path"
        const val EXTRA_ERROR = "error"
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_CHANGED = "changed"
        const val EXTRA_VERSION = "version"
        const val OP_ANALYZE = "analyze"
        const val OP_VERSION = "version"
        const val OP_UPDATE_STABLE = "update_stable"
        const val OP_UPDATE_BETA = "update_beta"
        const val OP_ROLLBACK = "rollback"
    }
}
