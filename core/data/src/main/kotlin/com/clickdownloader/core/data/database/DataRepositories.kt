package com.clickdownloader.core.data.database

import android.content.Context
import androidx.room.Room
import com.clickdownloader.core.domain.DownloadJobRepository

object DataRepositories {
    fun createDownloadJobRepository(context: Context): DownloadJobRepository {
        val database = Room.databaseBuilder(
            context.applicationContext,
            ClickDownloaderDatabase::class.java,
            "click_downloader.db",
        ).build()
        return RoomDownloadJobRepository(database.downloadJobDao())
    }
}

