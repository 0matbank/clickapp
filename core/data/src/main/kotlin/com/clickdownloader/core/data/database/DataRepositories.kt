package com.clickdownloader.core.data.database

import android.content.Context
import androidx.room.Room
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.OutputFileRepository

object DataRepositories {
    @Volatile
    private var instance: ClickDownloaderDatabase? = null

    private fun database(context: Context): ClickDownloaderDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            ClickDownloaderDatabase::class.java,
            "click_downloader.db",
        ).build().also { instance = it }
    }

    fun createDownloadJobRepository(context: Context): DownloadJobRepository {
        return RoomDownloadJobRepository(database(context).downloadJobDao())
    }

    fun createDownloadRequestRepository(context: Context): DownloadRequestRepository =
        RoomDownloadRequestRepository(database(context).downloadRequestDao())

    fun createOutputFileRepository(context: Context): OutputFileRepository =
        RoomOutputFileRepository(database(context).outputFileDao())
}
