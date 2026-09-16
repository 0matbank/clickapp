package com.clickdownloader.core.download

import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure
import com.clickdownloader.core.model.DownloadControl
import com.clickdownloader.core.model.DownloadProgress
import com.clickdownloader.core.model.DownloadRequest
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DirectDownloadResult(
    val file: File,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val resumed: Boolean,
    val serverIgnoredRange: Boolean,
)

class HttpDirectDownloader(private val client: OkHttpClient) {
    suspend fun download(
        request: DownloadRequest,
        partialFile: File,
        control: () -> DownloadControl = { DownloadControl.Continue },
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DirectDownloadResult = withContext(Dispatchers.IO) {
        partialFile.parentFile?.mkdirs()
        var offset = partialFile.takeIf(File::exists)?.length() ?: 0L
        val builder = Request.Builder().url(request.url).get()
        request.headers.forEach { (name, value) ->
            if (!name.equals("Host", true) && !name.equals("Content-Length", true)) builder.header(name, value)
        }
        if (offset > 0) {
            builder.header("Range", "bytes=$offset-")
            (request.etag ?: request.lastModified)?.let { builder.header("If-Range", it) }
        }
        try {
            client.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    401 -> throw DirectDownloadException(DirectDownloadFailure.AUTH_REQUIRED, "Login is required")
                    403, 410 -> throw DirectDownloadException(DirectDownloadFailure.LINK_EXPIRED, "The media link expired")
                    429 -> throw DirectDownloadException(DirectDownloadFailure.RATE_LIMITED, "The server rate-limited the request")
                }
                if (!response.isSuccessful) throw DirectDownloadException(
                    DirectDownloadFailure.INVALID_RESPONSE,
                    "Unexpected HTTP ${response.code}",
                )
                val ignoredRange = offset > 0 && response.code != 206
                if (ignoredRange) offset = 0
                if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) {
                    throw DirectDownloadException(DirectDownloadFailure.INVALID_RESPONSE, "Invalid resume range returned by server")
                }
                val body = response.body
                val responseBytes = body.contentLength().takeIf { it >= 0 }
                val total = request.expectedBytes ?: responseBytes?.let { it + offset }
                RandomAccessFile(partialFile, "rw").use { output ->
                    if (ignoredRange) output.setLength(0)
                    output.seek(offset)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = offset
                        var lastReportedAt = 0L
                        var lastReportedBytes = downloaded
                        while (true) {
                            ensureActive()
                            when (control()) {
                                DownloadControl.Pause -> throw DirectDownloadException(DirectDownloadFailure.NETWORK, "Download paused")
                                DownloadControl.Cancel -> throw DirectDownloadException(DirectDownloadFailure.CANCELLED, "Download cancelled")
                                DownloadControl.Continue -> Unit
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.nanoTime()
                            if (lastReportedAt == 0L || now - lastReportedAt >= 250_000_000L) {
                                val elapsed = if (lastReportedAt == 0L) 1.0 else (now - lastReportedAt) / 1_000_000_000.0
                                onProgress(DownloadProgress(downloaded, total, ((downloaded - lastReportedBytes) / elapsed).toLong()))
                                lastReportedAt = now
                                lastReportedBytes = downloaded
                            }
                        }
                        output.fd.sync()
                        onProgress(DownloadProgress(downloaded, total, 0))
                        if (total != null && downloaded != total) throw DirectDownloadException(
                            DirectDownloadFailure.VERIFICATION,
                            "Downloaded size $downloaded did not match expected size $total",
                        )
                        DirectDownloadResult(partialFile, downloaded, total, offset > 0, ignoredRange)
                    }
                }
            }
        } catch (error: DirectDownloadException) {
            throw error
        } catch (error: Exception) {
            throw DirectDownloadException(DirectDownloadFailure.NETWORK, "Network transfer failed", error)
        }
    }
}
