package com.clickdownloader.core.data.database

import com.clickdownloader.core.domain.PlaylistRepository
import com.clickdownloader.core.model.PlaylistItem

class RoomPlaylistRepository(private val dao: PlaylistDao) : PlaylistRepository {
    override suspend fun savePlaylist(id: String, sourceUrl: String, title: String, items: List<PlaylistItem>) {
        dao.upsertPlaylist(PlaylistEntity(id, sourceUrl, title, items.size, System.currentTimeMillis()))
        dao.upsertItems(items.map {
            PlaylistItemEntity(id, it.id, it.sourceUrl, it.title, it.position, true, null)
        })
    }

    override suspend fun attachJob(playlistId: String, itemId: String, jobId: String) {
        dao.attachJob(playlistId, itemId, jobId)
    }
}
