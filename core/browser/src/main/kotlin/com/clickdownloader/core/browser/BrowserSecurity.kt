package com.clickdownloader.core.browser

import android.content.Context
import java.io.File
import java.net.URI

object SecretRedactor {
    private val header = Regex("(?i)(authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+")
    private val query = Regex("(?i)([?&](?:token|access_token|auth|signature|sig|key|session|code)=)[^&#\\s]+")

    fun redact(value: String): String = value
        .replace(header) { "${it.groupValues[1]}: <redacted>" }
        .replace(query) { "${it.groupValues[1]}<redacted>" }
}

data class DetectedMedia(val actualUrl: String, val displayUrl: String, val kind: String)

object MediaRequestDetector {
    private val mediaExtensions = setOf("mp4", "mkv", "webm", "mov", "m4a", "mp3", "aac", "flac", "opus", "ogg", "m3u8", "mpd")

    fun detect(url: String, acceptHeader: String? = null): DetectedMedia? {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val extension = path.substringAfterLast('.', "").lowercase()
        val mediaAccept = acceptHeader.orEmpty().lowercase().let { "video/" in it || "audio/" in it || "mpegurl" in it || "dash+xml" in it }
        if (extension !in mediaExtensions && !mediaAccept) return null
        return DetectedMedia(url, SecretRedactor.redact(url), extension.ifBlank { "media" })
    }
}

class SessionCookieExporter(private val context: Context) {
    private val directory get() = File(context.cacheDir, "browser-cookie-exports").apply { mkdirs() }

    fun export(host: String, cookieHeader: String): File {
        val file = File.createTempFile("session-", ".txt", directory)
        val secure = if (host == "localhost") "FALSE" else "TRUE"
        file.bufferedWriter().use { writer ->
            writer.appendLine("# Netscape HTTP Cookie File")
            cookieHeader.split(';').map(String::trim).filter { '=' in it }.forEach { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "")
                if (name.isNotBlank()) writer.appendLine("$host\tTRUE\t/\t$secure\t0\t$name\t$value")
            }
        }
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        return file
    }

    fun clearAll() { directory.listFiles().orEmpty().forEach(File::delete) }
}
