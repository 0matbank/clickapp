package com.clickdownloader.core.download

import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request

data class DirectMediaInfo(
    val finalUrl: String,
    val displayName: String,
    val mimeType: String,
    val contentLength: Long?,
    val etag: String?,
    val lastModified: String?,
    val supportsRanges: Boolean,
)

class DirectMediaProbe(private val client: OkHttpClient) {
    fun probe(url: String): DirectMediaInfo {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            val mime = response.header("Content-Type")?.substringBefore(';')?.lowercase() ?: inferMime(url)
            require(isMediaMime(mime) || hasMediaExtension(response.request.url.encodedPath)) {
                "The URL did not return recognizable audio or video media"
            }
            val contentRange = response.header("Content-Range")
            val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                ?: response.body.contentLength().takeIf { it >= 0 && response.code == 200 }
            val dispositionName = response.header("Content-Disposition")
                ?.substringAfter("filename=", "")
                ?.trim(' ', '"', '\'')
                ?.takeIf(String::isNotBlank)
            val pathName = response.request.url.pathSegments.lastOrNull()?.takeIf(String::isNotBlank)
            return DirectMediaInfo(
                finalUrl = response.request.url.toString(),
                displayName = FilenamePolicy.sanitize(dispositionName ?: pathName ?: "download.${extensionFor(mime)}"),
                mimeType = mime,
                contentLength = total,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                supportsRanges = response.code == 206 || response.header("Accept-Ranges")?.equals("bytes", true) == true,
            )
        }
    }

    private fun inferMime(url: String): String = when (URI(url).path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    private fun isMediaMime(value: String) = value.startsWith("video/") || value.startsWith("audio/")
    private fun hasMediaExtension(path: String) = path.substringAfterLast('.', "").lowercase() in mediaExtensions
    private fun extensionFor(mime: String) = when (mime) {
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "audio/mpeg" -> "mp3"
        "audio/flac" -> "flac"
        "audio/mp4" -> "m4a"
        else -> "mp4"
    }

    private companion object {
        val mediaExtensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "mp3", "m4a", "aac", "flac")
    }
}
