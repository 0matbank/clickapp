package com.clickdownloader.core.data.database

import android.content.Context
import androidx.room.Room
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.OutputFileRepository
import com.clickdownloader.core.domain.ExtractionRepository
import com.clickdownloader.core.domain.FragmentCheckpointRepository
import com.clickdownloader.core.domain.PlaylistRepository

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

    fun createExtractionRepository(context: Context): ExtractionRepository =
        RoomExtractionRepository(database(context).extractionDao())

    fun createFragmentCheckpointRepository(context: Context): FragmentCheckpointRepository =
        RoomFragmentCheckpointRepository(database(context).fragmentCheckpointDao())

    fun createPlaylistRepository(context: Context): PlaylistRepository =
        RoomPlaylistRepository(database(context).playlistDao())
}
