package com.clickdownloader.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import com.clickdownloader.core.domain.StorageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageGateway(
    context: Context,
) : StorageGateway {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun persistDirectoryAccess(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val parsedUri = uri.toUri()
            require(parsedUri.scheme == ContentResolver.SCHEME_CONTENT) {
                "Only Storage Access Framework content URIs are accepted"
            }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(parsedUri, flags)
        }
    }

    override fun defaultDownloadsDescription(): String = Environment.DIRECTORY_DOWNLOADS
}
