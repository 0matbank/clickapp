package com.clickdownloader.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadJobEntity::class,
        MediaMetadataEntity::class,
        SelectedFormatEntity::class,
        FragmentStateEntity::class,
        OutputFileEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        ErrorSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ClickDownloaderDatabase : RoomDatabase() {
    abstract fun downloadJobDao(): DownloadJobDao
}

