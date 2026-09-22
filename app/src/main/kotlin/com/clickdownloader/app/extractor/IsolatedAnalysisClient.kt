package com.clickdownloader.app.extractor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import com.clickdownloader.core.extractor.PendingMediaSelection
import com.clickdownloader.core.extractor.ExtractorUpdateChannel
import com.clickdownloader.core.extractor.ExtractorUpdateResult
import java.io.File
import java.io.ObjectInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class IsolatedAnalysisClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun analyze(url: String, cookieFilePath: String?, sessionHost: String?): PendingMediaSelection {
        val path = suspendCancellableCoroutine<String> { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (!continuation.isActive) return
                    if (resultCode == ExtractorEngineService.RESULT_OK) {
                        continuation.resume(resultData?.getString(ExtractorEngineService.EXTRA_RESULT_PATH).orEmpty())
                    } else {
                        continuation.resumeWithException(IllegalStateException(resultData?.getString(ExtractorEngineService.EXTRA_ERROR) ?: "Extraction failed"))
                    }
                }
            }
            ContextCompat.startForegroundService(appContext, Intent(appContext, ExtractorEngineService::class.java).apply {
                putExtra(ExtractorEngineService.EXTRA_RECEIVER, receiver)
                putExtra(ExtractorEngineService.EXTRA_URL, url)
                putExtra(ExtractorEngineService.EXTRA_COOKIE, cookieFilePath)
                putExtra(ExtractorEngineService.EXTRA_SESSION_HOST, sessionHost)
            })
        }
        require(path.isNotBlank()) { "Extractor returned no result" }
        return withContext(Dispatchers.IO) {
            val file = File(path)
            try {
                ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as PendingMediaSelection }
            } finally {
                file.delete()
            }
        }
    }

    suspend fun currentVersion(): String = operation(ExtractorEngineService.OP_VERSION).getString(ExtractorEngineService.EXTRA_VERSION)
        ?: error("Extractor is unavailable")

    suspend fun update(channel: ExtractorUpdateChannel): ExtractorUpdateResult {
        val op = if (channel == ExtractorUpdateChannel.STABLE) ExtractorEngineService.OP_UPDATE_STABLE else ExtractorEngineService.OP_UPDATE_BETA
        val result = operation(op)
        return ExtractorUpdateResult(result.getBoolean(ExtractorEngineService.EXTRA_CHANGED), result.getString(ExtractorEngineService.EXTRA_VERSION).orEmpty())
    }

    suspend fun rollback(): ExtractorUpdateResult {
        val result = operation(ExtractorEngineService.OP_ROLLBACK)
        return ExtractorUpdateResult(true, result.getString(ExtractorEngineService.EXTRA_VERSION).orEmpty(), rolledBack = true)
    }

    private suspend fun operation(operation: String): Bundle = suspendCancellableCoroutine { continuation ->
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (!continuation.isActive) return
                if (resultCode == ExtractorEngineService.RESULT_OK && resultData != null) continuation.resume(resultData)
                else continuation.resumeWithException(IllegalStateException(resultData?.getString(ExtractorEngineService.EXTRA_ERROR) ?: "Extractor operation failed"))
            }
        }
        ContextCompat.startForegroundService(appContext, Intent(appContext, ExtractorEngineService::class.java).apply {
            putExtra(ExtractorEngineService.EXTRA_RECEIVER, receiver)
            putExtra(ExtractorEngineService.EXTRA_URL, "about:blank")
            putExtra(ExtractorEngineService.EXTRA_OPERATION, operation)
        })
    }
}
