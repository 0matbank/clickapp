package com.clickdownloader.core.data.database

import androidx.room.Database
import androidx.room.AutoMigration
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
        DownloadRequestEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ClickDownloaderDatabase : RoomDatabase() {
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun downloadRequestDao(): DownloadRequestDao
    abstract fun outputFileDao(): OutputFileDao
}
