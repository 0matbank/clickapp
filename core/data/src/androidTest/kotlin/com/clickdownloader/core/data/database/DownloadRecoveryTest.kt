package com.clickdownloader.core.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRecoveryTest {
    private lateinit var database: ClickDownloaderDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ClickDownloaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun interruptedTransferIsRequeuedAfterProcessRestart() = runTest {
        val repository = RoomDownloadJobRepository(database.downloadJobDao())
        repository.upsert(
            DownloadJob(
                id = "recover-me",
                sourceUrl = "https://example.test/video.mp4",
                displayTitle = "video.mp4",
                state = DownloadJobState.DOWNLOADING_VIDEO,
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
                downloadedBytes = 42,
                totalBytes = 100,
                retryCount = 0,
                errorCode = null,
                errorMessage = null,
                appVersion = "test",
                engineVersion = null,
            ),
        )

        repository.recoverInterruptedJobs()

        assertEquals(DownloadJobState.QUEUED, repository.findById("recover-me")!!.state)
    }
}
