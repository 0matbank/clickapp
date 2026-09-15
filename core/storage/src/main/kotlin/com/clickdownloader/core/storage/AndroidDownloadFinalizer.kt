package com.clickdownloader.core.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.clickdownloader.core.domain.DownloadFinalizer
import com.clickdownloader.core.model.FinalizedFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDownloadFinalizer(
    context: Context,
    private val selectedDirectoryUri: suspend () -> String?,
) : DownloadFinalizer {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun availableBytes(): Long? = withContext(Dispatchers.IO) {
        runCatching { StatFs(appContext.filesDir.absolutePath).availableBytes }.getOrNull()
    }

    override suspend fun finalizeFromTemporary(
        temporaryPath: String,
        displayName: String,
        mimeType: String,
    ): Result<FinalizedFile> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(temporaryPath)
            require(source.isFile && source.length() > 0) { "Temporary download is missing or empty" }
            val selected = selectedDirectoryUri()
            if (selected != null) finalizeToDocumentTree(source, selected, displayName, mimeType)
            else finalizeToMediaStore(source, displayName, mimeType)
        }
    }

    private fun finalizeToDocumentTree(source: File, treeUri: String, requestedName: String, mimeType: String): FinalizedFile {
        val directory = DocumentFile.fromTreeUri(appContext, treeUri.toUri())
            ?.takeIf { it.isDirectory && it.canWrite() }
            ?: error("The selected download folder is no longer writable")
        val finalName = uniqueDocumentName(directory, requestedName)
        val temporaryName = ".${UUID.randomUUID()}.$finalName.part"
        val document = directory.createFile("application/octet-stream", temporaryName)
            ?: error("Could not create a temporary destination file")
        try {
            resolver.openOutputStream(document.uri, "wt")!!.use { output -> source.inputStream().use { it.copyTo(output) } }
            require(document.length() == source.length()) { "Destination size verification failed" }
            require(document.renameTo(finalName)) { "Could not atomically finalize the destination file" }
            val finalized = directory.findFile(finalName) ?: document
            source.delete()
            return FinalizedFile(finalized.uri.toString(), finalName, mimeType, finalized.length())
        } catch (error: Throwable) {
            document.delete()
            throw error
        }
    }

    private fun finalizeToMediaStore(source: File, requestedName: String, mimeType: String): FinalizedFile {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Choose a download folder on Android 8 or 9"
        }
        val collection = if (mimeType.startsWith("audio/")) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDirectory = if (mimeType.startsWith("audio/")) "${Environment.DIRECTORY_DOWNLOADS}/Click Downloader/Audio"
        else "${Environment.DIRECTORY_DOWNLOADS}/Click Downloader/Videos"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, requestedName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create a MediaStore entry")
        try {
            resolver.openOutputStream(uri, "w")!!.use { output -> source.inputStream().use { it.copyTo(output) } }
            val written = resolver.openFileDescriptor(uri, "r")!!.use { it.statSize }
            require(written == source.length()) { "Destination size verification failed" }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            source.delete()
            return FinalizedFile(uri.toString(), requestedName, mimeType, written)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun uniqueDocumentName(directory: DocumentFile, requested: String): String {
        if (directory.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.')
        val stem = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 1
        while (directory.findFile("$stem ($index)$extension") != null) index++
        return "$stem ($index)$extension"
    }
}
