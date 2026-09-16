package com.clickdownloader.core.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "download_jobs")
data class DownloadJobEntity(
    @androidx.room.PrimaryKey val id: String,
    val sourceUrl: String,
    val displayTitle: String,
    val state: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val retryCount: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val appVersion: String,
    val engineVersion: String?,
)

@Entity(
    tableName = "media_metadata",
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class MediaMetadataEntity(
    @androidx.room.PrimaryKey val jobId: String,
    val title: String,
    val description: String?,
    val creator: String?,
    val sourcePlatform: String?,
    val canonicalUrl: String,
    val durationMillis: Long?,
    val uploadDate: String?,
    val liveStatus: String,
    val expiryHintEpochMillis: Long?,
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val thumbnailUrlsJson: String = "[]",
)

@Entity(
    tableName = "selected_formats",
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class SelectedFormatEntity(
    @androidx.room.PrimaryKey val jobId: String,
    val formatId: String,
    val width: Int?,
    val height: Int?,
    val framesPerSecond: Double?,
    val videoBitrate: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioBitrate: Long?,
    val container: String?,
    val dynamicRange: String?,
    val bitDepth: Int?,
    val audioLanguage: String?,
    val estimatedBytes: Long?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
)

@Entity(
    tableName = "fragment_states",
    primaryKeys = ["jobId", "trackId", "fragmentIndex"],
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class FragmentStateEntity(
    val jobId: String,
    val trackId: String,
    val fragmentIndex: Long,
    val state: String,
    val downloadedBytes: Long,
    val temporaryUri: String?,
    val checksum: String?,
)

@Entity(
    tableName = "output_files",
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class OutputFileEntity(
    @androidx.room.PrimaryKey val id: String,
    val jobId: String,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isVerified: Boolean,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @androidx.room.PrimaryKey val id: String,
    val sourceUrl: String,
    val title: String,
    val itemCount: Int?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "itemId"],
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId"), Index("jobId")],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val itemId: String,
    val sourceUrl: String,
    val title: String?,
    val position: Int,
    val isSelected: Boolean,
    val jobId: String?,
)

@Entity(
    tableName = "error_summaries",
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class ErrorSummaryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String,
    val code: String,
    val userMessage: String,
    val retryCount: Int,
    val occurredAtEpochMillis: Long,
)

@Entity(
    tableName = "download_requests",
    foreignKeys = [ForeignKey(
        entity = DownloadJobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId", unique = true), Index(value = ["priority", "queuePosition"])],
)
data class DownloadRequestEntity(
    @androidx.room.PrimaryKey val jobId: String,
    val url: String,
    val secondaryUrl: String?,
    val displayName: String,
    val mimeType: String?,
    val kind: String,
    val expectedBytes: Long?,
    val etag: String?,
    val lastModified: String?,
    val temporaryPath: String?,
    val outputUri: String?,
    val supportsRanges: Boolean?,
    @androidx.room.ColumnInfo(defaultValue = "''") val headersEncoded: String,
    @androidx.room.ColumnInfo(defaultValue = "''") val secondaryHeadersEncoded: String,
    val priority: Int,
    val queuePosition: Long,
    val attempt: Int,
    val maxAttempts: Int,
    val duplicatePolicy: String,
    val partialFilePolicy: String,
)
