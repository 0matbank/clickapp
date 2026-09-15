package com.clickdownloader.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageContractTest {
    @Test
    fun `logical directory names stay stable`() {
        assertEquals(listOf("Videos", "Audio", "Playlists", "Temporary"), LogicalDirectory.entries.map { it.folderName })
    }
}

