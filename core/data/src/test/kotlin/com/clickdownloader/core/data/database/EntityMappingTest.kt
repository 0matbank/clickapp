package com.clickdownloader.core.data.database

import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test
    fun `job mapping preserves exact selected state and byte counts`() {
        val job = DownloadJob(
            id = "id",
            sourceUrl = "https://example.com/video",
            displayTitle = "example.com",
            state = DownloadJobState.VERIFYING,
            createdAtEpochMillis = 10,
            updatedAtEpochMillis = 20,
            downloadedBytes = 1_024,
            totalBytes = 2_048,
            retryCount = 2,
            appVersion = "0.1.0",
            engineVersion = "bundled-1",
        )

        assertEquals(job, job.asEntity().asExternalModel())
    }
}

